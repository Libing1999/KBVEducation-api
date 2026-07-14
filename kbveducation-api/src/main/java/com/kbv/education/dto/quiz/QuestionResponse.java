package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.QuestionType;

import java.util.List;
import java.util.UUID;

/** Admin-facing question with options (correct answers visible). */
public record QuestionResponse(
        UUID id,
        String questionText,
        QuestionType questionType,
        int marks,
        int displayOrder,
        List<OptionResponse> options
) {
}
