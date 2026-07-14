package com.kbv.education.dto.lesson;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateLessonRequest(
        @NotNull UUID cohortId,
        @Min(1) int lessonNumber,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String summary,
        String description,
        LocalDate lessonDate,
        Integer displayOrder
) {
}
