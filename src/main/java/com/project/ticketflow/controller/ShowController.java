package com.project.ticketflow.controller;

import com.project.ticketflow.dto.booking.SeatMapResponseDto;
import com.project.ticketflow.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    // initial snapshot for the seat map — the client is expected to call this once on page
    // load, then subscribe to /topic/shows/{showId}/seatmap over WebSocket (see
    // WebSocketConfig) for live deltas instead of re-polling this endpoint
    @GetMapping("/{showId}/seatmap")
    public ResponseEntity<SeatMapResponseDto> getSeatMap(@PathVariable Long showId) {
        return ResponseEntity.ok(showService.getSeatMap(showId));
    }
}
