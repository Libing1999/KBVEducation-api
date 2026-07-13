package com.kbv.education.dto.lesson;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Student-facing lesson card with progress flags. Only published lessons are
 * returned.
 */
public record StudentLessonResponse(
        UUID id,
        int lessonNumber,
        String title,
        String summary,
        LocalDate lessonDate,
        long fileCount,
        boolean hasQuiz,
        boolean quizCompleted,
        boolean hasHomework,
        boolean homeworkSubmitted
) {
}
