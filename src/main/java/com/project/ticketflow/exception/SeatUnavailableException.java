package com.project.ticketflow.exception;

import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends CustomException {
    public SeatUnavailableException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
