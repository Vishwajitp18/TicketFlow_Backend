package com.project.ticketflow.service;

import com.project.ticketflow.dto.event.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {

    EventResponseDto createEvent(EventRequestDto requestDto);

    // upcomingOnly=true (public browse) hides shows that have already happened; the
    // organiser's own view passes false to still see their event's full show history
    EventResponseDto getEvent(Long eventId, boolean upcomingOnly);

    Page<EventResponseDto> searchEvents(String type, String city, String query, Pageable pageable);

    Page<EventResponseDto> getMyEvents(Pageable pageable);

    ShowResponseDto createShow(Long eventId, ShowRequestDto requestDto);

    EventReportDto getEventReport(Long eventId);
}
