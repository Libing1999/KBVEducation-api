package com.kbv.education.entity;

import com.kbv.education.entity.enums.LogSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unhandled exceptions, auth/authz failures, and upload/export errors,
 * captured from {@link com.kbv.education.exception.GlobalExceptionHandler}.
 * Severity is tiered (ERROR vs WARNING) so routine 400-level validation
 * misses don't bury real incidents in the admin viewer's default filter.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "application_logs")
public class ApplicationLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private LogSeverity severity;

    @Column(name = "source", nullable = false, length = 150)
    private String source;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "stack_trace_excerpt", columnDefinition = "text")
    private String stackTraceExcerpt;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;
}
