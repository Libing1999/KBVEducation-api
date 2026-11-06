package com.kbv.education.entity;

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

import java.util.UUID;

/**
 * What the AI returned for one job. {@code rawResponse} is kept exactly as received;
 * {@code resultJson} is the validated copy with the app's own checks added
 * (e.g. whether each quote was found word for word). The original submission is
 * never touched. When a target is analysed again, older results stay but lose
 * {@code current}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_results")
public class AiResult extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private AiJobType jobType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "reference_pack_version_id")
    private UUID referencePackVersionId;

    @Column(name = "raw_response", nullable = false, columnDefinition = "text")
    private String rawResponse;

    @Column(name = "result_json", nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(name = "recommendation", length = 30)
    private String recommendation;

    @Column(name = "trust_rating")
    private Integer trustRating;

    @Column(name = "engagement_rating")
    private Integer engagementRating;

    @Column(name = "flag_count", nullable = false)
    private int flagCount;

    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_read_tokens")
    private Long cacheReadTokens;

    @Column(name = "cache_write_tokens")
    private Long cacheWriteTokens;

    @Column(name = "via_batch", nullable = false)
    private boolean viaBatch;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;
}
