package com.project.ticketflow.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatMapSeatDto {
    private Long showSeatId;
    private String rowLabel;
    private Integer seatNumber;
    private String label;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String status;
}
