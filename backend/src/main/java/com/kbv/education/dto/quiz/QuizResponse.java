package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.QuizStatus;

import java.util.List;
import java.util.UUID;

/** Admin-facing quiz including full question/option detail (for the builder). */
public record QuizResponse(
        UUID id,
        UUID lessonId,
        String title,
        String description,
        Integer durationMinutes,
        Integer passingMarks,
        QuizStatus status,
        int questionCount,
        List<QuestionResponse> questions
) {
}
