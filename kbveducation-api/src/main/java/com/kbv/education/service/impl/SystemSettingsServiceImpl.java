package com.kbv.education.service.impl;

import com.kbv.education.dto.settings.PublicSettingsResponse;
import com.kbv.education.dto.settings.SystemSettingsResponse;
import com.kbv.education.dto.settings.UpdateSystemSettingsRequest;
import com.kbv.education.entity.SystemSettings;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.SystemSettingsMapper;
import com.kbv.education.repository.SystemSettingsRepository;
import com.kbv.education.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsMapper systemSettingsMapper;

    @Override
    @Transactional(readOnly = true)
    public SystemSettingsResponse getActive() {
        return systemSettingsMapper.toResponse(getActiveEntity());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSettingsResponse getPublic() {
        return systemSettingsMapper.toPublicResponse(getActiveEntity());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "systemSettings", allEntries = true)
    public SystemSettingsResponse update(UpdateSystemSettingsRequest request) {
        SystemSettings settings = getActiveEntity();
        settings.setApplicationName(request.applicationName());
        settings.setInstitutionName(request.institutionName());
        settings.setLogoPath(request.logoPath());
        settings.setPrimaryColorHex(request.primaryColorHex());
        settings.setSecondaryColorHex(request.secondaryColorHex());
        settings.setAccentColorHex(request.accentColorHex());
        settings.setTimezone(request.timezone());
        settings.setDateFormat(request.dateFormat());
        settings.setMaxFileSizeMb(request.maxFileSizeMb());
        settings.setAllowedFileTypes(request.allowedFileTypes());
        settings.setMaxLoginAttempts(request.maxLoginAttempts());
        settings.setPasswordMinLength(request.passwordMinLength());
        settings.setPasswordRequireUppercase(request.passwordRequireUppercase());
        settings.setPasswordRequireLowercase(request.passwordRequireLowercase());
        settings.setPasswordRequireDigit(request.passwordRequireDigit());
        settings.setPasswordRequireSpecial(request.passwordRequireSpecial());
        settings.setSessionTimeoutMinutes(request.sessionTimeoutMinutes());
        settings.setMaintenanceMode(request.maintenanceMode());
        settings.setCertificateEnabled(request.certificateEnabled());
        settings.setExportEnabled(request.exportEnabled());

        SystemSettings saved = systemSettingsRepository.save(settings);
        log.info("Updated system settings {}", saved.getId());
        return systemSettingsMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettings getActiveEntity() {
        return systemSettingsRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active system settings found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> allowedFileExtensions() {
        String raw = getActiveEntity().getAllowedFileTypes();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
