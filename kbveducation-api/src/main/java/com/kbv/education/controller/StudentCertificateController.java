package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Student — Certificates", description = "A student's own certificates")
@RestController
@RequestMapping("/api/student/certificates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentCertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "List my certificates")
    @GetMapping
    public ApiResponse<List<CertificateResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(certificateService.listForStudent(principal.getId()));
    }

    @Operation(summary = "Download one of my certificates")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return FileDownloads.attachment(certificateService.downloadForStudent(principal.getId(), id));
    }
}
