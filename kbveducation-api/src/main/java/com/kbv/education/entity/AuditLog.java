package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * General-purpose, cross-cutting audit trail — separate from the Phase 4
 * {@code score_audit_logs}/{@link ScoreAuditLog}, which stays scoped to
 * score/tier domain events. The actor is {@link BaseEntity#getCreatedBy()};
 * {@link #actorEmailSnapshot} additionally captures the attempted email for
 * events with no resolvable user (e.g. a failed login for an unknown
 * address). Write-side instrumentation (the {@code @Audited} aspect) lands
 * in Phase 5 Step 4 — this entity exists from Step 3 onward so the generic
 * export module has something to query.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_email_snapshot", length = 255)
    private String actorEmailSnapshot;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;
}
