package com.project.ticketflow.enums;

import com.project.ticketflow.exception.BadRequestException;

public enum Role {
    CUSTOMER,
    ORGANISER,
    ADMIN;

    public static Role from(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid role: " + value);
        }
    }
}
