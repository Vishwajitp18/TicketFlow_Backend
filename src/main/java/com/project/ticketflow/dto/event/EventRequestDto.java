package com.project.ticketflow.dto.event;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Type is required")
    private String type;

    private String description;
}
