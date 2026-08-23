package com.project.ticketflow.service.impl;

import com.project.ticketflow.entity.*;
import com.project.ticketflow.enums.BookingStatus;
import com.project.ticketflow.enums.SeatStatus;
import com.project.ticketflow.repository.BookingRepository;
import com.project.ticketflow.repository.BookingSeatRepository;
import com.project.ticketflow.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared "turn a set of already-locked, AVAILABLE/OFFERED show seats into a fresh HELD
 * booking" step. Used both by a direct seat-selection hold and by accepting a waitlist
 * offer — from this point on the two flows are identical (same checkout window, same
 * confirm/expire path).
 */
@Component
@RequiredArgsConstructor
public class BookingHoldFactory {

    @Value("${booking.hold.ttl-minutes:10}")
    private long holdTtlMinutes;

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public Booking createHeldBooking(User customer, Show show, List<ShowSeat> lockedSeats) {
        BigDecimal amount = lockedSeats.stream()
                .map(ShowSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Booking booking = Booking.builder()
                .customer(customer)
                .show(show)
                .status(BookingStatus.HELD)
                .amount(amount)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(holdTtlMinutes))
                .build();
        Booking savedBooking = bookingRepository.save(booking);

        for (ShowSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByBookingId(savedBooking.getId());
            seat.setHoldExpiresAt(savedBooking.getHoldExpiresAt());
        }
        showSeatRepository.saveAll(lockedSeats);

        List<BookingSeat> bookingSeats = lockedSeats.stream()
                .map(seat -> BookingSeat.builder()
                        .booking(savedBooking)
                        .showSeat(seat)
                        .priceAtBooking(seat.getPrice())
                        .build())
                .toList();
        bookingSeatRepository.saveAll(bookingSeats);

        return savedBooking;
    }
}
