package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Booking;
import com.project.ticketflow.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("select b from Booking b join fetch b.seats where b.id = :id")
    Optional<Booking> findByIdWithSeats(@Param("id") Long id);

    // "My bookings" history — HELD (mid-checkout, not yet a real outcome) and EXPIRED
    // (abandoned holds) are noise there, not history a customer wants to see.
    Page<Booking> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
            Long customerId, List<BookingStatus> statuses, Pageable pageable);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime threshold, Pageable pageable);

    @Query("select count(b) from Booking b where b.show.event.id = :eventId and b.status = 'CONFIRMED'")
    long countConfirmedForEvent(@Param("eventId") Long eventId);

    @Query("select coalesce(sum(b.amount),0) from Booking b where b.show.event.id = :eventId and b.status = 'CONFIRMED'")
    java.math.BigDecimal totalRevenueForEvent(@Param("eventId") Long eventId);

    @Query("select count(b) from Booking b where b.show.event.id = :eventId and b.status = 'CANCELLED'")
    long countCancelledForEvent(@Param("eventId") Long eventId);
}
