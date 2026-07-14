package com.kbv.education.dto.homework;

import com.kbv.education.dto.file.FileResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeworkSubmissionResponse(
        UUID id,
        UUID homeworkId,
        UUID lessonId,
        String lessonTitle,
        UUID studentId,
        String studentName,
        String note,
        Instant submittedAt,
        List<FileResponse> files
) {
}
