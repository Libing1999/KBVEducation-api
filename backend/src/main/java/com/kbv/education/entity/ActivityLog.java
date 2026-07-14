package com.kbv.education.entity;

import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.ReferenceType;
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

import java.time.Instant;
import java.util.UUID;

/** A single entry in a student's activity timeline. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "activity_logs")
public class ActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType activityType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
