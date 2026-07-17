package com.kbv.education.service.impl;

import com.kbv.education.entity.User;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.LoginAttemptService;
import com.kbv.education.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** No separate configurable lockout-duration field exists in system_settings (only the
     *  attempt threshold) - a fixed 15-minute window is a reasonable, documented default. */
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
        if (user == null) {
            return;
        }
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        int maxAttempts = systemSettingsService.getActiveEntity().getMaxLoginAttempts();
        if (attempts >= maxAttempts) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            log.warn("User {} locked after {} failed login attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }
}
