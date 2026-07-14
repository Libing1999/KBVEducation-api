package com.kbv.education.dto.lesson;

import com.kbv.education.entity.enums.LessonStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Admin-facing lesson representation. */
public record LessonResponse(
        UUID id,
        UUID cohortId,
        String cohortName,
        int lessonNumber,
        String title,
        String summary,
        String description,
        LocalDate lessonDate,
        LessonStatus status,
        Instant publishedDate,
        int displayOrder,
        long fileCount,
        boolean hasQuiz,
        boolean hasHomework,
        Instant createdAt
) {
}
