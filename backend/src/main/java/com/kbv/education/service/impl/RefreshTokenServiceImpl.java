package com.kbv.education.service.impl;

import com.kbv.education.config.JwtProperties;
import com.kbv.education.entity.RefreshToken;
import com.kbv.education.entity.User;
import com.kbv.education.exception.ApiException;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.repository.RefreshTokenRepository;
import com.kbv.education.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manages opaque refresh tokens. The raw token (high-entropy random string) is
 * returned to the client once; only its SHA-256 hash is persisted, so a DB leak
 * does not expose usable tokens. Supports rotation and revocation.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();

    @Override
    @Transactional
    public String createToken(User user) {
        String raw = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()));
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return raw;
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken verify(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (!token.isActive()) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        return token;
    }

    @Override
    @Transactional
    public String rotate(RefreshToken current, User user) {
        current.setRevoked(true);
        refreshTokenRepository.save(current);
        return createToken(user);
    }

    @Override
    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return base64.encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
