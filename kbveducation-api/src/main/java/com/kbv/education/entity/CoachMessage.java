package com.kbv.education.entity;

import com.kbv.education.entity.enums.MessageTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hand-sent note from staff (the coach) — the one manual messaging channel
 * in the app. Addressed either to a single student ({@link MessageTargetType#INDIVIDUAL},
 * {@code targetStudent} set) or to an entire cohort ({@link MessageTargetType#COLLECTIVE},
 * {@code targetCohort} set). Surfaces read-only in the student Leaderboard's
 * "Live Action" drawer and the parent "Messages from Bhavya" card — never
 * editable/deletable by students or parents. Read/unread is tracked
 * separately per recipient via {@link CoachMessageRead}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_message")
public class CoachMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private MessageTargetType targetType;

    /** Set only when {@code targetType == INDIVIDUAL}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_student_id")
    private User targetStudent;

    /** Set only when {@code targetType == COLLECTIVE}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_cohort_id")
    private Cohort targetCohort;

    /** Short label, e.g. "Cohort win" / "Shout-out". */
    @Column(name = "tag", nullable = false, length = 60)
    private String tag;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;
}
