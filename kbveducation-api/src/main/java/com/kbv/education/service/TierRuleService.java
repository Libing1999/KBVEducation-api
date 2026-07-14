package com.kbv.education.service;

import com.kbv.education.dto.tier.TierRuleResponse;
import com.kbv.education.dto.tier.UpsertTierRuleRequest;

import java.util.List;

public interface TierRuleService {

    List<TierRuleResponse> list();

    List<TierRuleResponse> updateAll(List<UpsertTierRuleRequest> rules);
}
