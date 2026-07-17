package com.kbv.education.service.impl;

import com.kbv.education.entity.SystemSettings;
import com.kbv.education.entity.User;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the account-lockout counter (Phase 5 Step 7). This logic was
 * originally inline in AuthServiceImpl.login() and had a real bug: the
 * counter increment was silently rolled back because it ran inside the same
 * @Transactional method that then threw an exception to report the auth
 * failure. It was extracted into this REQUIRES_NEW-transactional service
 * specifically to survive that rollback - these tests pin the counting/
 * locking behavior itself (the transactional-boundary fix can only be
 * verified with a real transaction manager, which the AuthController
 * integration test below covers).
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    private User user;
    private SystemSettings settings;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("student@kbv.edu");
        user.setFailedLoginAttempts(0);

        settings = new SystemSettings();
        settings.setMaxLoginAttempts(5);
    }

    @Test
    void incrementsCounterWithoutLockingBelowThreshold() {
        when(userRepository.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(user));
        when(systemSettingsService.getActiveEntity()).thenReturn(settings);

        loginAttemptService.recordFailedAttempt(user.getId());

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void locksAccountOnceThresholdIsReached() {
        user.setFailedLoginAttempts(4);
        when(userRepository.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(user));
        when(systemSettingsService.getActiveEntity()).thenReturn(settings);

        loginAttemptService.recordFailedAttempt(user.getId());

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(user.isLocked()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void isNoOpForAnUnknownUserId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedFalse(unknownId)).thenReturn(Optional.empty());

        loginAttemptService.recordFailedAttempt(unknownId);

        verify(userRepository, never()).save(any());
    }
}
