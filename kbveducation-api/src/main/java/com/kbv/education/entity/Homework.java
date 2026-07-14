package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Admin-configured homework for a lesson (at most one active per lesson).
 * Students submit against this configuration via {@link HomeworkSubmission}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "homework")
public class Homework extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "due_date")
    private Instant dueDate;

    /** Comma-separated list of allowed extensions, e.g. "pdf,docx,mp3". */
    @Column(name = "allowed_file_types", length = 255)
    private String allowedFileTypes;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;
}
