package com.kbv.education.config;

import com.kbv.education.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the "current auditor" (the acting user's id) to Spring Data JPA so
 * that {@code createdBy} / {@code updatedBy} are populated automatically.
 *
 * <p>Resolution is delegated to {@link SecurityUtils}, which reads the id from
 * the security context. For unauthenticated actions (e.g. the seeded super
 * admin, system migrations) the auditor is empty and the columns stay null.</p>
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityUtils.getCurrentUserId());
    }
}
