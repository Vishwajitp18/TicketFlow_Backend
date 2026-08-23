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

    // polling target for the customer's live seat map
    @GetMapping("/{showId}/seatmap")
    public ResponseEntity<SeatMapResponseDto> getSeatMap(@PathVariable Long showId) {
        return ResponseEntity.ok(showService.getSeatMap(showId));
    }
}
