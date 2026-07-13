package com.kbv.education.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Link between a parent user and a student user. In Phase 1 a parent is linked
 * to exactly one student initially; the model allows extension later.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "parent_student",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ps_parent_student",
                columnNames = {"parent_id", "student_id"}
        )
)
public class ParentStudent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
}
