package com.project.ticketflow.service;

import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.booking.ConfirmBookingRequestDto;
import com.project.ticketflow.dto.booking.HoldSeatsRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    BookingResponseDto holdSeats(HoldSeatsRequestDto requestDto);

    BookingResponseDto confirmBooking(Long bookingId, ConfirmBookingRequestDto requestDto);

    BookingResponseDto cancelBooking(Long bookingId);

    Page<BookingResponseDto> getMyBookings(Pageable pageable);

    BookingResponseDto getBookingById(Long bookingId);

    void cleanUpExpiredHoldsAndOffers();
}
