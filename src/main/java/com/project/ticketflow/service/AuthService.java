package com.project.ticketflow.service;

import com.project.ticketflow.dto.auth.LoginRequestDto;
import com.project.ticketflow.dto.auth.LoginResponseDto;
import com.project.ticketflow.dto.auth.RegisterRequestDto;
import com.project.ticketflow.dto.auth.RegisterResponseDto;

public interface AuthService {

    RegisterResponseDto register(RegisterRequestDto requestDto);

    LoginResponseDto login(LoginRequestDto requestDto);

    void logout(String refreshToken);

    LoginResponseDto refresh(String refreshToken);
}
