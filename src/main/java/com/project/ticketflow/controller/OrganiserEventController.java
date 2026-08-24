package com.project.ticketflow.controller;

import com.project.ticketflow.dto.event.*;
import com.project.ticketflow.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organiser/events")
@RequiredArgsConstructor
public class OrganiserEventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponseDto> createEvent(@Valid @RequestBody EventRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(requestDto));
    }

    @GetMapping
    public ResponseEntity<Page<EventResponseDto>> getMyEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(eventService.getMyEvents(pageable));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponseDto> getEvent(@PathVariable Long eventId) {
        // false: an organiser managing their own event should still see past shows too
        return ResponseEntity.ok(eventService.getEvent(eventId, false));
    }

    @PostMapping("/{eventId}/shows")
    public ResponseEntity<ShowResponseDto> createShow(@PathVariable Long eventId,
                                                        @Valid @RequestBody ShowRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createShow(eventId, requestDto));
    }

    @GetMapping("/{eventId}/report")
    public ResponseEntity<EventReportDto> getEventReport(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEventReport(eventId));
    }
}
