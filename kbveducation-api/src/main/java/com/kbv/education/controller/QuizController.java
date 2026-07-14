package com.kbv.education.controller;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.quiz.QuestionRequest;
import com.kbv.education.dto.quiz.QuestionResponse;
import com.kbv.education.dto.quiz.QuizRequest;
import com.kbv.education.dto.quiz.QuizResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Quiz Builder", description = "Quiz and question management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class QuizController {

    private final QuizService quizService;

    @Operation(summary = "Get a lesson's quiz")
    @GetMapping("/lessons/{lessonId}/quiz")
    public ApiResponse<QuizResponse> getByLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success(quizService.getByLesson(lessonId));
    }

    @Operation(summary = "Create or update a lesson's quiz")
    @PutMapping("/lessons/{lessonId}/quiz")
    public ApiResponse<QuizResponse> upsert(@PathVariable UUID lessonId,
                                            @Valid @RequestBody QuizRequest request) {
        return ApiResponse.success("Quiz saved", quizService.createOrUpdateForLesson(lessonId, request));
    }

    @Operation(summary = "Get a quiz by id")
    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<QuizResponse> get(@PathVariable UUID quizId) {
        return ApiResponse.success(quizService.get(quizId));
    }

    @Operation(summary = "Preview a quiz (full detail)")
    @GetMapping("/quizzes/{quizId}/preview")
    public ApiResponse<QuizResponse> preview(@PathVariable UUID quizId) {
        return ApiResponse.success(quizService.get(quizId));
    }

    @Operation(summary = "Delete a quiz")
    @DeleteMapping("/quizzes/{quizId}")
    public ApiResponse<Void> delete(@PathVariable UUID quizId) {
        quizService.deleteQuiz(quizId);
        return ApiResponse.success("Quiz deleted");
    }

    @Operation(summary = "Add a question to a quiz")
    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(@PathVariable UUID quizId,
                                                                     @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question added", quizService.addQuestion(quizId, request)));
    }

    @Operation(summary = "Update a question")
    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionResponse> updateQuestion(@PathVariable UUID questionId,
                                                        @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success("Question updated", quizService.updateQuestion(questionId, request));
    }

    @Operation(summary = "Delete a question")
    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<Void> deleteQuestion(@PathVariable UUID questionId) {
        quizService.deleteQuestion(questionId);
        return ApiResponse.success("Question deleted");
    }

    @Operation(summary = "Reorder a quiz's questions")
    @PatchMapping("/quizzes/{quizId}/questions/reorder")
    public ApiResponse<Void> reorderQuestions(@PathVariable UUID quizId,
                                              @Valid @RequestBody ReorderRequest request) {
        quizService.reorderQuestions(quizId, request);
        return ApiResponse.success("Questions reordered");
    }
}
