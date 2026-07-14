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

/** A student's typed answer to one configured reflection question. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "reflection_answers")
public class ReflectionAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reflection_entry_id", nullable = false)
    private ReflectionEntry reflectionEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ReflectionQuestion question;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;
}
