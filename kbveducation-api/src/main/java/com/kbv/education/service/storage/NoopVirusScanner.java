package com.kbv.education.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Placeholder virus scanner: logs and passes everything. Swap this bean for a
 * real scanner (e.g. ClamAV) to enforce scanning without changing callers.
 */
@Slf4j
@Service
public class NoopVirusScanner implements VirusScanner {

    @Override
    public void scan(MultipartFile file) {
        log.debug("Virus scan placeholder — passing file '{}' ({} bytes)",
                file == null ? "?" : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
    }
}
