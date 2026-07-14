package com.kbv.education.entity;

import com.kbv.education.entity.enums.RoleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A system role. Backed by the {@code roles} table and seeded via Flyway.
 * A user is associated with exactly one role in Phase 1.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 50, unique = true)
    private RoleType name;

    @Column(name = "description", length = 255)
    private String description;
}
