package com.kbv.education.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin create/update of a subject. */
public record SubjectRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean enabled
) {
}
