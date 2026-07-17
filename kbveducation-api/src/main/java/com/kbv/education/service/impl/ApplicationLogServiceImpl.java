package com.kbv.education.service.impl;

import com.kbv.education.dto.applog.ApplicationLogResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.ApplicationLog;
import com.kbv.education.entity.enums.LogSeverity;
import com.kbv.education.repository.ApplicationLogRepository;
import com.kbv.education.service.ApplicationLogService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationLogServiceImpl implements ApplicationLogService {

    private static final List<String> SORTABLE = List.of("createdAt");
    private static final int MAX_STACK_TRACE_LENGTH = 4000;

    private final ApplicationLogRepository applicationLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LogSeverity severity, String source, String message, String stackTraceExcerpt,
                        String endpoint, String httpMethod, String ipAddress) {
        try {
            ApplicationLog entry = new ApplicationLog();
            entry.setSeverity(severity);
            entry.setSource(source);
            entry.setMessage(message);
            entry.setStackTraceExcerpt(truncate(stackTraceExcerpt));
            entry.setEndpoint(endpoint);
            entry.setHttpMethod(httpMethod);
            entry.setIpAddress(ipAddress);
            applicationLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to record application log source={} severity={}", source, severity, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApplicationLogResponse> list(LogSeverity severity, int page, int size) {
        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", SORTABLE);
        Page<ApplicationLog> result = severity != null
                ? applicationLogRepository.findBySeverityAndDeletedFalseOrderByCreatedAtDesc(severity, pageable)
                : applicationLogRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);
        return PageResponse.from(result, this::toResponse);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_STACK_TRACE_LENGTH ? value : value.substring(0, MAX_STACK_TRACE_LENGTH);
    }

    private ApplicationLogResponse toResponse(ApplicationLog entry) {
        return new ApplicationLogResponse(
                entry.getId(), entry.getSeverity(), entry.getSource(), entry.getMessage(),
                entry.getEndpoint(), entry.getHttpMethod(), entry.getIpAddress(), entry.getCreatedAt());
    }
}
