package com.kbv.education.service.ai;

import com.kbv.education.dto.ai.ReferencePackVersionResponse;
import com.kbv.education.dto.ai.ReferencePackVersionResponse.ReferencePackFileResponse;
import com.kbv.education.entity.AiReferencePackFile;
import com.kbv.education.entity.AiReferencePackVersion;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.AiReferencePackFileRepository;
import com.kbv.education.repository.AiReferencePackVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The reference pack (memo 12.10.2026, section 5): the admin's Markdown checklists,
 * guidelines, prompts and topic lists, sent whole with every AI request.
 *
 * <p>Every upload, edit or delete creates a new version and makes it active; older versions
 * are kept unchanged so the admin can go back to any of them. Changes apply from the next
 * run, and each AI result records the version it used.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferencePackService {

    private static final Set<String> ALLOWED = Set.of("md", "markdown", "txt");
    private static final int MAX_FILE_BYTES = 200_000;

    private final AiReferencePackVersionRepository versionRepository;
    private final AiReferencePackFileRepository fileRepository;

    /** The active pack as sent to the AI, or empty text when no pack has been uploaded yet. */
    public record ActivePack(UUID versionId, String text) {
    }

    @Transactional(readOnly = true)
    public ActivePack activePack() {
        return versionRepository.findByActiveTrue()
                .map(v -> new ActivePack(v.getId(), render(v)))
                .orElse(new ActivePack(null, ""));
    }

    /** The pack a job was created with, so a retry or backup sends exactly the same text. */
    @Transactional(readOnly = true)
    public ActivePack pack(UUID versionId) {
        if (versionId == null) {
            return new ActivePack(null, "");
        }
        return versionRepository.findById(versionId)
                .map(v -> new ActivePack(v.getId(), render(v)))
                .orElse(new ActivePack(null, ""));
    }

    @Transactional(readOnly = true)
    public List<ReferencePackVersionResponse> listVersions() {
        return versionRepository.findAllByOrderByVersionNoDesc().stream()
                .map(v -> toResponse(v, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReferencePackVersionResponse getVersion(UUID id) {
        return toResponse(load(id), true);
    }

    @Transactional(readOnly = true)
    public ReferencePackVersionResponse getActive() {
        return versionRepository.findByActiveTrue().map(v -> toResponse(v, true)).orElse(null);
    }

    /** Upload one or more Markdown files; files with the same name replace the old ones. */
    @Transactional
    public ReferencePackVersionResponse upload(MultipartFile[] uploads, String note) {
        if (uploads == null || uploads.length == 0) {
            throw new BadRequestException("Choose at least one file to upload");
        }
        Map<String, String> files = activeFiles();
        for (MultipartFile upload : uploads) {
            if (upload == null || upload.isEmpty()) {
                continue;
            }
            String name = cleanName(upload.getOriginalFilename());
            if (upload.getSize() > MAX_FILE_BYTES) {
                throw new BadRequestException("'" + name + "' is larger than " + MAX_FILE_BYTES / 1000 + " KB");
            }
            try {
                putReplacingSameName(files, name, new String(upload.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new BadRequestException("Could not read '" + name + "'");
            }
        }
        return toResponse(createVersion(files, defaultNote(note, "Uploaded files")), true);
    }

    /** Create or edit one file. */
    @Transactional
    public ReferencePackVersionResponse saveFile(String fileName, String content, String note) {
        String name = cleanName(fileName);
        Map<String, String> files = activeFiles();
        putReplacingSameName(files, name, content);
        return toResponse(createVersion(files, defaultNote(note, "Edited " + name)), true);
    }

    @Transactional
    public ReferencePackVersionResponse deleteFile(String fileName, String note) {
        Map<String, String> files = activeFiles();
        String key = files.keySet().stream()
                .filter(n -> n.equalsIgnoreCase(fileName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No file named '" + fileName + "' in the active pack"));
        files.remove(key);
        return toResponse(createVersion(files, defaultNote(note, "Deleted " + key)), true);
    }

    /** Go back to an older version (or forward again): it becomes the one sent from the next run. */
    @Transactional
    public ReferencePackVersionResponse activate(UUID versionId) {
        AiReferencePackVersion target = load(versionId);
        versionRepository.findByActiveTrue().ifPresent(current -> {
            current.setActive(false);
            versionRepository.saveAndFlush(current);
        });
        target.setActive(true);
        versionRepository.save(target);
        log.info("Reference pack version {} is now active", target.getVersionNo());
        return toResponse(target, true);
    }

    // --- helpers -----------------------------------------------------------

    private AiReferencePackVersion createVersion(Map<String, String> files, String note) {
        versionRepository.findByActiveTrue().ifPresent(current -> {
            current.setActive(false);
            versionRepository.saveAndFlush(current);
        });
        AiReferencePackVersion version = new AiReferencePackVersion();
        version.setVersionNo(versionRepository.maxVersionNo() + 1);
        version.setNote(note);
        version.setActive(true);
        AiReferencePackVersion saved = versionRepository.save(version);
        files.forEach((name, content) -> {
            AiReferencePackFile file = new AiReferencePackFile();
            file.setPackVersion(saved);
            file.setFileName(name);
            file.setContent(content);
            fileRepository.save(file);
        });
        log.info("Created reference pack version {} ({} files): {}", saved.getVersionNo(), files.size(), note);
        return saved;
    }

    private Map<String, String> activeFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        versionRepository.findByActiveTrue().ifPresent(v ->
                fileRepository.findByPackVersion_IdOrderByFileNameAsc(v.getId())
                        .forEach(f -> files.put(f.getFileName(), f.getContent())));
        return files;
    }

    private void putReplacingSameName(Map<String, String> files, String name, String content) {
        files.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
        files.put(name, content);
    }

    /**
     * The text sent to the AI: files in name order, each under its own heading. Kept free of
     * dates and ids so it stays byte-identical between requests and can be cached.
     */
    private String render(AiReferencePackVersion version) {
        StringBuilder sb = new StringBuilder("# Reference pack\n");
        for (AiReferencePackFile file : fileRepository.findByPackVersion_IdOrderByFileNameAsc(version.getId())) {
            sb.append("\n\n## File: ").append(file.getFileName()).append("\n\n").append(file.getContent().strip());
        }
        return sb.toString();
    }

    private String cleanName(String raw) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(raw == null ? "" : raw.strip()));
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("File name is required");
        }
        String ext = StringUtils.getFilenameExtension(name);
        if (ext == null) {
            name = name + ".md";
        } else if (!ALLOWED.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Only Markdown files (.md) can be added to the reference pack");
        }
        if (name.length() > 200) {
            throw new BadRequestException("File name is too long");
        }
        return name;
    }

    private String defaultNote(String note, String fallback) {
        return StringUtils.hasText(note) ? note.strip() : fallback;
    }

    private AiReferencePackVersion load(UUID id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Reference pack version", id));
    }

    private ReferencePackVersionResponse toResponse(AiReferencePackVersion v, boolean withFiles) {
        List<ReferencePackFileResponse> files = withFiles
                ? fileRepository.findByPackVersion_IdOrderByFileNameAsc(v.getId()).stream()
                        .map(f -> new ReferencePackFileResponse(f.getId(), f.getFileName(), f.getContent(),
                                f.getContent().getBytes(StandardCharsets.UTF_8).length))
                        .toList()
                : null;
        long count = withFiles ? files.size() : fileRepository.countByPackVersion_Id(v.getId());
        return new ReferencePackVersionResponse(v.getId(), v.getVersionNo(), v.getNote(), v.isActive(), count,
                v.getCreatedAt(), files);
    }
}
