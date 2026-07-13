package com.kbv.education.controller;

import com.kbv.education.dto.quiz.QuizAttemptSummary;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.service.QuizAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Quiz Attempts", description = "View quiz attempts (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/quiz-attempts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class QuizAttemptController {

    private final QuizAttemptService quizAttemptService;

    @Operation(summary = "List quiz attempts with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<QuizAttemptSummary>> list(
            @RequestParam(required = false) UUID lessonId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(
                quizAttemptService.adminList(lessonId, studentId, search, page, size, sort, direction));
    }
}
