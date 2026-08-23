package com.project.ticketflow.dto.venue;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VenueRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;
}
