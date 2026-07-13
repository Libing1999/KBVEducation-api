package com.kbv.education.dto.file;

import org.springframework.core.io.Resource;

/** A resolved file ready to be streamed to the client by a controller. */
public record FileDownloadResult(
        String fileName,
        String contentType,
        long size,
        Resource resource
) {
}
