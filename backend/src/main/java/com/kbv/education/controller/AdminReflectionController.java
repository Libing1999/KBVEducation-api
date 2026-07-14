package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.reflection.AdminReflectionSummary;
import com.kbv.education.dto.reflection.ReflectionAnswerInput;
import com.kbv.education.dto.reflection.ReflectionResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.service.ReflectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin Daily Reflections panel. */
@Tag(name = "Admin — Reflections", description = "Review daily reflections (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/reflections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminReflectionController {

    private final ReflectionService reflectionService;

    @Operation(summary = "List reflections (newest first) with filter and search")
    @GetMapping
    public ApiResponse<PageResponse<AdminReflectionSummary>> list(
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) ReflectionType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(
                reflectionService.adminList(cohortId, studentId, type, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a reflection")
    @GetMapping("/{id}")
    public ApiResponse<ReflectionResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(reflectionService.adminGet(id));
    }

    @Operation(summary = "Edit a reflection's typed answers")
    @PutMapping("/{id}/text")
    public ApiResponse<ReflectionResponse> updateText(@PathVariable UUID id,
                                                      @RequestBody List<ReflectionAnswerInput> answers) {
        return ApiResponse.success("Reflection updated", reflectionService.adminUpdateText(id, answers));
    }

    @Operation(summary = "Delete a reflection")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        reflectionService.adminDelete(id);
        return ApiResponse.success("Reflection deleted");
    }

    @Operation(summary = "Listen to a reflection's audio")
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable UUID id) {
        return FileDownloads.inline(reflectionService.adminDownloadAudio(id));
    }

    @Operation(summary = "Export a reflection as a text file")
    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> export(@PathVariable UUID id) {
        return FileDownloads.attachment(reflectionService.export(id));
    }
}
