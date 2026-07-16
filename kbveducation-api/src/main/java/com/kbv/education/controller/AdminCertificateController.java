package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.dto.certificate.GenerateCertificateRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Certificates", description = "Issue and manage student certificates (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/certificates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "List every issued certificate")
    @GetMapping
    public ApiResponse<List<CertificateResponse>> list() {
        return ApiResponse.success(certificateService.listForAdmin());
    }

    @Operation(summary = "Generate a certificate for a student from the active template for the requested type")
    @PostMapping
    public ApiResponse<CertificateResponse> generate(@Valid @RequestBody GenerateCertificateRequest request) {
        return ApiResponse.success("Certificate generated",
                certificateService.generate(request.studentId(), request.certificateType()));
    }

    @Operation(summary = "Download any issued certificate")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        return FileDownloads.attachment(certificateService.downloadForAdmin(id));
    }
}
