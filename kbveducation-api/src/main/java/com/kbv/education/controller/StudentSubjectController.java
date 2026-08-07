package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.subject.SubjectResponse;
import com.kbv.education.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Active subjects for the student practice log Subject dropdown. */
@Tag(name = "Student — Subjects", description = "Active subjects for the practice log (STUDENT only)")
@RestController
@RequestMapping("/api/student/subjects")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentSubjectController {

    private final SubjectService subjectService;

    @Operation(summary = "List active subjects, ordered for display")
    @GetMapping
    public ApiResponse<List<SubjectResponse>> list() {
        return ApiResponse.success(subjectService.listEnabled());
    }
}
