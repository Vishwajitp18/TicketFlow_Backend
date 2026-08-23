package com.project.ticketflow.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventReportDto {
    private Long eventId;
    private String title;
    private long confirmedBookings;
    private long cancelledBookings;
    private BigDecimal totalRevenue;
}
