package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiBatchStatus;
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

/** A batch sent to one AI provider. Its jobs point back to it through {@link AiJob#getBatchId()}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_batches")
public class AiBatch extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "provider_batch_id", nullable = false, length = 255)
    private String providerBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiBatchStatus status = AiBatchStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** 24 hours for Claude, 48 hours for Gemini; unfinished jobs go to the backup after this. */
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
