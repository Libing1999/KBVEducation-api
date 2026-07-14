package com.kbv.education.dto.reflection;

import java.time.LocalDate;
import java.util.List;

/**
 * What the student needs to fill in today's reflection: the enabled questions
 * and their existing entry for today (null if not yet submitted).
 */
public record TodayReflectionResponse(
        LocalDate date,
        List<ReflectionQuestionResponse> questions,
        ReflectionResponse reflection
) {
}
