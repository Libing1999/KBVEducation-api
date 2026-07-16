package com.kbv.education.service.impl;

import com.kbv.education.entity.enums.CertificateType;

/** Fixed display titles per certificate type — shared by preview and generate. */
final class CertificateTitles {

    private CertificateTitles() {
    }

    static String of(CertificateType type) {
        return switch (type) {
            case TIER_1 -> "Tier 1 Achievement Certificate";
            case TIER_2 -> "Tier 2 Achievement Certificate";
            case TIER_3 -> "Tier 3 Achievement Certificate";
            case COMPLETION -> "Certificate of Completion";
        };
    }
}
