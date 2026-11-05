package com.kbv.education.service.ai;

import com.kbv.education.service.ai.provider.AiModelOption;
import com.kbv.education.service.ai.provider.ClaudeAiClient;
import com.kbv.education.service.ai.provider.GeminiAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The model lists for the admin Settings dropdowns, read from Claude and Gemini themselves so
 * new models appear without a new app release (memo 12.10.2026, 2). Cached for an hour; when a
 * provider is not set up or cannot be reached, the models named in the memos are offered instead.
 */
@Service
@RequiredArgsConstructor
public class AiModelCatalog {

    private static final Duration CACHE_FOR = Duration.ofHours(1);

    private static final List<AiModelOption> CLAUDE_FALLBACK = List.of(
            new AiModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5"),
            new AiModelOption("claude-opus-5-5", "Claude Opus 5.5"),
            new AiModelOption("claude-haiku-5-5", "Claude Haiku 5.5"));

    private static final List<AiModelOption> GEMINI_FALLBACK = List.of(
            new AiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash"),
            new AiModelOption("gemini-3.5-transcribe", "Gemini 3.5 Transcribe"));

    private final ClaudeAiClient claude;
    private final GeminiAiClient gemini;

    private volatile Models cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    /** {@code live} is false when a list is the built-in fallback rather than the provider's own. */
    public record Models(List<AiModelOption> claude, boolean claudeLive, List<AiModelOption> gemini, boolean geminiLive) {
    }

    public Models models(boolean refresh) {
        if (!refresh && cached != null && cachedAt.plus(CACHE_FOR).isAfter(Instant.now())) {
            return cached;
        }
        List<AiModelOption> c = claude.listModels();
        List<AiModelOption> g = gemini.listModels();
        Models fresh = new Models(
                c.isEmpty() ? CLAUDE_FALLBACK : c, !c.isEmpty(),
                g.isEmpty() ? GEMINI_FALLBACK : g, !g.isEmpty());
        if (!c.isEmpty() || !g.isEmpty()) {
            cached = fresh;
            cachedAt = Instant.now();
        }
        return fresh;
    }
}
