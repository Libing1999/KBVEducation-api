package com.kbv.education.entity.enums;

/** Which AI service did (or should do) a job. Claude is the default; Gemini is the backup and transcriber. */
public enum AiProvider {
    CLAUDE,
    GEMINI;

    public AiProvider other() {
        return this == CLAUDE ? GEMINI : CLAUDE;
    }
}
