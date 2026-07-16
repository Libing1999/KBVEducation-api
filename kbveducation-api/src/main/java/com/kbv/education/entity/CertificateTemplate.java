package com.kbv.education.entity;

import com.kbv.education.entity.enums.CertificateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-editable certificate layout: a body-text template with
 * {@code {{placeholder}}} tokens plus branding fields. At most one template
 * per {@link CertificateType} may be {@code active} at a time — that's the
 * template {@link CertificateService} resolves when an admin generates a
 * certificate of that type.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "certificate_templates")
public class CertificateTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 20)
    private CertificateType certificateType;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "primary_color_hex", nullable = false, length = 7)
    private String primaryColorHex = "#1B3A6B";

    @Column(name = "institution_name_override", length = 150)
    private String institutionNameOverride;

    @Column(name = "logo_path_override", columnDefinition = "text")
    private String logoPathOverride;

    @Column(name = "active", nullable = false)
    private boolean active = false;
}
