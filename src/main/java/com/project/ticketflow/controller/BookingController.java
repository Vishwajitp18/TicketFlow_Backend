package com.project.ticketflow.controller;

import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.booking.ConfirmBookingRequestDto;
import com.project.ticketflow.dto.booking.HoldSeatsRequestDto;
import com.project.ticketflow.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/hold")
    public ResponseEntity<BookingResponseDto> holdSeats(@Valid @RequestBody HoldSeatsRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.holdSeats(requestDto));
    }

    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingResponseDto> confirmBooking(@PathVariable Long bookingId,
                                                                @Valid @RequestBody ConfirmBookingRequestDto requestDto) {
        return ResponseEntity.ok(bookingService.confirmBooking(bookingId, requestDto));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId));
    }

    @GetMapping
    public ResponseEntity<Page<BookingResponseDto>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(bookingService.getMyBookings(pageable));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBookingById(bookingId));
    }
}
