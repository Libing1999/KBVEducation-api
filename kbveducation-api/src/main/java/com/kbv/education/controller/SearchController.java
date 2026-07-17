package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.search.SearchResultItem;
import com.kbv.education.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Global search (Phase 5 Step 7). SUPER_ADMIN only for now — the searchable
 * set spans other students' PII (email, submissions), so it stays scoped to
 * the one role that can already see all of it via the admin module.
 */
@Tag(name = "Search", description = "Global search across students, cohorts, lessons and more (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search across users, cohorts, lessons, homework, quizzes, reflections, practice sessions, certificates and audit logs")
    @GetMapping
    public ApiResponse<List<SearchResultItem>> search(@RequestParam String q) {
        return ApiResponse.success(searchService.search(q));
    }
}
