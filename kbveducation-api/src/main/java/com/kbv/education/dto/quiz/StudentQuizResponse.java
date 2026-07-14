package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.QuestionType;

import java.util.List;
import java.util.UUID;

/**
 * Student-facing quiz for taking. Correct answers are never included. Options
 * for MCQs carry only id + text.
 */
public record StudentQuizResponse(
        UUID id,
        UUID lessonId,
        String title,
        String description,
        Integer durationMinutes,
        boolean alreadySubmitted,
        List<Question> questions
) {
    public record Question(
            UUID id,
            String questionText,
            QuestionType questionType,
            int marks,
            int displayOrder,
            List<Option> options
    ) {
    }

    public record Option(
            UUID id,
            String optionText
    ) {
    }
}
