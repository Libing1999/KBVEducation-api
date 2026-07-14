package com.kbv.education.dto.reflection;

import java.util.UUID;

public record ReflectionQuestionResponse(
        UUID id,
        String questionText,
        int displayOrder,
        boolean enabled
) {
}
