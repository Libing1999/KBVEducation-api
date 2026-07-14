package com.kbv.education.dto.reflection;

import jakarta.validation.constraints.NotBlank;

/** Admin create/update of a reflection question. */
public record ReflectionQuestionRequest(
        @NotBlank String questionText,
        Boolean enabled
) {
}
