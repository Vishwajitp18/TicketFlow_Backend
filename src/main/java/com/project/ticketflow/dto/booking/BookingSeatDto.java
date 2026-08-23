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
public class BookingSeatDto {
    private String seatLabel;
    private String categoryName;
    private BigDecimal price;
}
