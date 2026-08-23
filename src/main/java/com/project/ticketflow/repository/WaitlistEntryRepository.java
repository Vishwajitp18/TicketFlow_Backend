package com.project.ticketflow.repository;

import com.project.ticketflow.entity.WaitlistEntry;
import com.project.ticketflow.enums.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    // The whole join-order queue for a (show, category), locked so a concurrent seat-release
    // pass can't act on the same entries at the same time. See
    // SeatReleaseService#tryFulfillQueue — this walks the list in order but is allowed to
    // skip an entry whose requestedQuantity the current pool can't cover yet, letting a
    // smaller request behind it be satisfied first.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from WaitlistEntry w
            where w.show.id = :showId and w.category.id = :categoryId and w.status = 'WAITING'
            order by w.joinedAt asc
            """)
    List<WaitlistEntry> findAllWaitingForUpdate(@Param("showId") Long showId, @Param("categoryId") Long categoryId);

    @Query("""
            select case when count(w) > 0 then true else false end from WaitlistEntry w
            where w.show.id = :showId and w.category.id = :categoryId and w.status = 'WAITING'
            """)
    boolean existsWaiting(@Param("showId") Long showId, @Param("categoryId") Long categoryId);

    boolean existsByCustomerIdAndShowIdAndCategoryIdAndStatus(
            Long customerId, Long showId, Long categoryId, WaitlistStatus status);

    Page<WaitlistEntry> findByCustomerIdOrderByJoinedAtDesc(Long customerId, Pageable pageable);

    // for the accumulation-timeout cron — how long is too long to keep waiting is a business
    // call (waitlist.accumulation-timeout-minutes), not something baked into the query
    List<WaitlistEntry> findByStatusAndJoinedAtBefore(WaitlistStatus status, LocalDateTime threshold, Pageable pageable);
}
