package com.kbv.education.dto.practice;

import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.StudyType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A logged practice session, with attachments and review history. */
public record PracticeSessionResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String cohortName,
        LocalDate studyDate,
        String subject,
        int durationMinutes,
        StudyType studyType,
        String notes,
        PracticeStatus status,
        String adminComment,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        List<FileResponse> files,
        List<ReviewRequestResponse> reviewRequests
) {
}
