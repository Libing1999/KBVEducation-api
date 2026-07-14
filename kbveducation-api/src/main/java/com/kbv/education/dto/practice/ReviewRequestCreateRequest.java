package com.kbv.education.dto.practice;

/** A student's request to have a rejected practice session reviewed again. */
public record ReviewRequestCreateRequest(
        String reason
) {
}
