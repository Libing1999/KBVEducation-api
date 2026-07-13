package com.kbv.education.utils;

import java.util.Locale;
import java.util.Map;

/** Minimal extension → MIME-type resolution for download responses. */
public final class MimeTypes {

    private MimeTypes() {
    }

    private static final String DEFAULT = "application/octet-stream";

    private static final Map<String, String> BY_EXT = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webm", "audio/webm"),
            Map.entry("m4a", "audio/mp4"));

    public static String forExtension(String ext) {
        if (ext == null) {
            return DEFAULT;
        }
        return BY_EXT.getOrDefault(ext.toLowerCase(Locale.ROOT), DEFAULT);
    }
}
