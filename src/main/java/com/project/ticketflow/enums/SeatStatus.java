package com.project.ticketflow.enums;

public enum SeatStatus {
    AVAILABLE,
    HELD,
    // pulled out of general circulation into a category's shared waitlist pool — not tied
    // to any one waiter yet, just no longer directly bookable (see SeatReleaseService)
    RESERVED,
    OFFERED,
    BOOKED
}
