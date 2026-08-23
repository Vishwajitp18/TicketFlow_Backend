package com.project.ticketflow.service;

public interface EmailService {

    void sendBookingConfirmation(Long bookingId);

    // token identifies a whole offer group — a multi-seat offer is several SeatOffer rows
    // sharing one token
    void sendWaitlistOffer(String token);
}
