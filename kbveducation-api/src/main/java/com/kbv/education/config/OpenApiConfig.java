package com.kbv.education.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration with a bearer-token security scheme so the
 * "Authorize" button in Swagger UI accepts the JWT access token.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI kbvOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KBV Education API")
                        .description("Course Companion Platform - Auth, admin/student/parent management, "
                                + "lessons/homework/quizzes/reflections/practice, scoring & tiers, leaderboard "
                                + "& analytics, certificates, data export, audit trail, system settings, backups, "
                                + "and production-readiness features (security hardening, error monitoring, "
                                + "caching, global search)")
                        .version("v0.1.0")
                        .contact(new Contact().name("KBV Education")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
