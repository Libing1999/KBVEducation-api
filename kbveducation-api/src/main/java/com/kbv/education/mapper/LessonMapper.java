package com.kbv.education.mapper;

import com.kbv.education.dto.lesson.LessonResponse;
import com.kbv.education.entity.Lesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LessonMapper {

    @Mapping(target = "cohortId", source = "lesson.cohort.id")
    @Mapping(target = "cohortName", source = "lesson.cohort.name")
    @Mapping(target = "fileCount", source = "fileCount")
    @Mapping(target = "hasQuiz", source = "hasQuiz")
    @Mapping(target = "hasHomework", source = "hasHomework")
    LessonResponse toResponse(Lesson lesson, long fileCount, boolean hasQuiz, boolean hasHomework);
}
