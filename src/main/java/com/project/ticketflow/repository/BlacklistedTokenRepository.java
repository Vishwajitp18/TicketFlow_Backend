package com.project.ticketflow.repository;

import com.project.ticketflow.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByJti(String jti);

    @Modifying
    @Query(value = "DELETE FROM blacklisted_token WHERE expires_at < :now", nativeQuery = true)
    void deleteExpired(@Param("now") LocalDateTime now);
}
