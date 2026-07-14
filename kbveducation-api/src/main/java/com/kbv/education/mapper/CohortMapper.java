package com.kbv.education.mapper;

import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.entity.Cohort;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CohortMapper {

    @Mapping(target = "studentCount", source = "studentCount")
    CohortResponse toResponse(Cohort cohort, long studentCount);
}
