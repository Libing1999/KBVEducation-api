package com.kbv.education.dto.lesson;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateLessonRequest(
        @Min(1) int lessonNumber,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String summary,
        String description,
        LocalDate lessonDate
) {
}
