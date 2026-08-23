package com.project.ticketflow.service;

import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.waitlist.JoinWaitlistRequestDto;
import com.project.ticketflow.dto.waitlist.WaitlistEntryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WaitlistService {

    WaitlistEntryResponseDto join(JoinWaitlistRequestDto requestDto);

    Page<WaitlistEntryResponseDto> getMyEntries(Pageable pageable);

    BookingResponseDto acceptOffer(String token);
}
