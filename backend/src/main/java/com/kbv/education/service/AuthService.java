package com.kbv.education.service;

import com.kbv.education.dto.auth.AuthUserResponse;
import com.kbv.education.dto.auth.LoginRequest;
import com.kbv.education.dto.auth.LoginResponse;
import com.kbv.education.dto.auth.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface AuthService {

    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    TokenResponse refresh(String refreshToken);

    void logout(UUID userId);

    AuthUserResponse getCurrentUser(UUID userId);
}
