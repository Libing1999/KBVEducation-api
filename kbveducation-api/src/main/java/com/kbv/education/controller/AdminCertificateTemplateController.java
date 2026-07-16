package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.certificate.CertificateTemplateResponse;
import com.kbv.education.dto.certificate.UpsertCertificateTemplateRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.CertificateTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Certificate Templates", description = "Certificate layout templates (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/certificate-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCertificateTemplateController {

    private final CertificateTemplateService certificateTemplateService;

    @Operation(summary = "List all certificate templates")
    @GetMapping
    public ApiResponse<List<CertificateTemplateResponse>> list() {
        return ApiResponse.success(certificateTemplateService.list());
    }

    @Operation(summary = "Create a certificate template")
    @PostMapping
    public ApiResponse<CertificateTemplateResponse> create(@Valid @RequestBody UpsertCertificateTemplateRequest request) {
        return ApiResponse.success("Certificate template created", certificateTemplateService.create(request));
    }

    @Operation(summary = "Update a certificate template")
    @PutMapping("/{id}")
    public ApiResponse<CertificateTemplateResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpsertCertificateTemplateRequest request) {
        return ApiResponse.success("Certificate template updated", certificateTemplateService.update(id, request));
    }

    @Operation(summary = "Activate a template (deactivates any other template of the same type)")
    @PutMapping("/{id}/activate")
    public ApiResponse<CertificateTemplateResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success("Certificate template activated", certificateTemplateService.activate(id));
    }

    @Operation(summary = "Preview a certificate rendered from sample data, without persisting anything")
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable UUID id) {
        return FileDownloads.inline(certificateTemplateService.preview(id));
    }
}
