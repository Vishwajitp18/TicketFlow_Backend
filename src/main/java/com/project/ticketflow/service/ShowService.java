package com.project.ticketflow.service;

import com.project.ticketflow.dto.booking.SeatMapResponseDto;

public interface ShowService {
    SeatMapResponseDto getSeatMap(Long showId);
}
