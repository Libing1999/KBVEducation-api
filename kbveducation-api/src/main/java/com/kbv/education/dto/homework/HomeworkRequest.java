package com.kbv.education.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Admin configuration of a lesson's homework. */
public record HomeworkRequest(
        @NotBlank @Size(max = 200) String title,
        String instructions,
        Instant dueDate,
        List<String> allowedFileTypes,
        @Positive Integer maxFileSizeMb
) {
}
