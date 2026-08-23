package com.project.ticketflow.dto.waitlist;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinWaitlistRequestDto {

    @NotNull(message = "Show is required")
    private Long showId;

    @NotNull(message = "Category is required")
    private Long categoryId;

    // fulfillment is all-or-nothing for this many seats together — see SeatReleaseService
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 20, message = "Quantity cannot exceed 20")
    private Integer quantity;
}
