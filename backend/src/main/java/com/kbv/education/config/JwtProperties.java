package com.kbv.education.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.security.jwt.*} configuration.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** Signing secret; must be at least 32 bytes for HS256. */
    private String secret;

    /** Access-token lifetime in milliseconds. */
    private long accessTokenExpirationMs = 900_000L;

    /** Refresh-token lifetime in milliseconds. */
    private long refreshTokenExpirationMs = 604_800_000L;

    /** Token issuer claim. */
    private String issuer = "kbv-education";
}
