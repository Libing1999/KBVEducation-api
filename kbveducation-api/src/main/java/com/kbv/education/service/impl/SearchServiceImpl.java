package com.kbv.education.service.impl;

import com.kbv.education.dto.search.SearchResultItem;
import com.kbv.education.repository.AuditLogRepository;
import com.kbv.education.repository.CertificateRepository;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.repository.ReflectionQuestionRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Global search (Phase 5 Step 7, decision #11): plain ILIKE-equivalent queries
 * across the 9 named entity types, no {@code pg_trgm}/GIN index required.
 * Results are capped per-type so one noisy match doesn't crowd out the rest.
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int PER_TYPE_LIMIT = 5;

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final LessonRepository lessonRepository;
    private final HomeworkRepository homeworkRepository;
    private final QuizRepository quizRepository;
    private final ReflectionQuestionRepository reflectionQuestionRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final CertificateRepository certificateRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SearchResultItem> search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String q = query.trim();
        Pageable limit = PageRequest.of(0, PER_TYPE_LIMIT);
        List<SearchResultItem> results = new ArrayList<>();

        userRepository.search(q, limit).forEach(u -> results.add(new SearchResultItem(
                "USER", u.getId(), u.getFullName(), u.getEmail() + " · " + u.getRole().getName())));

        cohortRepository.search(q, limit).forEach(c -> results.add(new SearchResultItem(
                "COHORT", c.getId(), c.getName(), c.getStatus().name())));

        lessonRepository.search(q, limit).forEach(l -> results.add(new SearchResultItem(
                "LESSON", l.getId(), l.getTitle(), "Lesson " + l.getLessonNumber())));

        homeworkRepository.search(q, limit).forEach(h -> results.add(new SearchResultItem(
                "HOMEWORK", h.getId(), h.getTitle(), "Homework")));

        quizRepository.search(q, limit).forEach(qz -> results.add(new SearchResultItem(
                "QUIZ", qz.getId(), qz.getTitle(), qz.getStatus().name())));

        reflectionQuestionRepository.search(q, limit).forEach(r -> results.add(new SearchResultItem(
                "REFLECTION_QUESTION", r.getId(), truncate(r.getQuestionText()), "Reflection Question")));

        practiceSessionRepository.search(q, limit).forEach(p -> results.add(new SearchResultItem(
                "PRACTICE_SESSION", p.getId(), p.getSubject(), p.getStudyDate() + " · " + p.getStudyType())));

        certificateRepository.search(q, limit).forEach(c -> results.add(new SearchResultItem(
                "CERTIFICATE", c.getId(), c.getCertificateNumber(), c.getCertificateType() + " · " + c.getStatus())));

        auditLogRepository.search(q, limit).forEach(a -> results.add(new SearchResultItem(
                "AUDIT_LOG", a.getId(), a.getAction() + " — " + a.getEntityType(), String.valueOf(a.getActorEmailSnapshot()))));

        return results;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 80 ? value : value.substring(0, 80) + "…";
    }
}
