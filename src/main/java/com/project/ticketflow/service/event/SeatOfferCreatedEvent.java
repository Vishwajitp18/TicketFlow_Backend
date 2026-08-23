package com.project.ticketflow.service.event;

// token identifies a whole offer group (one row per seat in SeatOffer, all sharing this token)
public record SeatOfferCreatedEvent(String token) {
}
