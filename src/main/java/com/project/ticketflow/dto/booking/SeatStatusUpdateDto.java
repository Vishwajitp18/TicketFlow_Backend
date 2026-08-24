package com.project.ticketflow.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload pushed over the /topic/shows/{showId}/seatmap WebSocket topic whenever a single
 * seat's status changes — a delta, not a full seatmap snapshot. The client is expected to
 * fetch GET /shows/{id}/seatmap once on page load for the initial state, then apply these as
 * they arrive instead of re-polling.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatStatusUpdateDto {
    private Long showSeatId;
    private String status;
}
