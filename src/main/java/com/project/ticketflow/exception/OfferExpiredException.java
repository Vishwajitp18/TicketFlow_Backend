package com.project.ticketflow.exception;

import org.springframework.http.HttpStatus;

public class OfferExpiredException extends CustomException {
    public OfferExpiredException(String message) {
        super(message, HttpStatus.GONE);
    }
}
