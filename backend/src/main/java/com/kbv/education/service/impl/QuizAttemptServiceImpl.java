package com.kbv.education.service.impl;

import com.kbv.education.dto.quiz.QuizAttemptSummary;
import com.kbv.education.dto.quiz.QuizSubmissionResult;
import com.kbv.education.dto.quiz.StudentQuizResponse;
import com.kbv.education.dto.quiz.SubmitQuizRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.QuizAnswer;
import com.kbv.education.entity.QuizAttempt;
import com.kbv.education.entity.QuizOption;
import com.kbv.education.entity.QuizQuestion;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.QuestionType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.QuizAnswerRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.QuizOptionRepository;
import com.kbv.education.repository.QuizQuestionRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.QuizAttemptSpecifications;
import com.kbv.education.service.NotificationService;
import com.kbv.education.service.QuizAttemptService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAttemptServiceImpl implements QuizAttemptService {

    private static final List<String> SORTABLE = List.of("submittedAt", "createdAt", "score");

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public StudentQuizResponse getForStudent(UUID userId, UUID quizId) {
        Quiz quiz = accessibleQuiz(userId, quizId);
        boolean alreadySubmitted = attemptRepository.existsByQuiz_IdAndStudent_IdAndDeletedFalse(quizId, userId);

        List<StudentQuizResponse.Question> questions =
                questionRepository.findByQuiz_IdAndDeletedFalseOrderByDisplayOrderAsc(quizId).stream()
                        .map(q -> new StudentQuizResponse.Question(
                                q.getId(),
                                q.getQuestionText(),
                                q.getQuestionType(),
                                q.getMarks(),
                                q.getDisplayOrder(),
                                q.getQuestionType() == QuestionType.MCQ
                                        ? optionRepository
                                            .findByQuestion_IdAndDeletedFalseOrderByDisplayOrderAsc(q.getId()).stream()
                                            .map(o -> new StudentQuizResponse.Option(o.getId(), o.getOptionText()))
                                            .toList()
                                        : List.of()))
                        .toList();

        return new StudentQuizResponse(
                quiz.getId(),
                quiz.getLesson().getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getDurationMinutes(),
                alreadySubmitted,
                questions);
    }

    @Override
    @Transactional
    public QuizSubmissionResult submit(UUID userId, UUID quizId, SubmitQuizRequest request) {
        Quiz quiz = accessibleQuiz(userId, quizId);
        if (attemptRepository.existsByQuiz_IdAndStudent_IdAndDeletedFalse(quizId, userId)) {
            throw new BusinessRuleException("You have already submitted this quiz");
        }

        User student = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", userId));

        List<QuizQuestion> questions =
                questionRepository.findByQuiz_IdAndDeletedFalseOrderByDisplayOrderAsc(quizId);
        Map<UUID, QuizQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        QuizAttempt savedAttempt = attemptRepository.save(attempt);

        int score = 0;
        int maxScore = 0;
        int answered = 0;

        // Max score = total marks of auto-scorable (MCQ) questions.
        for (QuizQuestion q : questions) {
            if (q.getQuestionType() == QuestionType.MCQ) {
                maxScore += q.getMarks();
            }
        }

        if (request.answers() != null) {
            for (SubmitQuizRequest.Answer ans : request.answers()) {
                QuizQuestion question = questionById.get(ans.questionId());
                if (question == null) {
                    continue; // ignore answers to unknown/foreign questions
                }

                QuizAnswer answer = new QuizAnswer();
                answer.setAttempt(savedAttempt);
                answer.setQuestion(question);

                if (question.getQuestionType() == QuestionType.MCQ) {
                    if (ans.selectedOptionId() != null) {
                        QuizOption option = optionRepository.findByIdAndDeletedFalse(ans.selectedOptionId())
                                .filter(o -> o.getQuestion().getId().equals(question.getId()))
                                .orElseThrow(() -> new BusinessRuleException("Invalid option for question"));
                        answer.setSelectedOption(option);
                        boolean correct = option.isCorrect();
                        answer.setCorrect(correct);
                        if (correct) {
                            score += question.getMarks();
                        }
                        answered++;
                    }
                } else {
                    if (StringUtils.hasText(ans.answerText())) {
                        answer.setAnswerText(ans.answerText());
                        answered++;
                    }
                }
                answerRepository.save(answer);
            }
        }

        savedAttempt.setScore(score);
        savedAttempt.setMaxScore(maxScore);
        attemptRepository.save(savedAttempt);

        notificationService.notifyAdmins(NotificationType.QUIZ_SUBMITTED, "Quiz Submitted",
                student.getFullName() + " submitted " + quiz.getTitle(), ReferenceType.QUIZ, quiz.getId());

        log.info("Student {} submitted quiz {} (score {}/{})", userId, quizId, score, maxScore);
        return new QuizSubmissionResult(
                savedAttempt.getId(),
                savedAttempt.getSubmittedAt(),
                questions.size(),
                answered,
                "Quiz Submitted Successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizAttemptSummary> myAttempts(UUID userId) {
        return attemptRepository.findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuizAttemptSummary> adminList(UUID lessonId, UUID studentId, String search,
                                                      int page, int size, String sort, String direction) {
        Specification<QuizAttempt> spec = Specification.where(QuizAttemptSpecifications.notDeleted())
                .and(QuizAttemptSpecifications.inLesson(lessonId))
                .and(QuizAttemptSpecifications.forStudent(studentId))
                .and(QuizAttemptSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<QuizAttempt> result = attemptRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toSummary);
    }

    // --- helpers -----------------------------------------------------------

    private Quiz accessibleQuiz(UUID studentId, UUID quizId) {
        Quiz quiz = quizRepository.findByIdAndDeletedFalse(quizId)
                .orElseThrow(() -> ResourceNotFoundException.of("Quiz", quizId));
        Lesson lesson = quiz.getLesson();
        UUID cohortId = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getId())
                .orElse(null);
        if (!quiz.isPublished() || !lesson.isPublished() || lesson.isDeleted()
                || cohortId == null || !lesson.getCohort().getId().equals(cohortId)) {
            throw ResourceNotFoundException.of("Quiz", quizId);
        }
        return quiz;
    }

    private QuizAttemptSummary toSummary(QuizAttempt attempt) {
        Quiz quiz = attempt.getQuiz();
        Lesson lesson = quiz.getLesson();
        User student = attempt.getStudent();
        return new QuizAttemptSummary(
                attempt.getId(),
                quiz.getId(),
                quiz.getTitle(),
                lesson.getId(),
                lesson.getTitle(),
                student.getId(),
                student.getFullName(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getSubmittedAt());
    }
}
