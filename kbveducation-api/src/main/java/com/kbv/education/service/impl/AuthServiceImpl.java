package com.kbv.education.service.impl;

import com.kbv.education.dto.auth.AuthUserResponse;
import com.kbv.education.dto.auth.LoginRequest;
import com.kbv.education.dto.auth.LoginResponse;
import com.kbv.education.dto.auth.TokenResponse;
import com.kbv.education.audit.Audited;
import com.kbv.education.entity.RefreshToken;
import com.kbv.education.entity.User;
import com.kbv.education.entity.UserSession;
import com.kbv.education.exception.ApiException;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.mapper.UserMapper;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.UserSessionRepository;
import com.kbv.education.security.JwtService;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.AuthService;
import com.kbv.education.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    @Audited(action = "LOGIN", entityType = "AUTH", failureAction = "LOGIN_FAILED", captureResult = false)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (DisabledException ex) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        } catch (BadCredentialsException ex) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByIdAndDeletedFalse(principal.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createToken(user);

        recordSession(user, httpRequest);

        log.info("User {} logged in", user.getEmail());
        return new LoginResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenExpirationMs() / 1000,
                user.getRole().getName(),
                userMapper.toAuthUser(user));
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken token = refreshTokenService.verify(refreshToken);
        User user = token.getUser();

        if (user.isDeleted() || !user.isActive()) {
            refreshTokenService.revokeAllForUser(user);
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String newRefreshToken = refreshTokenService.rotate(token, user);
        String accessToken = jwtService.generateAccessToken(user);

        return new TokenResponse(
                accessToken,
                newRefreshToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenExpirationMs() / 1000);
    }

    @Override
    @Transactional
    @Audited(action = "LOGOUT", entityType = "AUTH")
    public void logout(UUID userId) {
        userRepository.findByIdAndDeletedFalse(userId)
                .ifPresent(refreshTokenService::revokeAllForUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return userMapper.toAuthUser(user);
    }

    private void recordSession(User user, HttpServletRequest httpRequest) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setIpAddress(resolveClientIp(httpRequest));
        session.setUserAgent(truncate(httpRequest.getHeader("User-Agent"), 512));
        session.setLoginAt(Instant.now());
        userSessionRepository.save(session);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
