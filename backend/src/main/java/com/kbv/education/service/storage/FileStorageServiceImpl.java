package com.kbv.education.service.storage;

import com.kbv.education.config.StorageProperties;
import com.kbv.education.exception.ApiException;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Filesystem-backed {@link FileStorageService}. Generates unique file names,
 * guards against path traversal, and keeps the storage root outside the project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final StorageProperties properties;
    private Path root;

    @PostConstruct
    void init() {
        String base = properties.getBasePath();
        if (!StringUtils.hasText(base)) {
            base = Paths.get(System.getProperty("user.home"), "kbv-education-storage").toString();
        }
        this.root = Paths.get(base).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("File storage root: {}", root);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not initialise storage directory");
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        Path dir = resolveDir(subDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create storage sub-directory");
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        String storedName = UUID.randomUUID() + (ext != null ? "." + ext.toLowerCase(Locale.ROOT) : "");

        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw new BadRequestException("Invalid file path");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to store file");
        }

        return new StoredFile(storedName, original, file.getContentType(), file.getSize());
    }

    @Override
    public Resource loadAsResource(String subDir, String storedName) {
        Path file = resolveDir(subDir).resolve(storedName).normalize();
        if (!file.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("File not found");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found");
        }
    }

    @Override
    public void delete(String subDir, String storedName) {
        try {
            Path file = resolveDir(subDir).resolve(storedName).normalize();
            if (file.startsWith(root)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete stored file {}/{}: {}", subDir, storedName, e.getMessage());
        }
    }

    private Path resolveDir(String subDir) {
        String safe = StringUtils.hasText(subDir) ? subDir.replaceAll("[^a-zA-Z0-9_-]", "") : "misc";
        return root.resolve(safe).normalize();
    }
}
