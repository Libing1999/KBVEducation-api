package com.kbv.education.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * Builds a {@link Pageable} from raw request params, applying safe defaults and
 * a maximum page size. A whitelist of sortable fields prevents arbitrary
 * property access (which would otherwise throw at query time).
 */
public final class PageableBuilder {

    private PageableBuilder() {
    }

    public static Pageable build(int page, int size, String sort, String direction, List<String> allowedSortFields) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? AppConstants.DEFAULT_PAGE_SIZE : Math.min(size, AppConstants.MAX_PAGE_SIZE);

        String field = (sort == null || sort.isBlank()) ? AppConstants.DEFAULT_SORT_FIELD : sort;
        if (allowedSortFields != null && !allowedSortFields.contains(field)) {
            field = AppConstants.DEFAULT_SORT_FIELD;
        }

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, field));
    }
}
