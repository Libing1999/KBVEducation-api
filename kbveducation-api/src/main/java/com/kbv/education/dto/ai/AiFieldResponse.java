package com.kbv.education.dto.ai;

import com.kbv.education.entity.enums.AiFieldType;
import com.kbv.education.entity.enums.AiJobType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One field the AI returns. Built-in fields have no id and cannot be changed. */
public record AiFieldResponse(
        UUID id,
        AiJobType jobType,
        String key,
        String label,
        String description,
        String type,
        boolean builtIn,
        boolean active,
        Instant createdAt
) {

    /** All fields, built-in first, as the admin panel lists them. */
    public record AiFieldList(List<AiFieldResponse> practice, List<AiFieldResponse> reflection) {
    }

    public record CreateAiFieldRequest(
            @jakarta.validation.constraints.NotNull AiJobType jobType,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String label,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000) String description,
            @jakarta.validation.constraints.NotNull AiFieldType fieldType
    ) {
    }

    public record UpdateAiFieldRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String label,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000) String description,
            boolean active
    ) {
    }

    /** Analyse older items again so a new field gets filled in (normal calls, full price). */
    public record ReanalyseRequest(
            @jakarta.validation.constraints.NotNull AiJobType jobType,
            @jakarta.validation.constraints.NotNull java.time.LocalDate from,
            @jakarta.validation.constraints.NotNull java.time.LocalDate to
    ) {
    }
}
