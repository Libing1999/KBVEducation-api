package com.kbv.education.dto.search;

import java.util.UUID;

public record SearchResultItem(
        String entityType,
        UUID id,
        String title,
        String subtitle
) {
}
