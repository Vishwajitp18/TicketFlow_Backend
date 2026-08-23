package com.project.ticketflow.service;

import com.project.ticketflow.dto.auth.LoginResponseDto;

public interface SessionService {
    String createSession(Long userId, String refreshToken, String familyId);

    void deleteSession(Long userId, String familyId);

    LoginResponseDto refreshSession(Long userId, String refreshToken, String familyId);
}
