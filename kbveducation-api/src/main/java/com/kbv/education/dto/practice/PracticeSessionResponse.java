package com.kbv.education.dto.practice;

import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.StudyType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A logged practice session, with attachments and review history. {@code ai} is filled only
 * for the admin; for students it is always null (and so left out of the JSON), because
 * students never see anything the AI writes.
 */
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
        String transcript,
        Integer year,
        PracticeStatus status,
        String adminComment,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        List<FileResponse> files,
        List<ReviewRequestResponse> reviewRequests,
        AdminAi ai
) {

    /** The AI headline for the admin review list. Full details come from the review card endpoint. */
    public record AdminAi(
            AiJobStatus status,
            Integer trustRating,
            String recommendation,
            Integer flagCount,
            boolean autoApproved,
            Instant autoApprovedAt,
            boolean spotCheck,
            String overrideReason,
            Integer turnCount,
            Integer transcriptChars
    ) {
    }
}
