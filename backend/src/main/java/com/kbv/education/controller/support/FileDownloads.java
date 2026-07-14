package com.kbv.education.controller.support;

import com.kbv.education.dto.file.FileDownloadResult;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds a streaming download response with a safe Content-Disposition. */
public final class FileDownloads {

    private FileDownloads() {
    }

    public static ResponseEntity<Resource> attachment(FileDownloadResult download) {
        return build(download, "attachment");
    }

    /** Inline disposition — lets the browser render/play the file (e.g. audio). */
    public static ResponseEntity<Resource> inline(FileDownloadResult download) {
        return build(download, "inline");
    }

    private static ResponseEntity<Resource> build(FileDownloadResult download, String disposition) {
        String encoded = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + download.fileName() + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.contentType()));
        if (download.size() > 0) {
            builder.contentLength(download.size());
        }
        return builder.body(download.resource());
    }
}
