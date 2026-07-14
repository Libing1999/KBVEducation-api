package com.kbv.education.controller;

import com.kbv.education.dto.auth.AuthUserResponse;
import com.kbv.education.dto.auth.ForgotPasswordRequest;
import com.kbv.education.dto.auth.LoginRequest;
import com.kbv.education.dto.auth.LoginResponse;
import com.kbv.education.dto.auth.RefreshTokenRequest;
import com.kbv.education.dto.auth.TokenResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Authentication", description = "Login, token refresh, and session endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Authenticate with email and password")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.success("Login successful", authService.login(request, httpRequest));
    }

    @Operation(summary = "Exchange a refresh token for a new access/refresh token pair")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("Token refreshed", authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Revoke the current user's refresh tokens")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getId());
        return ApiResponse.success("Logged out");
    }

    @Operation(summary = "Get the currently authenticated user")
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.getCurrentUser(principal.getId()));
    }

    @Operation(summary = "Request a password reset (UI-only stub in Phase 1)")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Phase 1: no email delivery. Always return success to avoid account enumeration.
        log.info("Password reset requested for {}", request.email());
        return ApiResponse.success("If the email exists, reset instructions have been sent");
    }
}
