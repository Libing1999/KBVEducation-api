package com.kbv.education.service;

import com.kbv.education.dto.applog.ApplicationLogResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LogSeverity;

public interface ApplicationLogService {

    /** Never throws — logging an error must not itself break the error path. */
    void record(LogSeverity severity, String source, String message, String stackTraceExcerpt,
                String endpoint, String httpMethod, String ipAddress);

    PageResponse<ApplicationLogResponse> list(LogSeverity severity, int page, int size);
}
