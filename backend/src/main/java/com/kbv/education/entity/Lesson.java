package com.kbv.education.entity;

import com.kbv.education.entity.enums.LessonStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A lesson belonging to a {@link Cohort}. Only PUBLISHED lessons are visible to
 * students. Ordered within a cohort by {@code displayOrder}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "lessons")
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "lesson_number", nullable = false)
    private int lessonNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "lesson_date")
    private LocalDate lessonDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LessonStatus status = LessonStatus.DRAFT;

    @Column(name = "published_date")
    private Instant publishedDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public boolean isPublished() {
        return status == LessonStatus.PUBLISHED;
    }
}
