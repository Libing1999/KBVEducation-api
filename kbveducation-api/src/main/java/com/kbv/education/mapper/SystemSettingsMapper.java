package com.kbv.education.mapper;

import com.kbv.education.dto.settings.PublicSettingsResponse;
import com.kbv.education.dto.settings.SystemSettingsResponse;
import com.kbv.education.entity.SystemSettings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SystemSettingsMapper {

    SystemSettingsResponse toResponse(SystemSettings settings);

    PublicSettingsResponse toPublicResponse(SystemSettings settings);
}
