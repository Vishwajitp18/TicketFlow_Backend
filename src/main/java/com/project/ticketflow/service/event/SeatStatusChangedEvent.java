package com.project.ticketflow.service.event;

// published once per seat whenever its status changes (hold, confirm, cancel, free, offer, ...)
public record SeatStatusChangedEvent(Long showId, Long showSeatId, String status) {
}
