package com.project.ticketflow.util;

import com.project.ticketflow.entity.BlacklistedToken;
import com.project.ticketflow.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Postgres-backed replacement for a Redis TTL blacklist: rows are inserted with
 * an expiresAt equal to the access-token expiry and swept by a ShedLock cron
 * (see BlacklistCleanupCron) instead of relying on Redis key TTL.
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklister {

    @Value("${jwt.access.expiry}")
    private Long accessExpiryMs;

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Transactional
    public void blacklist(String jti) {
        if (blacklistedTokenRepository.existsByJti(jti)) return;
        blacklistedTokenRepository.save(BlacklistedToken.builder()
                .jti(jti)
                .expiresAt(LocalDateTime.now().plusNanos(accessExpiryMs * 1_000_000L))
                .build());
    }

    @Transactional
    public void blacklistBatch(List<String> jtis) {
        if (jtis == null || jtis.isEmpty()) return;
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(accessExpiryMs * 1_000_000L);
        List<BlacklistedToken> tokens = jtis.stream()
                .map(jti -> BlacklistedToken.builder().jti(jti).expiresAt(expiresAt).build())
                .toList();
        blacklistedTokenRepository.saveAll(tokens);
    }

    public boolean isBlacklisted(String jti) {
        return blacklistedTokenRepository.existsByJti(jti);
    }
}
