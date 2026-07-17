package com.kbv.education.dto.settings;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingsRequest(
        @NotBlank @Size(max = 150) String applicationName,
        @NotBlank @Size(max = 150) String institutionName,
        String logoPath,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColorHex,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String secondaryColorHex,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String accentColorHex,
        @NotBlank String timezone,
        @NotBlank String dateFormat,
        @Min(1) int maxFileSizeMb,
        @NotBlank String allowedFileTypes,
        @Min(1) int maxLoginAttempts,
        @Min(6) int passwordMinLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireDigit,
        boolean passwordRequireSpecial,
        @Min(5) int sessionTimeoutMinutes,
        boolean maintenanceMode,
        boolean certificateEnabled,
        boolean exportEnabled
) {
}
