package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A configurable graduation-tier threshold. {@link #tierRank} orders tiers
 * best-first (1 = best); the tier engine picks the first rule a student's
 * composite/practice/full-papers satisfy.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tier_rules")
public class TierRule extends BaseEntity {

    @Column(name = "tier_name", nullable = false, length = 30)
    private String tierName;

    @Column(name = "tier_rank", nullable = false)
    private int tierRank;

    @Column(name = "min_composite", nullable = false, precision = 5, scale = 2)
    private BigDecimal minComposite;

    @Column(name = "max_composite", precision = 5, scale = 2)
    private BigDecimal maxComposite;

    @Column(name = "min_practice_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPracticePercentage;

    @Column(name = "min_full_papers", nullable = false)
    private int minFullPapers;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
