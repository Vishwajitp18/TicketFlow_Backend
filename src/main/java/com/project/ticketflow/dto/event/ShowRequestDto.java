package com.project.ticketflow.dto.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class ShowRequestDto {

    @NotNull(message = "Venue is required")
    private Long venueId;

    @NotNull(message = "Show date is required")
    private LocalDate showDate;

    @NotNull(message = "Show time is required")
    private LocalTime showTime;

    @NotEmpty(message = "At least one category price is required")
    @Valid
    private List<CategoryPriceDto> categoryPrices;
}
