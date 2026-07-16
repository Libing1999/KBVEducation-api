package com.kbv.education.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over file persistence. Stores files under a configurable base path
 * outside the project directory and never exposes real paths to callers.
 */
public interface FileStorageService {

    /**
     * Persist a file into the given sub-directory, generating a unique on-disk name.
     *
     * @param file   the uploaded file
     * @param subDir logical bucket (e.g. {@code lessons}, {@code homework})
     * @return metadata describing the stored file
     */
    StoredFile store(MultipartFile file, String subDir);

    /**
     * Persist server-generated content (e.g. a rendered PDF) into the given
     * sub-directory, generating a unique on-disk name.
     */
    StoredFile store(byte[] content, String originalName, String contentType, String subDir);

    /** Load a previously stored file as a downloadable resource. */
    Resource loadAsResource(String subDir, String storedName);

    /** Delete a stored file (best-effort). */
    void delete(String subDir, String storedName);
}
