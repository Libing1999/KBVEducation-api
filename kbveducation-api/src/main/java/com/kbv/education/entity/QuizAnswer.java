package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single answer within a {@link QuizAttempt}. For MCQ, {@code selectedOption}
 * and {@code correct} are set; for open-ended, {@code answerText} is set.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "quiz_answers")
public class QuizAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuizOption selectedOption;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    /** MCQ auto-score result; null for open-ended answers. */
    @Column(name = "is_correct")
    private Boolean correct;
}
