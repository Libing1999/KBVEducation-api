package com.kbv.education.repository;

import com.kbv.education.entity.TierRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TierRuleRepository extends JpaRepository<TierRule, UUID> {

    List<TierRule> findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
}
