package com.kbv.education.dto.subject;

import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        int displayOrder,
        boolean enabled
) {
}
