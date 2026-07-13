package com.kbv.education.service;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.quiz.QuestionRequest;
import com.kbv.education.dto.quiz.QuestionResponse;
import com.kbv.education.dto.quiz.QuizRequest;
import com.kbv.education.dto.quiz.QuizResponse;

import java.util.UUID;

/** Admin quiz builder (Step 4). One quiz per lesson. */
public interface QuizService {

    /** The quiz for a lesson, or throws if none exists yet. */
    QuizResponse getByLesson(UUID lessonId);

    QuizResponse get(UUID quizId);

    /** Create the lesson's quiz if absent, otherwise update it. */
    QuizResponse createOrUpdateForLesson(UUID lessonId, QuizRequest request);

    void deleteQuiz(UUID quizId);

    QuestionResponse addQuestion(UUID quizId, QuestionRequest request);

    QuestionResponse updateQuestion(UUID questionId, QuestionRequest request);

    void deleteQuestion(UUID questionId);

    void reorderQuestions(UUID quizId, ReorderRequest request);
}
