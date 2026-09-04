package com.kbv.education.controller;

import com.kbv.education.dto.message.CoachMessageResponse;
import com.kbv.education.dto.message.SendMessageRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.CoachMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff compose/send for the one manual messaging channel (Live Action /
 * Messages from Bhavya). SUPER_ADMIN only — students and parents only ever
 * read via their own role-scoped endpoints.
 */
@Tag(name = "Admin — Messages", description = "Compose/send coach messages (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMessageController {

    private final CoachMessageService coachMessageService;

    @Operation(summary = "Send a message to a single student, or collectively to a cohort")
    @PostMapping
    public ApiResponse<CoachMessageResponse> send(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success("Message sent", coachMessageService.send(principal.getId(), request));
    }

    @Operation(summary = "List recently sent messages, newest first")
    @GetMapping
    public ApiResponse<PageResponse<CoachMessageResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(coachMessageService.adminList(page, size));
    }
}
