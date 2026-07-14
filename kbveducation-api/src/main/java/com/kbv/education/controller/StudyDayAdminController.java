package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.studyday.VoidStudyDayRequest;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.StudyDayAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Study Days", description = "Void a student's study day so it's excluded from scoring (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/study-days")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StudyDayAdminController {

    private final StudyDayAdminService studyDayAdminService;

    @Operation(summary = "Void a study day (excluded from Practice %/Reflection % calculations)")
    @PatchMapping("/{id}/void")
    public ApiResponse<Void> voidDay(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody VoidStudyDayRequest request) {
        studyDayAdminService.voidDay(id, request.reason(), principal.getId());
        return ApiResponse.success("Study day voided");
    }
}
