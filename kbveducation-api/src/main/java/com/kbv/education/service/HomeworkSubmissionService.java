package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.homework.HomeworkSubmissionResponse;
import com.kbv.education.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Student homework submission + admin submissions viewer (Step 5). */
public interface HomeworkSubmissionService {

    HomeworkSubmissionResponse submit(UUID userId, UUID lessonId, String note, MultipartFile[] files);

    /** The caller's submission for a lesson, or throws if not submitted yet. */
    HomeworkSubmissionResponse myByLesson(UUID userId, UUID lessonId);

    List<HomeworkSubmissionResponse> myAll(UUID userId);

    PageResponse<HomeworkSubmissionResponse> adminList(UUID lessonId, UUID studentId, String search,
                                                       int page, int size, String sort, String direction);

    /** Admin download of any submission file. */
    FileDownloadResult downloadFile(UUID fileId);

    /** Student/parent download of a file belonging to the effective student. */
    FileDownloadResult downloadMyFile(UUID userId, UUID fileId);
}
