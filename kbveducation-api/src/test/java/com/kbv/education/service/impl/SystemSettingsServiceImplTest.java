package com.kbv.education.service.impl;

import com.kbv.education.dto.settings.SystemSettingsResponse;
import com.kbv.education.dto.settings.UpdateSystemSettingsRequest;
import com.kbv.education.entity.SystemSettings;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.SystemSettingsMapper;
import com.kbv.education.repository.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the single-active-row upsert pattern system_settings shares with
 * score_config (Phase 4) - there's exactly one active row, ever, and
 * update() must mutate that same row rather than create a new one.
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceImplTest {

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private SystemSettingsMapper systemSettingsMapper;

    @InjectMocks
    private SystemSettingsServiceImpl systemSettingsService;

    private SystemSettings existingRow;

    @BeforeEach
    void setUp() {
        existingRow = new SystemSettings();
        existingRow.setId(UUID.randomUUID());
        existingRow.setMaxLoginAttempts(5);
    }

    @Test
    void updateMutatesTheSingleActiveRowInPlace() {
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest(
                "KBV Education", "KBV Institute", null, "#1B3A6B", "#F2F6FA", "#C4972A",
                "UTC", "yyyy-MM-dd", 25, "pdf,docx", 7, 10,
                true, true, true, true, 10080, false, true, true);

        when(systemSettingsRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Optional.of(existingRow));
        when(systemSettingsRepository.save(any(SystemSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(systemSettingsMapper.toResponse(any(SystemSettings.class)))
                .thenReturn(new SystemSettingsResponse("KBV Education", "KBV Institute", null,
                        "#1B3A6B", "#F2F6FA", "#C4972A", "UTC", "yyyy-MM-dd", 25, "pdf,docx",
                        7, 10, true, true, true, true, 10080, false, true, true));

        SystemSettingsResponse response = systemSettingsService.update(request);

        ArgumentCaptor<SystemSettings> saved = ArgumentCaptor.forClass(SystemSettings.class);
        verify(systemSettingsRepository).save(saved.capture());

        // Same row (same id), not a newly-constructed one - the "single active row" contract.
        assertThat(saved.getValue().getId()).isEqualTo(existingRow.getId());
        assertThat(saved.getValue().getMaxLoginAttempts()).isEqualTo(7);
        assertThat(saved.getValue().getInstitutionName()).isEqualTo("KBV Institute");
        assertThat(response.maxLoginAttempts()).isEqualTo(7);
    }

    @Test
    void throwsWhenNoActiveRowExists() {
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest(
                "KBV Education", "KBV Institute", null, "#1B3A6B", "#F2F6FA", "#C4972A",
                "UTC", "yyyy-MM-dd", 25, "pdf,docx", 7, 10,
                true, true, true, true, 10080, false, true, true);
        when(systemSettingsRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemSettingsService.update(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
