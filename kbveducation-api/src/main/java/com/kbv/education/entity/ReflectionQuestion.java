package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An admin-configured daily reflection question. Never hardcoded. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "reflection_questions")
public class ReflectionQuestion extends BaseEntity {

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
