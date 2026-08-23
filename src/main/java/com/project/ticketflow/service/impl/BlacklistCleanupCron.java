package com.project.ticketflow.service.impl;

import com.project.ticketflow.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Sweeps the Postgres-backed blacklist (see TokenBlacklister) — the analogue of Redis key
 * TTL, since this project intentionally has no Redis dependency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlacklistCleanupCron {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(cron = "0 0 * * * *") // hourly
    @SchedulerLock(name = "blacklistCleanupTask", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    @Transactional
    public void cleanUpExpiredTokens() {
        blacklistedTokenRepository.deleteExpired(LocalDateTime.now());
        log.debug("Blacklist cleanup task ran");
    }
}
