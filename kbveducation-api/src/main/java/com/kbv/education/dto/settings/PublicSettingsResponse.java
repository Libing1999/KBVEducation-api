package com.kbv.education.dto.settings;

/** Unauthenticated-safe subset — no upload limits, no security policy, no feature toggles. */
public record PublicSettingsResponse(
        String applicationName,
        String institutionName,
        String logoPath,
        String primaryColorHex,
        String secondaryColorHex,
        String accentColorHex,
        boolean maintenanceMode
) {
}
