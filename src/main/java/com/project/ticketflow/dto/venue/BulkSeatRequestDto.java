package com.project.ticketflow.dto.venue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkSeatRequestDto {

    @NotEmpty(message = "At least one row is required")
    @Valid
    private List<BulkSeatRowDto> rows;
}
