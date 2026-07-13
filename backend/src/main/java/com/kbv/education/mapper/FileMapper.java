package com.kbv.education.mapper;

import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.entity.HomeworkSubmissionFile;
import com.kbv.education.entity.LessonFile;
import org.mapstruct.Mapper;

/** Maps the (structurally identical) file entities to the shared FileResponse. */
@Mapper(componentModel = "spring")
public interface FileMapper {

    FileResponse toResponse(LessonFile file);

    FileResponse toResponse(HomeworkSubmissionFile file);
}
