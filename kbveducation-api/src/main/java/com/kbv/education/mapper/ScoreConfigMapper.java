package com.kbv.education.mapper;

import com.kbv.education.dto.scoreconfig.ScoreConfigResponse;
import com.kbv.education.entity.ScoreConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScoreConfigMapper {

    ScoreConfigResponse toResponse(ScoreConfig config);
}
