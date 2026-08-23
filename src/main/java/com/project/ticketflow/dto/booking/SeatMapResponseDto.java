package com.project.ticketflow.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatMapResponseDto {
    private Long showId;
    private List<SeatMapSeatDto> seats;
}
