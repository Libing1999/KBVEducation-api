package com.kbv.education.entity;

import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.StudyType;
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
import java.time.LocalDate;

/**
 * A logged study session. Saved verbatim (no AI validation) and reviewed
 * manually by an admin. A future {@code PracticeValidationService} may set the
 * status automatically without any schema change.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "practice_sessions")
public class PracticeSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_type", nullable = false, length = 30)
    private StudyType studyType;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PracticeStatus status = PracticeStatus.PENDING_REVIEW;

    @Column(name = "admin_comment", columnDefinition = "text")
    private String adminComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
