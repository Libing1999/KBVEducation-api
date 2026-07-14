package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.CohortStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CohortResponse(
        UUID id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate examDate,
        CohortStatus status,
        int maxStudents,
        long studentCount,
        Instant createdAt
) {
}
