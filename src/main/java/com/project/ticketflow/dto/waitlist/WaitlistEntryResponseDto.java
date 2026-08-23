package com.project.ticketflow.dto.waitlist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WaitlistEntryResponseDto {
    private Long id;
    private Long showId;
    private String eventTitle;
    private Long categoryId;
    private String categoryName;
    private Integer requestedQuantity;
    private String status;
    private LocalDateTime joinedAt;
}
