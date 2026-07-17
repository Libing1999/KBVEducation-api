package com.kbv.education.service;

import com.kbv.education.dto.settings.PublicSettingsResponse;
import com.kbv.education.dto.settings.SystemSettingsResponse;
import com.kbv.education.dto.settings.UpdateSystemSettingsRequest;
import com.kbv.education.entity.SystemSettings;

import java.util.Set;

public interface SystemSettingsService {

    SystemSettingsResponse getActive();

    PublicSettingsResponse getPublic();

    SystemSettingsResponse update(UpdateSystemSettingsRequest request);

    /** Raw entity for internal callers (upload validation, maintenance filter, JWT session-timeout). */
    SystemSettings getActiveEntity();

    /** Parses the comma-separated allowed-types column into a lookup set (lower-cased). */
    Set<String> allowedFileExtensions();
}
