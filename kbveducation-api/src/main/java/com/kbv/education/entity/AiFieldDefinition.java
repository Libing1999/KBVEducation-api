package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiFieldType;
import com.kbv.education.entity.enums.AiJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A field the admin asked the AI to return, on top of the built-in ones
 * (memo 17.10.2026, 2.1). The AI's answer for it is saved in the result's
 * {@code extra} object under {@link #fieldKey}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_field_definitions")
public class AiFieldDefinition extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private AiJobType jobType;

    /** Machine name in the reply, e.g. "used_notes". Never changes once created. */
    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    /** Plain description of what the AI should look for; sent to the AI with the reply format. */
    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private AiFieldType fieldType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
