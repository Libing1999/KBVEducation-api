package com.kbv.education.service;

import com.kbv.education.dto.quiz.QuizAttemptSummary;
import com.kbv.education.dto.quiz.QuizSubmissionResult;
import com.kbv.education.dto.quiz.StudentQuizResponse;
import com.kbv.education.dto.quiz.SubmitQuizRequest;
import com.kbv.education.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

/** Student quiz taking + admin attempts viewer (Step 4). */
public interface QuizAttemptService {

    StudentQuizResponse getForStudent(UUID userId, UUID quizId);

    QuizSubmissionResult submit(UUID userId, UUID quizId, SubmitQuizRequest request);

    List<QuizAttemptSummary> myAttempts(UUID userId);

    PageResponse<QuizAttemptSummary> adminList(UUID lessonId, UUID studentId, String search,
                                               int page, int size, String sort, String direction);
}
