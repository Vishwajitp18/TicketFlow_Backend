package com.project.ticketflow.dto.venue;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BulkSeatRowDto {

    @NotBlank(message = "Row label is required")
    private String rowLabel;

    @NotBlank(message = "Category name is required")
    private String categoryName;

    @Min(value = 1, message = "Seat count must be at least 1")
    private int seatCount;
}
