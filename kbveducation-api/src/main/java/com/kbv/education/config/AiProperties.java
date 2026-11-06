package com.kbv.education.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code app.ai.*}. The API keys come only from the server's environment
 * ({@code ANTHROPIC_API_KEY}, {@code GEMINI_API_KEY}) — never from the database,
 * the repository, or anything sent to the browser. A blank key means that AI is
 * "not set up" and is skipped, including as a backup.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String anthropicApiKey;

    private String geminiApiKey;

    /** Largest transcript sent in one request; longer ones are split into parts and the results combined. */
    private int maxCharsPerRequest = 1_200_000;

    /** Cap on the AI's reply length (tokens). Includes Claude's thinking. */
    private long maxOutputTokens = 16_000;
}
