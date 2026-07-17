package com.kbv.education.dto.settings;

public record SystemSettingsResponse(
        String applicationName,
        String institutionName,
        String logoPath,
        String primaryColorHex,
        String secondaryColorHex,
        String accentColorHex,
        String timezone,
        String dateFormat,
        int maxFileSizeMb,
        String allowedFileTypes,
        int maxLoginAttempts,
        int passwordMinLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireDigit,
        boolean passwordRequireSpecial,
        int sessionTimeoutMinutes,
        boolean maintenanceMode,
        boolean certificateEnabled,
        boolean exportEnabled
) {
}
