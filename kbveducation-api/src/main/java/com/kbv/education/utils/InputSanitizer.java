package com.kbv.education.utils;

/**
 * Lightweight sanitization for free-text fields (Phase 5 Step 7) — trims and
 * strips control characters, and enforces a max length. This is not a full
 * HTML-sanitization framework: the frontend is React (auto-escapes by
 * default, no {@code dangerouslySetInnerHTML} usage found in this app), so
 * stored-XSS risk from these fields is already low. Scope stays proportionate
 * to that: a few known free-text fields (override reasons, notification
 * messages), not every string in the system.
 */
public final class InputSanitizer {

    private InputSanitizer() {
    }

    public static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }
}
