package com.kbv.education.entity;

import com.kbv.education.entity.enums.ScoreAuditEntityType;
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

import java.util.UUID;

/**
 * Immutable record of a score-related change (weight edit, tier override,
 * practice approval, ...). {@link BaseEntity#getCreatedBy()} /
 * {@link BaseEntity#getCreatedAt()} are the who/when — no separate fields.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "score_audit_logs")
public class ScoreAuditLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private ScoreAuditEntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "previous_value", columnDefinition = "text")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;
}
