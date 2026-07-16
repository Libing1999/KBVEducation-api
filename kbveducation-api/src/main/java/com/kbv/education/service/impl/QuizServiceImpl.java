package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.quiz.OptionRequest;
import com.kbv.education.dto.quiz.OptionResponse;
import com.kbv.education.dto.quiz.QuestionRequest;
import com.kbv.education.dto.quiz.QuestionResponse;
import com.kbv.education.dto.quiz.QuizRequest;
import com.kbv.education.dto.quiz.QuizResponse;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.QuizOption;
import com.kbv.education.entity.QuizQuestion;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.QuestionType;
import com.kbv.education.entity.enums.QuizStatus;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.repository.QuizOptionRepository;
import com.kbv.education.repository.QuizQuestionRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.service.NotificationService;
import com.kbv.education.service.QuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private static final int MCQ_OPTION_COUNT = 4;

    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public QuizResponse getByLesson(UUID lessonId) {
        Quiz quiz = quizRepository.findByLesson_IdAndDeletedFalse(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("No quiz configured for this lesson"));
        return toResponse(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResponse get(UUID quizId) {
        return toResponse(getQuiz(quizId));
    }

    @Override
    @Transactional
    @Audited(action = "QUIZ_EDITED", entityType = "QUIZ")
    public QuizResponse createOrUpdateForLesson(UUID lessonId, QuizRequest request) {
        Lesson lesson = lessonRepository.findByIdAndDeletedFalse(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));

        Quiz quiz = quizRepository.findByLesson_IdAndDeletedFalse(lessonId).orElseGet(() -> {
            Quiz q = new Quiz();
            q.setLesson(lesson);
            return q;
        });
        QuizStatus previousStatus = quiz.getStatus();
        quiz.setTitle(request.title());
        quiz.setDescription(request.description());
        quiz.setDurationMinutes(request.durationMinutes());
        quiz.setPassingMarks(request.passingMarks());
        quiz.setStatus(request.status() != null ? request.status() : QuizStatus.DRAFT);

        Quiz saved = quizRepository.save(quiz);
        log.info("Saved quiz for lesson {}", lessonId);

        // Notify students when the quiz becomes available (published on a published lesson).
        if (saved.isPublished() && previousStatus != QuizStatus.PUBLISHED && lesson.isPublished()) {
            notificationService.notifyCohortStudents(lesson.getCohort().getId(),
                    NotificationType.QUIZ_AVAILABLE, "Quiz Available", saved.getTitle(),
                    ReferenceType.QUIZ, saved.getId());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteQuiz(UUID quizId) {
        Quiz quiz = getQuiz(quizId);
        for (QuizQuestion question : questionRepository.findByQuiz_IdAndDeletedFalseOrderByDisplayOrderAsc(quizId)) {
            softDeleteQuestion(question);
        }
        quiz.setDeleted(true);
        quizRepository.save(quiz);
        log.info("Soft-deleted quiz {}", quizId);
    }

    @Override
    @Transactional
    public QuestionResponse addQuestion(UUID quizId, QuestionRequest request) {
        Quiz quiz = getQuiz(quizId);
        validateQuestion(request);

        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setQuestionText(request.questionText());
        question.setQuestionType(request.questionType());
        question.setMarks(request.marks() != null ? request.marks() : 1);
        question.setDisplayOrder(nextQuestionOrder(quizId));
        QuizQuestion savedQuestion = questionRepository.save(question);

        if (request.questionType() == QuestionType.MCQ) {
            saveOptions(savedQuestion, request.options());
        }
        return toQuestionResponse(savedQuestion);
    }

    @Override
    @Transactional
    public QuestionResponse updateQuestion(UUID questionId, QuestionRequest request) {
        QuizQuestion question = questionRepository.findByIdAndDeletedFalse(questionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
        validateQuestion(request);

        question.setQuestionText(request.questionText());
        question.setQuestionType(request.questionType());
        question.setMarks(request.marks() != null ? request.marks() : 1);
        QuizQuestion saved = questionRepository.save(question);

        // Replace options entirely.
        optionRepository.findByQuestion_IdAndDeletedFalseOrderByDisplayOrderAsc(questionId)
                .forEach(o -> {
                    o.setDeleted(true);
                    optionRepository.save(o);
                });
        if (request.questionType() == QuestionType.MCQ) {
            saveOptions(saved, request.options());
        }
        return toQuestionResponse(saved);
    }

    @Override
    @Transactional
    public void deleteQuestion(UUID questionId) {
        QuizQuestion question = questionRepository.findByIdAndDeletedFalse(questionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
        softDeleteQuestion(question);
        log.info("Soft-deleted question {}", questionId);
    }

    @Override
    @Transactional
    public void reorderQuestions(UUID quizId, ReorderRequest request) {
        for (ReorderRequest.Item item : request.items()) {
            QuizQuestion question = questionRepository.findByIdAndDeletedFalse(item.id())
                    .orElseThrow(() -> ResourceNotFoundException.of("Question", item.id()));
            if (!question.getQuiz().getId().equals(quizId)) {
                throw new BadRequestException("Question does not belong to this quiz");
            }
            question.setDisplayOrder(item.displayOrder());
            questionRepository.save(question);
        }
    }

    // --- helpers -----------------------------------------------------------

    private void validateQuestion(QuestionRequest request) {
        if (request.questionType() == QuestionType.MCQ) {
            List<OptionRequest> options = request.options();
            if (options == null || options.size() != MCQ_OPTION_COUNT) {
                throw new BadRequestException("An MCQ must have exactly " + MCQ_OPTION_COUNT + " options");
            }
            long correct = options.stream().filter(OptionRequest::correct).count();
            if (correct != 1) {
                throw new BadRequestException("An MCQ must have exactly one correct option");
            }
        }
    }

    private void saveOptions(QuizQuestion question, List<OptionRequest> options) {
        int order = 0;
        for (OptionRequest opt : options) {
            QuizOption option = new QuizOption();
            option.setQuestion(question);
            option.setOptionText(opt.optionText());
            option.setCorrect(opt.correct());
            option.setDisplayOrder(order++);
            optionRepository.save(option);
        }
    }

    private void softDeleteQuestion(QuizQuestion question) {
        optionRepository.findByQuestion_IdAndDeletedFalseOrderByDisplayOrderAsc(question.getId())
                .forEach(o -> {
                    o.setDeleted(true);
                    optionRepository.save(o);
                });
        question.setDeleted(true);
        questionRepository.save(question);
    }

    private int nextQuestionOrder(UUID quizId) {
        return questionRepository.findFirstByQuiz_IdAndDeletedFalseOrderByDisplayOrderDesc(quizId)
                .map(q -> q.getDisplayOrder() + 1)
                .orElse(0);
    }

    private Quiz getQuiz(UUID quizId) {
        return quizRepository.findByIdAndDeletedFalse(quizId)
                .orElseThrow(() -> ResourceNotFoundException.of("Quiz", quizId));
    }

    private QuizResponse toResponse(Quiz quiz) {
        List<QuestionResponse> questions =
                questionRepository.findByQuiz_IdAndDeletedFalseOrderByDisplayOrderAsc(quiz.getId()).stream()
                        .map(this::toQuestionResponse)
                        .toList();
        return new QuizResponse(
                quiz.getId(),
                quiz.getLesson().getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getDurationMinutes(),
                quiz.getPassingMarks(),
                quiz.getStatus(),
                questions.size(),
                questions);
    }

    private QuestionResponse toQuestionResponse(QuizQuestion question) {
        List<OptionResponse> options = question.getQuestionType() == QuestionType.MCQ
                ? optionRepository.findByQuestion_IdAndDeletedFalseOrderByDisplayOrderAsc(question.getId()).stream()
                        .map(o -> new OptionResponse(o.getId(), o.getOptionText(), o.isCorrect(), o.getDisplayOrder()))
                        .toList()
                : List.of();
        return new QuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getQuestionType(),
                question.getMarks(),
                question.getDisplayOrder(),
                options);
    }
}
