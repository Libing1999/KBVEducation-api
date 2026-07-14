package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.reflection.AdminReflectionSummary;
import com.kbv.education.dto.reflection.ReflectionAnswerInput;
import com.kbv.education.dto.reflection.ReflectionResponse;
import com.kbv.education.dto.reflection.TodayReflectionResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.ReflectionType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Daily reflections. Students submit at most one per day (editable until
 * midnight); admins review, edit text, listen to audio, delete, and export.
 * Parents have no access to reflection content.
 */
public interface ReflectionService {

    // --- student ---
    TodayReflectionResponse getToday(UUID studentId);

    List<ReflectionResponse> getMine(UUID studentId);

    ReflectionResponse getMineById(UUID studentId, UUID id);

    ReflectionResponse submit(UUID studentId, String answersJson, MultipartFile audio);

    ReflectionResponse update(UUID studentId, UUID id, String answersJson, MultipartFile audio, boolean removeAudio);

    FileDownloadResult downloadMyAudio(UUID studentId, UUID id);

    // --- admin ---
    PageResponse<AdminReflectionSummary> adminList(UUID cohortId, UUID studentId, ReflectionType type,
                                                   String search, int page, int size, String sort, String direction);

    ReflectionResponse adminGet(UUID id);

    ReflectionResponse adminUpdateText(UUID id, List<ReflectionAnswerInput> answers);

    void adminDelete(UUID id);

    FileDownloadResult adminDownloadAudio(UUID id);

    FileDownloadResult export(UUID id);
}
