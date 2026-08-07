package com.kbv.education.entity.enums;

/**
 * Category of a logged practice session.
 *
 * <p>{@code PAST_PAPER}, {@code WEAKNESS_PRACTICE} and {@code GENERAL_PRACTICE}
 * are legacy values retained only so existing historical rows keep loading;
 * the Log Practice form no longer offers them. New sessions use one of the
 * five current values below.</p>
 */
public enum StudyType {
    // Legacy — historical rows only, not offered in the create form.
    PAST_PAPER,
    WEAKNESS_PRACTICE,
    GENERAL_PRACTICE,

    // Current
    PAST_PAPER_TEST_DAY,
    PAST_PAPER_IMPROVEMENT_DAY,
    TOPIC_STUDY,
    STRUCTURE_STUDY,
    OTHER
}
