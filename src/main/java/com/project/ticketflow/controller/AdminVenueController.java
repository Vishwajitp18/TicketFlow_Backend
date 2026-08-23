package com.project.ticketflow.controller;

import com.project.ticketflow.dto.venue.*;
import com.project.ticketflow.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/venues")
@RequiredArgsConstructor
public class AdminVenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<VenueResponseDto> createVenue(@Valid @RequestBody VenueRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.createVenue(requestDto));
    }

    @GetMapping
    public ResponseEntity<List<VenueResponseDto>> getAllVenues() {
        return ResponseEntity.ok(venueService.getAllVenues());
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<VenueResponseDto> getVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(venueService.getVenue(venueId));
    }

    @PostMapping("/{venueId}/seats/bulk")
    public ResponseEntity<List<SeatDto>> addSeats(@PathVariable Long venueId,
                                                    @Valid @RequestBody BulkSeatRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.addSeats(venueId, requestDto));
    }

    @GetMapping("/{venueId}/seats")
    public ResponseEntity<List<SeatDto>> getSeats(@PathVariable Long venueId) {
        return ResponseEntity.ok(venueService.getSeats(venueId));
    }
}
