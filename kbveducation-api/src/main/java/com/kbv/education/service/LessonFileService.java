package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.file.FileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Lesson file uploads/downloads (Step 3). */
public interface LessonFileService {

    List<FileResponse> listByLesson(UUID lessonId);

    List<FileResponse> upload(UUID lessonId, MultipartFile[] files);

    FileDownloadResult download(UUID fileId);

    void delete(UUID fileId);
}
