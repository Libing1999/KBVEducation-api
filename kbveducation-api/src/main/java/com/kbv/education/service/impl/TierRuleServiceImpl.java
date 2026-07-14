package com.kbv.education.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.tier.TierRuleResponse;
import com.kbv.education.dto.tier.UpsertTierRuleRequest;
import com.kbv.education.entity.TierRule;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.TierRuleMapper;
import com.kbv.education.repository.TierRuleRepository;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.service.TierRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TierRuleServiceImpl implements TierRuleService {

    private final TierRuleRepository tierRuleRepository;
    private final TierRuleMapper tierRuleMapper;
    private final ScoreAuditLogService scoreAuditLogService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TierRuleResponse> list() {
        return tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc().stream()
                .map(tierRuleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<TierRuleResponse> updateAll(List<UpsertTierRuleRequest> rules) {
        Map<UUID, TierRule> existingById = tierRuleRepository.findAll().stream()
                .filter(rule -> !rule.isDeleted())
                .collect(Collectors.toMap(TierRule::getId, Function.identity()));

        List<TierRule> updated = new ArrayList<>();
        List<AuditEntry> pendingAudits = new ArrayList<>();

        for (UpsertTierRuleRequest request : rules) {
            TierRule rule = existingById.get(request.id());
            if (rule == null) {
                throw ResourceNotFoundException.of("Tier rule", request.id());
            }

            boolean changed = isChanged(rule, request);
            String previousJson = changed ? toJson(tierRuleMapper.toResponse(rule)) : null;

            rule.setTierName(request.tierName());
            rule.setTierRank(request.tierRank());
            rule.setMinComposite(request.minComposite());
            rule.setMaxComposite(request.maxComposite());
            rule.setMinPracticePercentage(request.minPracticePercentage());
            rule.setMinFullPapers(request.minFullPapers());

            updated.add(rule);
            if (changed) {
                pendingAudits.add(new AuditEntry(rule, previousJson));
            }
        }

        validateNoOverlap(updated);

        List<TierRule> saved = tierRuleRepository.saveAll(updated);

        for (AuditEntry entry : pendingAudits) {
            scoreAuditLogService.record(ScoreAuditEntityType.TIER, entry.rule().getId(), null,
                    "TIER_RULE_UPDATED", entry.previousJson(), toJson(tierRuleMapper.toResponse(entry.rule())), null);
        }

        log.info("Updated {} tier rule(s)", saved.size());
        return saved.stream()
                .sorted(Comparator.comparingInt(TierRule::getTierRank))
                .map(tierRuleMapper::toResponse)
                .toList();
    }

    private boolean isChanged(TierRule rule, UpsertTierRuleRequest request) {
        return !Objects.equals(rule.getTierName(), request.tierName())
                || rule.getTierRank() != request.tierRank()
                || decimalDiffers(rule.getMinComposite(), request.minComposite())
                || decimalDiffers(rule.getMaxComposite(), request.maxComposite())
                || decimalDiffers(rule.getMinPracticePercentage(), request.minPracticePercentage())
                || rule.getMinFullPapers() != request.minFullPapers();
    }

    private boolean decimalDiffers(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    /** Ensures the [minComposite, maxComposite] ranges are contiguous and don't overlap. */
    private void validateNoOverlap(List<TierRule> rules) {
        List<TierRule> sorted = rules.stream()
                .sorted(Comparator.comparing(TierRule::getMinComposite))
                .toList();
        for (int i = 0; i < sorted.size() - 1; i++) {
            BigDecimal currentMax = sorted.get(i).getMaxComposite();
            BigDecimal nextMin = sorted.get(i + 1).getMinComposite();
            if (currentMax == null || currentMax.compareTo(nextMin) >= 0) {
                throw new BusinessRuleException("Tier thresholds cannot overlap");
            }
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private record AuditEntry(TierRule rule, String previousJson) {
    }
}
