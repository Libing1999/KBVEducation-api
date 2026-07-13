package com.kbv.education.dto.homework;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeworkResponse(
        UUID id,
        UUID lessonId,
        String lessonTitle,
        String title,
        String instructions,
        Instant dueDate,
        List<String> allowedFileTypes,
        Integer maxFileSizeMb
) {
}
