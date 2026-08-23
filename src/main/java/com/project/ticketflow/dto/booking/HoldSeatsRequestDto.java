package com.project.ticketflow.dto.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class HoldSeatsRequestDto {

    @NotNull(message = "Show is required")
    private Long showId;

    @NotEmpty(message = "At least one seat is required")
    private List<Long> showSeatIds;
}
