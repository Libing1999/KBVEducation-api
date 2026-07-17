package com.kbv.education.security;

import com.kbv.education.entity.SystemSettings;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a raw password against the admin-configured policy in
 * {@code system_settings} (min length + character-class requirements).
 * Complements the static {@code @Size} annotations on the request DTOs,
 * which only enforce the length ceiling, not the dynamic policy.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private final SystemSettingsService systemSettingsService;

    public void validate(String password) {
        SystemSettings settings = systemSettingsService.getActiveEntity();
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < settings.getPasswordMinLength()) {
            violations.add("at least " + settings.getPasswordMinLength() + " characters");
        }
        if (password != null) {
            if (settings.isPasswordRequireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
                violations.add("an uppercase letter");
            }
            if (settings.isPasswordRequireLowercase() && password.chars().noneMatch(Character::isLowerCase)) {
                violations.add("a lowercase letter");
            }
            if (settings.isPasswordRequireDigit() && password.chars().noneMatch(Character::isDigit)) {
                violations.add("a digit");
            }
            if (settings.isPasswordRequireSpecial()
                    && password.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
                violations.add("a special character");
            }
        }

        if (!violations.isEmpty()) {
            throw new BusinessRuleException("Password must contain " + String.join(", ", violations));
        }
    }
}
