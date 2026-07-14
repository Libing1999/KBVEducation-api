package com.kbv.education.dto.practice;

/** Admin approve/reject of a practice session, with an optional comment. */
public record ReviewDecisionRequest(
        String comment
) {
}
