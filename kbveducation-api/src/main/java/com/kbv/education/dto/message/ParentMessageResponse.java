package com.kbv.education.dto.message;

import java.time.Instant;

/**
 * Parent-facing "Messages from Bhavya" card entry. Kept intentionally
 * minimal ({@code text} + {@code date}) to match the design, which shows
 * only the latest note text and a relative date, newest first.
 */
public record ParentMessageResponse(
        String text,
        Instant date
) {
}
