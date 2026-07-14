package com.kbv.education.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** A student's quiz submission (single, no retake). */
public record SubmitQuizRequest(
        @NotNull @Valid List<Answer> answers
) {
    public record Answer(
            @NotNull UUID questionId,
            UUID selectedOptionId,
            String answerText
    ) {
    }
}
