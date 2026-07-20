package com.kbv.education.service.email;

import com.kbv.education.dto.email.EmailSettingsResponse;
import com.kbv.education.dto.email.UpdateEmailSettingsRequest;

public interface EmailSettingsService {

    EmailSettingsResponse get();

    EmailSettingsResponse update(UpdateEmailSettingsRequest request);

    /** DB settings merged over the spring.mail.* environment fallbacks, password decrypted. */
    SmtpConfig effectiveConfig();
}
