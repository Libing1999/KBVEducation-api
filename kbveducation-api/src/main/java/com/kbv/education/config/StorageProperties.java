package com.kbv.education.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.storage.*}. Files are stored under {@code basePath}, which
 * should point outside the project/deployment directory.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Root directory for uploaded files (configurable, outside the project). */
    private String basePath;

    /** Global upper bound on a single uploaded file, in megabytes. */
    private int maxFileSizeMb = 25;
}
