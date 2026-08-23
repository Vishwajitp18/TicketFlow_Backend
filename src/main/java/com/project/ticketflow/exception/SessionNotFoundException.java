package com.project.ticketflow.exception;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends CustomException {
    public SessionNotFoundException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
