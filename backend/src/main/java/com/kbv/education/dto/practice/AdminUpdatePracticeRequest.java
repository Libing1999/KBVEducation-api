package com.kbv.education.dto.practice;

import com.kbv.education.entity.enums.StudyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Admin edit of a practice session (subject, date, duration, type, notes, comment). */
public record AdminUpdatePracticeRequest(
        @NotNull LocalDate studyDate,
        @NotBlank @Size(max = 200) String subject,
        @Positive int durationMinutes,
        @NotNull StudyType studyType,
        String notes,
        String adminComment
) {
}
