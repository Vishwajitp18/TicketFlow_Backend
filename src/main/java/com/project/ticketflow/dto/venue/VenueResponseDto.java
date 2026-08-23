package com.project.ticketflow.dto.venue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VenueResponseDto {
    private Long id;
    private String name;
    private String address;
    private String city;
    private boolean active;
}
