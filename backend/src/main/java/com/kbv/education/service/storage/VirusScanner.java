package com.kbv.education.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Extension point for scanning uploaded files. Phase 3 ships
 * {@link NoopVirusScanner} (a pass-through placeholder). A real scanner (e.g.
 * ClamAV) can replace this bean without touching upload call sites.
 */
public interface VirusScanner {

    /**
     * Scan an uploaded file. Implementations should throw if the file is
     * rejected; the default placeholder always passes.
     */
    void scan(MultipartFile file);
}
