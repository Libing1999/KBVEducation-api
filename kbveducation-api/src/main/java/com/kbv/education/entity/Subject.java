package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An admin-configured subject offered in the practice log Subject dropdown. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "subjects")
public class Subject extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
