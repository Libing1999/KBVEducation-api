package com.kbv.education.entity;

import com.kbv.education.entity.enums.TierEventSource;
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

import java.math.BigDecimal;

/**
 * Append-only tier decision log. The latest row per student is the current
 * calculated tier; the latest row with {@link #confirmedTier} set is the
 * current confirmed/overridden tier — tiers are never permanently assigned.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tier_history")
public class TierHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "calculated_tier", nullable = false, length = 30)
    private String calculatedTier;

    @Column(name = "confirmed_tier", length = 30)
    private String confirmedTier;

    @Column(name = "is_override", nullable = false)
    private boolean override = false;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "composite_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal compositeScore;

    @Column(name = "practice_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal practicePercentage;

    @Column(name = "full_papers_count", nullable = false)
    private int fullPapersCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TierEventSource source;
}
