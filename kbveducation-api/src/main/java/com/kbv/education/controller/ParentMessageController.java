package com.kbv.education.controller;

import com.kbv.education.dto.message.ParentMessageResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.CoachMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Messages from Bhavya" — read-only for the parent's own linked student. PARENT only. */
@Tag(name = "Parent — Messages", description = "Coach messages for my linked student (PARENT only)")
@RestController
@RequestMapping("/api/parent/messages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
public class ParentMessageController {

    private final CoachMessageService coachMessageService;

    @Operation(summary = "Get my linked student's coach messages (individual + their cohort's collective), newest first")
    @GetMapping
    public ApiResponse<List<ParentMessageResponse>> myMessages(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(coachMessageService.listForParent(principal.getId()));
    }
}
