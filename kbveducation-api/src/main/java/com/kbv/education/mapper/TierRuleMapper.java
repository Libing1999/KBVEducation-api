package com.kbv.education.mapper;

import com.kbv.education.dto.tier.TierRuleResponse;
import com.kbv.education.entity.TierRule;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TierRuleMapper {

    TierRuleResponse toResponse(TierRule rule);
}
