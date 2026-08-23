package com.project.ticketflow.dto.venue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatDto {
    private Long id;
    private String rowLabel;
    private Integer seatNumber;
    private String label;
    private String categoryName;
}
