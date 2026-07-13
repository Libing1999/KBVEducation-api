package com.kbv.education.dto.quiz;

import java.util.UUID;

/** Admin-facing option (includes the correct flag). */
public record OptionResponse(
        UUID id,
        String optionText,
        boolean correct,
        int displayOrder
) {
}
