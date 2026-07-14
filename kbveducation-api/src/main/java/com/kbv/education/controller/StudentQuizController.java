package com.kbv.education.controller;

import com.kbv.education.dto.quiz.QuizAttemptSummary;
import com.kbv.education.dto.quiz.QuizSubmissionResult;
import com.kbv.education.dto.quiz.StudentQuizResponse;
import com.kbv.education.dto.quiz.SubmitQuizRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.QuizAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Student quiz taking and attempt history. */
@Tag(name = "Student — Quizzes", description = "Take quizzes and view own attempts")
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentQuizController {

    private final QuizAttemptService quizAttemptService;

    @Operation(summary = "Open a quiz to take (correct answers are not included)")
    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<StudentQuizResponse> take(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID quizId) {
        return ApiResponse.success(quizAttemptService.getForStudent(principal.getId(), quizId));
    }

    @Operation(summary = "Submit a quiz (once, no retake)")
    @PostMapping("/quizzes/{quizId}/submit")
    public ApiResponse<QuizSubmissionResult> submit(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID quizId,
                                                    @Valid @RequestBody SubmitQuizRequest request) {
        return ApiResponse.success(quizAttemptService.submit(principal.getId(), quizId, request));
    }

    @Operation(summary = "List my quiz attempts")
    @GetMapping("/quiz-attempts")
    public ApiResponse<List<QuizAttemptSummary>> myAttempts(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(quizAttemptService.myAttempts(principal.getId()));
    }
}
