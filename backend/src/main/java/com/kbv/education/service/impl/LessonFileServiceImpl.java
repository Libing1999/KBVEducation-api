package com.kbv.education.service.impl;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.LessonFile;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.FileMapper;
import com.kbv.education.repository.LessonFileRepository;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.service.LessonFileService;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import com.kbv.education.utils.MimeTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonFileServiceImpl implements LessonFileService {

    private static final String SUBDIR = "lessons";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "mp3", "mp4");

    private final LessonRepository lessonRepository;
    private final LessonFileRepository lessonFileRepository;
    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FileResponse> listByLesson(UUID lessonId) {
        getLesson(lessonId);
        return lessonFileRepository.findByLesson_IdAndDeletedFalseOrderByUploadedDateAsc(lessonId).stream()
                .map(fileMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<FileResponse> upload(UUID lessonId, MultipartFile[] files) {
        Lesson lesson = getLesson(lessonId);
        if (files == null || files.length == 0) {
            throw new BadRequestException("No files provided");
        }

        return java.util.Arrays.stream(files).map(file -> {
            String original = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String ext = extensionOf(original);
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new BadRequestException(
                        "File type '" + ext + "' is not allowed. Allowed: " + ALLOWED_EXTENSIONS);
            }
            if (lessonFileRepository.existsByLesson_IdAndFileNameAndDeletedFalse(lessonId, original)) {
                throw new BadRequestException("A file named '" + original + "' already exists for this lesson");
            }

            StoredFile stored = fileStorageService.store(file, SUBDIR);
            LessonFile entity = new LessonFile();
            entity.setLesson(lesson);
            entity.setFileName(original);
            entity.setStoredName(stored.storedName());
            entity.setFileType(ext);
            entity.setFileSize(stored.size());
            return fileMapper.toResponse(lessonFileRepository.save(entity));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult download(UUID fileId) {
        LessonFile file = lessonFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        Resource resource = fileStorageService.loadAsResource(SUBDIR, file.getStoredName());
        return new FileDownloadResult(
                file.getFileName(),
                MimeTypes.forExtension(file.getFileType()),
                file.getFileSize() == null ? 0 : file.getFileSize(),
                resource);
    }

    @Override
    @Transactional
    public void delete(UUID fileId) {
        LessonFile file = lessonFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        file.setDeleted(true);
        lessonFileRepository.save(file);
        fileStorageService.delete(SUBDIR, file.getStoredName());
        log.info("Deleted lesson file {}", fileId);
    }

    private String extensionOf(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    private Lesson getLesson(UUID lessonId) {
        return lessonRepository.findByIdAndDeletedFalse(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
    }
}
