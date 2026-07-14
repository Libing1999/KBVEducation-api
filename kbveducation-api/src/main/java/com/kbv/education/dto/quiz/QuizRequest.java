package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.QuizStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Create/update the quiz attached to a lesson. */
public record QuizRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @Positive Integer durationMinutes,
        @PositiveOrZero Integer passingMarks,
        QuizStatus status
) {
}
