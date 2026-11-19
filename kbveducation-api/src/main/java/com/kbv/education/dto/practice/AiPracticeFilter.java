package com.kbv.education.dto.practice;

import com.kbv.education.entity.enums.AiJobStatus;

/** Admin review list filters on the AI headline. Every field is optional. */
public record AiPracticeFilter(
        String recommendation,
        Integer minRating,
        Integer maxRating,
        Boolean flagged,
        AiJobStatus aiStatus,
        Boolean autoApproved,
        Boolean spotCheck
) {

    public static AiPracticeFilter none() {
        return new AiPracticeFilter(null, null, null, null, null, null, null);
    }
}
