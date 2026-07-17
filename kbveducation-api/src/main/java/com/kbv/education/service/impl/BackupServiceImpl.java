package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.config.StorageProperties;
import com.kbv.education.dto.backup.BackupHistoryResponse;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.BackupHistory;
import com.kbv.education.entity.enums.BackupStatus;
import com.kbv.education.exception.ApiException;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.BackupHistoryRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs {@code pg_dump} via {@link ProcessBuilder} — a hand-rolled JDBC dumper
 * would be strictly worse for the one feature whose entire job is dump
 * correctness. Requires {@code pg_dump} (part of the postgresql-client
 * package) on the host PATH; the backend's Docker image (Step 8) installs it
 * explicitly since it won't be present by default in a JRE-only base image.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:postgresql://([^:/]+):(\\d+)/([^?]+)");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long TIMEOUT_MINUTES = 5;

    private final BackupHistoryRepository backupHistoryRepository;
    private final UserRepository userRepository;
    private final StorageProperties storageProperties;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // Deliberately NOT @Transactional: pg_dump can run for minutes, and holding a
    // connection-pool slot for that whole span risks exhausting the pool. Each
    // backupHistoryRepository.save() below already commits as its own short
    // transaction via Spring Data's repository proxy - no wrapping transaction needed.
    @Override
    @Audited(action = "BACKUP_CREATED", entityType = "BACKUP")
    public BackupHistoryResponse create() {
        Matcher matcher = JDBC_URL_PATTERN.matcher(datasourceUrl);
        if (!matcher.matches()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not parse database URL for backup");
        }
        String host = matcher.group(1);
        String port = matcher.group(2);
        String dbName = matcher.group(3);

        BackupHistory history = new BackupHistory();
        history.setStatus(BackupStatus.IN_PROGRESS);
        history = backupHistoryRepository.save(history);

        Path target = backupsDir().resolve(
                "backup-" + FILE_TIMESTAMP.format(Instant.now().atZone(java.time.ZoneOffset.UTC)) + "-"
                        + history.getId() + ".dump");

        ProcessBuilder builder = new ProcessBuilder(
                "pg_dump", "-h", host, "-p", port, "-U", dbUsername, "-d", dbName,
                "-F", "c", "-f", target.toString());
        builder.environment().put("PGPASSWORD", dbPassword);
        builder.redirectErrorStream(false);

        String stderr;
        int exitCode;
        try {
            Process process = builder.start();

            // Drain stderr on a background thread *while* waiting, not after - reading it
            // synchronously before waitFor() would block until the pipe hits EOF (i.e. until
            // the process exits anyway), making the timeout below never actually trigger for
            // a hung pg_dump. This way waitFor's timeout is the real bound.
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            Thread stderrReader = new Thread(() -> {
                try (InputStream in = process.getErrorStream()) {
                    in.transferTo(stderrBuffer);
                } catch (IOException ignored) {
                    // Process was killed mid-read after a timeout - nothing more to capture.
                }
            });
            stderrReader.setDaemon(true);
            stderrReader.start();

            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                stderrReader.join(2000);
                history.setStatus(BackupStatus.FAILED);
                history.setErrorMessage("Backup timed out after " + TIMEOUT_MINUTES + " minutes");
                return toResponse(backupHistoryRepository.save(history));
            }
            stderrReader.join(2000);
            stderr = stderrBuffer.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            exitCode = process.exitValue();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            history.setStatus(BackupStatus.FAILED);
            history.setErrorMessage("Failed to run pg_dump: " + e.getMessage());
            return toResponse(backupHistoryRepository.save(history));
        }

        if (exitCode != 0) {
            history.setStatus(BackupStatus.FAILED);
            history.setErrorMessage(StringUtils.hasText(stderr) ? stderr : "pg_dump exited with code " + exitCode);
            log.warn("Backup {} failed: {}", history.getId(), history.getErrorMessage());
            return toResponse(backupHistoryRepository.save(history));
        }

        try {
            history.setFileSizeBytes(Files.size(target));
        } catch (IOException e) {
            history.setFileSizeBytes(null);
        }
        history.setFilePath(target.toString());
        history.setStatus(BackupStatus.COMPLETED);
        BackupHistory saved = backupHistoryRepository.save(history);
        log.info("Backup {} completed ({} bytes)", saved.getId(), saved.getFileSizeBytes());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BackupHistoryResponse> list() {
        return backupHistoryRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult download(UUID id) {
        BackupHistory history = getBackup(id);
        if (history.getFilePath() == null) {
            throw new ResourceNotFoundException("This backup has no file to download");
        }
        Resource resource;
        try {
            resource = new UrlResource(Paths.get(history.getFilePath()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Backup file not found on disk");
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Backup file not found on disk");
        }
        String fileName = Paths.get(history.getFilePath()).getFileName().toString();
        return new FileDownloadResult(fileName, "application/octet-stream",
                history.getFileSizeBytes() != null ? history.getFileSizeBytes() : 0, resource);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        BackupHistory history = getBackup(id);
        if (history.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(history.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete backup file {}: {}", history.getFilePath(), e.getMessage());
            }
        }
        history.setDeleted(true);
        backupHistoryRepository.save(history);
    }

    private BackupHistory getBackup(UUID id) {
        return backupHistoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Backup", id));
    }

    private Path backupsDir() {
        String base = storageProperties.getBasePath();
        if (!StringUtils.hasText(base)) {
            base = Paths.get(System.getProperty("user.home"), "kbv-education-storage").toString();
        }
        Path dir = Paths.get(base).toAbsolutePath().normalize().resolve("backups");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create backups directory");
        }
        return dir;
    }

    private BackupHistoryResponse toResponse(BackupHistory history) {
        String createdByName = history.getCreatedBy() != null
                ? userRepository.findByIdAndDeletedFalse(history.getCreatedBy()).map(u -> u.getFullName()).orElse("Unknown")
                : "System";
        return new BackupHistoryResponse(
                history.getId(), history.getFileSizeBytes(), history.getStatus(),
                history.getErrorMessage(), createdByName, history.getCreatedAt());
    }
}
