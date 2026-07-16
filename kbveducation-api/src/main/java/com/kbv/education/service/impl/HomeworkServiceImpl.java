package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.homework.HomeworkRequest;
import com.kbv.education.dto.homework.HomeworkResponse;
import com.kbv.education.entity.Homework;
import com.kbv.education.entity.Lesson;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.service.HomeworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkServiceImpl implements HomeworkService {

    private final LessonRepository lessonRepository;
    private final HomeworkRepository homeworkRepository;

    @Override
    @Transactional(readOnly = true)
    public HomeworkResponse getByLesson(UUID lessonId) {
        Homework homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("No homework configured for this lesson"));
        return toResponse(homework);
    }

    @Override
    @Transactional
    public HomeworkResponse createOrUpdateForLesson(UUID lessonId, HomeworkRequest request) {
        Lesson lesson = lessonRepository.findByIdAndDeletedFalse(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));

        Homework homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lessonId).orElseGet(() -> {
            Homework h = new Homework();
            h.setLesson(lesson);
            return h;
        });
        homework.setTitle(request.title());
        homework.setInstructions(request.instructions());
        homework.setDueDate(request.dueDate());
        homework.setAllowedFileTypes(joinTypes(request.allowedFileTypes()));
        homework.setMaxFileSizeMb(request.maxFileSizeMb());

        Homework saved = homeworkRepository.save(homework);
        log.info("Saved homework for lesson {}", lessonId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Audited(action = "HOMEWORK_DELETED", entityType = "HOMEWORK")
    public void delete(UUID homeworkId) {
        Homework homework = homeworkRepository.findByIdAndDeletedFalse(homeworkId)
                .orElseThrow(() -> ResourceNotFoundException.of("Homework", homeworkId));
        homework.setDeleted(true);
        homeworkRepository.save(homework);
        log.info("Soft-deleted homework {}", homeworkId);
    }

    private HomeworkResponse toResponse(Homework homework) {
        return new HomeworkResponse(
                homework.getId(),
                homework.getLesson().getId(),
                homework.getLesson().getTitle(),
                homework.getTitle(),
                homework.getInstructions(),
                homework.getDueDate(),
                splitTypes(homework.getAllowedFileTypes()),
                homework.getMaxFileSizeMb());
    }

    private String joinTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return types.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT).replace(".", ""))
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    private List<String> splitTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
