package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiJobStatus;
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

    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    /** Only meaningful (and required) for the two past-paper study types. */
    @Column(name = "year")
    private Integer year;

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

    /** JSON array of numbered turns ({@code n}, {@code speaker}, {@code text}); the transcript itself is never changed. */
    @Column(name = "transcript_turns", columnDefinition = "text")
    private String transcriptTurns;

    @Column(name = "turn_count")
    private Integer turnCount;

    @Column(name = "transcript_chars")
    private Integer transcriptChars;

    /** LABELS (speaker labels found), PARAGRAPHS (no labels: split on blank lines), or NONE (no transcript). */
    @Column(name = "turn_split_method", length = 20)
    private String turnSplitMethod;

    /** Set when the AI auto-approved the session (only possible with auto-approve switched on). */
    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved = false;

    @Column(name = "auto_approved_at")
    private Instant autoApprovedAt;

    /** Auto-approved, but also placed in the admin's review queue as a spot check. */
    @Column(name = "spot_check", nullable = false)
    private boolean spotCheck = false;

    /** The admin's one-line reason when their decision differs from the AI's recommendation. */
    @Column(name = "ai_override_reason", length = 500)
    private String aiOverrideReason;

    // Copy of the current AI headline (see AiResult) so the review list can filter and sort.

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", length = 20)
    private AiJobStatus aiStatus;

    @Column(name = "ai_trust_rating")
    private Integer aiTrustRating;

    @Column(name = "ai_recommendation", length = 30)
    private String aiRecommendation;

    @Column(name = "ai_flag_count")
    private Integer aiFlagCount;
}
