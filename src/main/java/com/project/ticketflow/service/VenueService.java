package com.project.ticketflow.service;

import com.project.ticketflow.dto.venue.*;

import java.util.List;

public interface VenueService {

    VenueResponseDto createVenue(VenueRequestDto requestDto);

    VenueResponseDto getVenue(Long venueId);

    List<VenueResponseDto> getAllVenues();

    List<SeatDto> addSeats(Long venueId, BulkSeatRequestDto requestDto);

    List<SeatDto> getSeats(Long venueId);
}
