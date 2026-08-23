package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.auth.*;
import com.project.ticketflow.entity.User;
import com.project.ticketflow.enums.Role;
import com.project.ticketflow.exception.BadRequestException;
import com.project.ticketflow.exception.SessionNotFoundException;
import com.project.ticketflow.repository.UserRepository;
import com.project.ticketflow.security.JwtService;
import com.project.ticketflow.security.SecurityHelper;
import com.project.ticketflow.service.AuthService;
import com.project.ticketflow.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final SecurityHelper securityHelper;

    private static final Set<Role> SELF_REGISTERABLE_ROLES = Set.of(Role.CUSTOMER, Role.ORGANISER);

    @Override
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto requestDto) {
        log.info("Registering user with email: {}", requestDto.getEmail());
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            throw new BadRequestException("An account already exists with this email.");
        }

        Role role = Role.from(requestDto.getRole());
        if (!SELF_REGISTERABLE_ROLES.contains(role)) {
            throw new BadRequestException("Role must be one of: " + SELF_REGISTERABLE_ROLES);
        }

        User user = User.builder()
                .name(requestDto.getName())
                .email(requestDto.getEmail())
                .passwordHash(passwordEncoder.encode(requestDto.getPassword()))
                .roles(Set.of(role))
                .build();

        User savedUser = userRepository.saveAndFlush(user);
        log.info("Successfully registered user with id: {}", savedUser.getId());

        return RegisterResponseDto.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .role(role.name())
                .build();
    }

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        log.info("Logging in user with email: {}", loginRequestDto.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDto.getEmail(), loginRequestDto.getPassword()
                )
        );

        User user = (User) authentication.getPrincipal();
        String familyId = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(user.getId(), familyId);

        String jti = sessionService.createSession(user.getId(), refreshToken, familyId);
        String accessToken = jwtService.generateAccessToken(user, jti);

        log.info("Successfully logged in user with email: {}", loginRequestDto.getEmail());
        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadRequestException("Refresh token cannot be null or empty");
        }
        AuthenticatedUser authenticatedUser = securityHelper.getCurrentAuthenticatedUser().orElseThrow(
                () -> new AuthenticationServiceException("Cannot verify the authenticated user.")
        );
        RefreshTokenClaims claims = jwtService.parseRefreshToken(refreshToken);
        Long userId = claims.getUserId();
        String familyId = claims.getFamilyId();
        if (!authenticatedUser.getId().equals(userId)) {
            throw new AuthenticationServiceException("Cannot verify the authenticated user.");
        }
        log.info("Logging out user with id: {}", userId);
        sessionService.deleteSession(userId, familyId);
        log.info("Successfully logged out user with id: {}", userId);
    }

    @Override
    public LoginResponseDto refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadRequestException("Refresh token cannot be null or empty");
        }
        RefreshTokenClaims claims = jwtService.parseRefreshToken(refreshToken);
        Long userId = claims.getUserId();
        String familyId = claims.getFamilyId();
        log.info("Refreshing user with id: {}", userId);

        LoginResponseDto loginResponseDto;
        try {
            loginResponseDto = sessionService.refreshSession(userId, refreshToken, familyId);
        } catch (OptimisticLockingFailureException e) {
            // Another concurrent request with the same refresh token won the rotation race
            // first (see SessionServiceImpl.refreshSession) — not an invalid session, just a
            // lost race. Surface it as a clean, retryable 401 rather than letting the generic
            // handler report it as a 409 "resource updated by another process".
            log.debug("Lost a concurrent refresh race for user {}", userId);
            throw new SessionNotFoundException(
                    "This refresh token was already used by a concurrent request. Retry with the latest token.");
        }

        if (loginResponseDto == null) {
            throw new SessionNotFoundException("Please login again.");
        }
        log.info("Successfully refreshed user with id: {}", userId);
        return loginResponseDto;
    }
}
