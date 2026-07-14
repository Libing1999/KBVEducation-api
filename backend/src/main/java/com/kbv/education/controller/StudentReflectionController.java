package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.reflection.ReflectionResponse;
import com.kbv.education.dto.reflection.TodayReflectionResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.ReflectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Student daily reflections. One per day, editable until midnight. */
@Tag(name = "Student — Reflections", description = "Daily reflections (STUDENT only)")
@RestController
@RequestMapping("/api/student/reflections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentReflectionController {

    private final ReflectionService reflectionService;

    @Operation(summary = "Get today's questions and my reflection (if any)")
    @GetMapping("/today")
    public ApiResponse<TodayReflectionResponse> today(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reflectionService.getToday(principal.getId()));
    }

    @Operation(summary = "List my reflections (newest first)")
    @GetMapping
    public ApiResponse<List<ReflectionResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reflectionService.getMine(principal.getId()));
    }

    @Operation(summary = "Get one of my reflections")
    @GetMapping("/{id}")
    public ApiResponse<ReflectionResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID id) {
        return ApiResponse.success(reflectionService.getMineById(principal.getId(), id));
    }

    @Operation(summary = "Submit today's reflection (typed answers and/or audio; once per day)")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ReflectionResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "answers", required = false) String answers,
            @RequestParam(value = "audio", required = false) MultipartFile audio) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reflection submitted",
                        reflectionService.submit(principal.getId(), answers, audio)));
    }

    @Operation(summary = "Edit today's reflection (until midnight)")
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ApiResponse<ReflectionResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(value = "answers", required = false) String answers,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "removeAudio", required = false, defaultValue = "false") boolean removeAudio) {
        return ApiResponse.success("Reflection updated",
                reflectionService.update(principal.getId(), id, answers, audio, removeAudio));
    }

    @Operation(summary = "Play my reflection audio")
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> audio(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID id) {
        return FileDownloads.inline(reflectionService.downloadMyAudio(principal.getId(), id));
    }
}
