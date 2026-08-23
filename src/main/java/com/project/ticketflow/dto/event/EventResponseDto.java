package com.project.ticketflow.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventResponseDto {
    private Long id;
    private Long organiserId;
    private String title;
    private String type;
    private String description;
    private boolean active;
    private List<ShowResponseDto> shows;
}
