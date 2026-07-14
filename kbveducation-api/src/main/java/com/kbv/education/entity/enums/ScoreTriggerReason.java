package com.kbv.education.entity.enums;

/**
 * What caused a {@code student_scores} row to be (re)calculated.
 */
public enum ScoreTriggerReason {
    PRACTICE_CHANGE,
    REFLECTION_CHANGE,
    HOMEWORK_CHANGE,
    QUIZ_CHANGE,
    CONFIG_CHANGE,
    MANUAL_RECALC
}
