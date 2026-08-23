package com.project.ticketflow.repository;

import com.project.ticketflow.entity.ShowSeat;
import com.project.ticketflow.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @Query("select ss from ShowSeat ss join fetch ss.seat join fetch ss.category where ss.show.id = :showId")
    List<ShowSeat> findByShowId(@Param("showId") Long showId);

    // locked in id-ascending order to avoid deadlocks between two overlapping seat-selection requests
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.id in :ids order by ss.id asc")
    List<ShowSeat> findByIdInForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.id = :id")
    ShowSeat findByIdForUpdate(@Param("id") Long id);

    long countByShowIdAndCategoryIdAndStatus(Long showId, Long categoryId, SeatStatus status);

    // the shared waitlist pool for a (show, category) — locked + id-ordered so the same
    // deterministic slice of seats is picked whenever multiple entries are being fulfilled
    // in one pass (see SeatReleaseService#tryFulfillQueue)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss
            where ss.show.id = :showId and ss.category.id = :categoryId and ss.status = :status
            order by ss.id asc
            """)
    List<ShowSeat> findByShowIdAndCategoryIdAndStatusForUpdate(
            @Param("showId") Long showId, @Param("categoryId") Long categoryId, @Param("status") SeatStatus status);
}
