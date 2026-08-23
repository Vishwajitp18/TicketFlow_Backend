package com.project.ticketflow.security;

import com.project.ticketflow.dto.auth.AccessTokenClaims;
import com.project.ticketflow.dto.auth.AuthenticatedUser;
import com.project.ticketflow.enums.Role;
import com.project.ticketflow.exception.SessionNotFoundException;
import com.project.ticketflow.util.TokenBlacklister;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenBlacklister tokenBlacklister;
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String accessToken = authHeader.substring(7);
                AccessTokenClaims claims = jwtService.parseAccessToken(accessToken);

                if (tokenBlacklister.isBlacklisted(claims.getJti())) {
                    throw new SessionNotFoundException("Session expired.");
                }

                Set<Role> roles = claims.getRoles().stream()
                        .map(Role::valueOf)
                        .collect(Collectors.toSet());

                AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                        .id(claims.getUserId())
                        .name(claims.getName())
                        .email(claims.getEmail())
                        .roles(roles)
                        .build();

                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.getAuthorities());

                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/auth/") && !request.getServletPath().startsWith("/auth/logout");
    }
}
