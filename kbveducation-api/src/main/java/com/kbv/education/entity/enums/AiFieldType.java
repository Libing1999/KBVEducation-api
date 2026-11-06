package com.kbv.education.entity.enums;

/** Types an admin can choose for a custom AI field (memo 17.10.2026, 2.1). */
public enum AiFieldType {
    /** Any number. Shown as a graph over time. */
    NUMBER,
    /** A whole number from 1 to 10. Shown as a graph over time. */
    RATING,
    /** Yes or no. Shown as a graph over time (share of "yes"). */
    YES_NO,
    /** A short piece of text. Shown in a table. */
    SHORT_TEXT,
    /** A list of short texts. Shown in a table. */
    LIST;

    public boolean isGraphed() {
        return this == NUMBER || this == RATING || this == YES_NO;
    }
}
