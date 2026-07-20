package com.kbv.education.entity;

import com.kbv.education.entity.enums.EmailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One row per outbound email attempt — queued → sent/failed (or skipped when SMTP isn't configured). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "email_logs")
public class EmailLog extends BaseEntity {

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailStatus status = EmailStatus.QUEUED;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "student_id")
    private UUID studentId;
}
