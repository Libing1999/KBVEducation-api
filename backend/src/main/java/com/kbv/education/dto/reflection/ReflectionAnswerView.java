package com.kbv.education.dto.reflection;

import java.util.UUID;

/** One question + the student's typed answer, for reading a reflection. */
public record ReflectionAnswerView(
        UUID questionId,
        String questionText,
        String answerText
) {
}
