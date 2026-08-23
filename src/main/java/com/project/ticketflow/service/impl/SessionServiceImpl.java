package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.auth.LoginResponseDto;
import com.project.ticketflow.entity.Session;
import com.project.ticketflow.entity.User;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.SessionRepository;
import com.project.ticketflow.repository.UserRepository;
import com.project.ticketflow.security.JwtService;
import com.project.ticketflow.service.SessionService;
import com.project.ticketflow.util.RefreshTokenHasher;
import com.project.ticketflow.util.TokenBlacklister;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${session.count.max:3}")
    private int SESSION_LIMIT;

    private final SessionRepository sessionRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final TokenBlacklister tokenBlacklister;

    @Override
    @Transactional
    public String createSession(Long userId, String refreshToken, String familyId) {
        User user = userRepository.findByIdAndLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        List<Session> sessionList = sessionRepository.findByUserOrderByLastUsedAtAsc(user);
        int excess = sessionList.size() - SESSION_LIMIT + 1;
        if (excess > 0) {
            List<Session> toDeleteList = sessionList.subList(0, excess);
            List<String> jtiList = toDeleteList.stream().map(Session::getJti).toList();
            sessionRepository.deleteAllInBatch(toDeleteList);
            tokenBlacklister.blacklistBatch(jtiList);
        }

        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        String jti = UUID.randomUUID().toString();
        Session toSave = Session.builder()
                .user(user).refreshTokenHash(refreshTokenHash).familyId(familyId)
                .jti(jti).lastUsedAt(LocalDateTime.now()).build();
        sessionRepository.save(toSave);
        return jti;
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, String familyId) {
        Optional<Session> optionalSession = sessionRepository.findByUserIdAndFamilyId(userId, familyId);
        if (optionalSession.isEmpty()) return;

        Session session = optionalSession.get();
        String jti = session.getJti();
        sessionRepository.delete(session);
        sessionRepository.flush();
        tokenBlacklister.blacklist(jti);
    }

    @Override
    @Transactional
    public LoginResponseDto refreshSession(Long userId, String refreshToken, String familyId) {
        Session session = sessionRepository.findSessionWithUser(userId, familyId).orElse(null);
        if (session == null) {
            return null;
        }

        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);

        if (!refreshTokenHash.equals(session.getRefreshTokenHash())) {
            if (session.getLastUsedAt().isAfter(LocalDateTime.now().minusSeconds(20))) {
                return null;
            }
            List<String> jtiList = sessionRepository.findAllJti(userId);
            sessionRepository.deleteAllSessionsForUser(userId);
            tokenBlacklister.blacklistBatch(jtiList);
            return null;
        }

        String jtiToBlacklist = session.getJti();
        String newJti = UUID.randomUUID().toString();

        String newAccessToken = jwtService.generateAccessToken(session.getUser(), newJti);
        String newRefreshToken = jwtService.generateRefreshToken(session.getUser().getId(), session.getFamilyId());

        String newRefreshTokenHash = refreshTokenHasher.hash(newRefreshToken);

        session.setJti(newJti);
        session.setRefreshTokenHash(newRefreshTokenHash);
        session.setLastUsedAt(LocalDateTime.now());

        // Deliberately not caught here: an OptimisticLockingFailureException on this save
        // means a concurrent refresh call for this same session won the race and already
        // rotated it (same benign scenario as the lastUsedAt-within-20s branch above, just
        // caught at the DB-write instant instead of the pre-check). Letting it propagate out
        // of this @Transactional method is what makes Spring roll the transaction back
        // cleanly; AuthServiceImpl catches it and turns it into a normal "please retry" 401
        // instead of the generic handler's misleading "resource updated" 409.
        sessionRepository.save(session);
        sessionRepository.flush();

        tokenBlacklister.blacklist(jtiToBlacklist);

        return LoginResponseDto.builder()
                .accessToken(newAccessToken).refreshToken(newRefreshToken)
                .build();
    }
}
