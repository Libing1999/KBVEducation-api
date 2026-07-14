package com.kbv.education.dto.reflection;

import java.util.UUID;

/** One submitted answer (parsed from the multipart {@code answers} JSON field). */
public record ReflectionAnswerInput(
        UUID questionId,
        String answerText
) {
}
