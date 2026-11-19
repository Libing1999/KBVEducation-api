package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.entity.enums.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One AI analysis of one practice session or reflection. {@code provider}/{@code model}
 * always name the AI currently responsible for the job, so after a backup they name
 * the backup AI and {@code backupUsed} is set.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_jobs")
public class AiJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private AiJobType jobType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiJobStatus status = AiJobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "backup_used", nullable = false)
    private boolean backupUsed = false;

    @Column(name = "format_retries", nullable = false)
    private int formatRetries = 0;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private AiFailureReason failureReason;

    @Column(name = "failure_detail", columnDefinition = "text")
    private String failureDetail;

    @Column(name = "reference_pack_version_id")
    private UUID referencePackVersionId;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Batch custom_id / Gemini metadata key: the job's UUID (36 characters, letters, digits and hyphens). */
    public String customId() {
        return getId().toString();
    }
}
