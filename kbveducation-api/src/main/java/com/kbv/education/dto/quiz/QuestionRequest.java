package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Add/edit a question. For MCQ, exactly 4 options with exactly one correct are
 * expected (validated in the service). For OPEN_ENDED, options are ignored.
 */
public record QuestionRequest(
        @NotBlank String questionText,
        @NotNull QuestionType questionType,
        @Positive Integer marks,
        @Valid List<OptionRequest> options
) {
}
