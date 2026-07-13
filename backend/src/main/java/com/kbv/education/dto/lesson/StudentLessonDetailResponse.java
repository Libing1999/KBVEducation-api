package com.kbv.education.dto.lesson;

import com.kbv.education.dto.file.FileResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full lesson view for a student/parent, including files and quiz/homework
 * availability + progress. Correct answers are never included here.
 */
public record StudentLessonDetailResponse(
        UUID id,
        int lessonNumber,
        String title,
        String summary,
        String description,
        LocalDate lessonDate,
        List<FileResponse> files,

        boolean hasQuiz,
        UUID quizId,
        String quizTitle,
        boolean quizCompleted,

        boolean hasHomework,
        UUID homeworkId,
        String homeworkTitle,
        String homeworkInstructions,
        Instant homeworkDueDate,
        List<String> homeworkAllowedFileTypes,
        Integer homeworkMaxFileSizeMb,
        boolean homeworkSubmitted
) {
}
