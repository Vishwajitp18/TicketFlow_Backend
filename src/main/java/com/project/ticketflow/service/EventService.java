package com.project.ticketflow.service;

import com.project.ticketflow.dto.event.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {

    EventResponseDto createEvent(EventRequestDto requestDto);

    EventResponseDto getEvent(Long eventId);

    Page<EventResponseDto> searchEvents(String type, String city, String query, Pageable pageable);

    Page<EventResponseDto> getMyEvents(Pageable pageable);

    ShowResponseDto createShow(Long eventId, ShowRequestDto requestDto);

    EventReportDto getEventReport(Long eventId);
}
