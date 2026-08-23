package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.booking.SeatMapResponseDto;
import com.project.ticketflow.dto.booking.SeatMapSeatDto;
import com.project.ticketflow.entity.ShowSeat;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.ShowRepository;
import com.project.ticketflow.repository.ShowSeatRepository;
import com.project.ticketflow.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponseDto getSeatMap(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show not found with id: " + showId);
        }

        var seatDtos = showSeatRepository.findByShowId(showId).stream()
                .map(this::toDto)
                .toList();

        return SeatMapResponseDto.builder()
                .showId(showId)
                .seats(seatDtos)
                .build();
    }

    private SeatMapSeatDto toDto(ShowSeat showSeat) {
        return SeatMapSeatDto.builder()
                .showSeatId(showSeat.getId())
                .rowLabel(showSeat.getSeat().getRowLabel())
                .seatNumber(showSeat.getSeat().getSeatNumber())
                .label(showSeat.getSeat().getLabel())
                .categoryId(showSeat.getCategory().getId())
                .categoryName(showSeat.getCategory().getName())
                .price(showSeat.getPrice())
                .status(showSeat.getStatus().name())
                .build();
    }
}
