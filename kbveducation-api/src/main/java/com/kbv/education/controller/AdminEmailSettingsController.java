package com.kbv.education.controller;

import com.kbv.education.dto.email.EmailSettingsResponse;
import com.kbv.education.dto.email.SendTestEmailRequest;
import com.kbv.education.dto.email.UpdateEmailSettingsRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.service.email.EmailNotificationService;
import com.kbv.education.service.email.EmailSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Email Settings", description = "SMTP configuration and test send (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/settings/email")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminEmailSettingsController {

    private final EmailSettingsService emailSettingsService;
    private final EmailNotificationService emailNotificationService;

    @Operation(summary = "Get the SMTP configuration (password never returned, only whether one is set)")
    @GetMapping
    public ApiResponse<EmailSettingsResponse> get() {
        return ApiResponse.success(emailSettingsService.get());
    }

    @Operation(summary = "Update the SMTP configuration (blank password keeps the stored one)")
    @PutMapping
    public ApiResponse<EmailSettingsResponse> update(@Valid @RequestBody UpdateEmailSettingsRequest request) {
        return ApiResponse.success("Email settings updated", emailSettingsService.update(request));
    }

    @Operation(summary = "Send a test email with the current configuration (synchronous, reports failure directly)")
    @PostMapping("/test")
    public ApiResponse<Void> sendTest(@Valid @RequestBody SendTestEmailRequest request) {
        try {
            emailNotificationService.sendTestEmail(request.recipient());
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessRuleException("Test email failed: " + reason);
        }
        return ApiResponse.success("Test email sent to " + request.recipient());
    }
}
