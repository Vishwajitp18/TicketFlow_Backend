package com.project.ticketflow.controller;

import com.project.ticketflow.dto.venue.SeatDto;
import com.project.ticketflow.dto.venue.VenueResponseDto;
import com.project.ticketflow.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public, read-only counterpart to /admin/venues — lets an organiser (or anyone) discover
 * which venues exist and what seat categories they have before creating a show, since
 * /admin/venues itself is ADMIN-only for management.
 */
@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueBrowseController {

    private final VenueService venueService;

    @GetMapping
    public ResponseEntity<List<VenueResponseDto>> getAllVenues() {
        return ResponseEntity.ok(venueService.getAllVenues());
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<VenueResponseDto> getVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(venueService.getVenue(venueId));
    }

    // an organiser needs this to know which category names to price when creating a show
    @GetMapping("/{venueId}/seats")
    public ResponseEntity<List<SeatDto>> getSeats(@PathVariable Long venueId) {
        return ResponseEntity.ok(venueService.getSeats(venueId));
    }
}
