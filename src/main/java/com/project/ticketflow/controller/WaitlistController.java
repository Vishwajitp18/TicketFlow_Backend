package com.project.ticketflow.controller;

import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.waitlist.JoinWaitlistRequestDto;
import com.project.ticketflow.dto.waitlist.WaitlistEntryResponseDto;
import com.project.ticketflow.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<WaitlistEntryResponseDto> join(@Valid @RequestBody JoinWaitlistRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.join(requestDto));
    }

    @GetMapping
    public ResponseEntity<Page<WaitlistEntryResponseDto>> getMyEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(waitlistService.getMyEntries(pageable));
    }

    // the "time-limited link" a waitlisted customer clicks from their offer email
    @PostMapping("/offers/{token}/accept")
    public ResponseEntity<BookingResponseDto> acceptOffer(@PathVariable String token) {
        return ResponseEntity.ok(waitlistService.acceptOffer(token));
    }
}
