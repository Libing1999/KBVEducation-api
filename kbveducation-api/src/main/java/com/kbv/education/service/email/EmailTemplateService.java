package com.kbv.education.service.email;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.SystemSettings;
import com.kbv.education.entity.User;
import com.kbv.education.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renders branded, responsive HTML emails. Email clients need table layouts
 * and inline styles (no external CSS), so everything is inlined; the shared
 * shell (header with logo/name, card, footer) is reused by every event so
 * future emails (account created, reminders, certificates…) only supply
 * their body block. All user-supplied values are HTML-escaped.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final String PRIMARY = "#1B3A6B";
    private static final String BACKGROUND = "#F2F6FA";
    private static final String ACCENT = "#C4972A";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final SystemSettingsService systemSettingsService;

    @Value("${app.application-url:http://localhost:5173}")
    private String applicationUrl;

    /** Subject + HTML for the cohort-assignment welcome email. */
    public RenderedEmail cohortAssignment(User student, Cohort cohort) {
        String subject = "Welcome to Your KBV Education Cohort";
        String body = """
                <p style="margin:0 0 16px;font-size:15px;color:#16243a;">Hi %s,</p>
                <p style="margin:0 0 16px;font-size:15px;color:#16243a;"><strong>Congratulations!</strong></p>
                <p style="margin:0 0 20px;font-size:15px;color:#16243a;">You have been successfully enrolled in a KBV Education cohort.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background:%s;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:18px 20px;">
                    <p style="margin:0 0 10px;font-size:13px;font-weight:bold;letter-spacing:.06em;text-transform:uppercase;color:%s;">Cohort Details</p>
                    <p style="margin:0 0 6px;font-size:14px;color:#16243a;">&bull; <strong>Cohort Name:</strong> %s</p>
                    <p style="margin:0 0 6px;font-size:14px;color:#16243a;">&bull; <strong>Start Date:</strong> %s</p>
                    <p style="margin:0 0 6px;font-size:14px;color:#16243a;">&bull; <strong>End Date:</strong> %s</p>
                    <p style="margin:0;font-size:14px;color:#16243a;">&bull; <strong>Exam Date:</strong> %s</p>
                  </td></tr>
                </table>

                <p style="margin:0 0 10px;font-size:15px;color:#16243a;">You can now log in to the KBV Education portal to access:</p>
                <p style="margin:0 0 20px;font-size:14px;color:#16243a;line-height:1.9;">
                  &bull; Lessons<br>&bull; Homework<br>&bull; Quizzes<br>&bull; Daily Reflections<br>&bull; Practice Logs<br>&bull; Dashboard
                </p>

                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                  <tr><td style="border-radius:8px;background:%s;">
                    <a href="%s" style="display:inline-block;padding:12px 28px;font-size:14px;font-weight:bold;color:#ffffff;text-decoration:none;">Open the Login Portal</a>
                  </td></tr>
                </table>

                <p style="margin:0;font-size:13px;color:#586a82;">If you have any questions, please contact your course administrator.</p>
                """.formatted(
                escape(student.getFullName()),
                BACKGROUND, ACCENT,
                escape(cohort.getName()),
                formatDate(cohort.getStartDate()),
                formatDate(cohort.getEndDate()),
                formatDate(cohort.getExamDate()),
                PRIMARY,
                escape(applicationUrl));
        return new RenderedEmail(subject, shell(subject, body));
    }

    /** Subject + HTML for the admin "Send test email" button. */
    public RenderedEmail testEmail() {
        String subject = "KBV Education — Test Email";
        String body = """
                <p style="margin:0 0 16px;font-size:15px;color:#16243a;">This is a test email from your KBV Education installation.</p>
                <p style="margin:0;font-size:14px;color:#586a82;">If you are reading this, your SMTP configuration is working correctly.</p>
                """;
        return new RenderedEmail(subject, shell(subject, body));
    }

    /** The shared responsive shell: brand header (logo when configured), white card, footer. */
    private String shell(String title, String bodyHtml) {
        SystemSettings settings = systemSettingsService.getActiveEntity();
        String appName = escape(settings.getApplicationName());
        String logoUrl = settings.getLogoPath() != null && settings.getLogoPath().startsWith("http")
                ? settings.getLogoPath() : null;
        String headerContent = logoUrl != null
                ? "<img src=\"" + escape(logoUrl) + "\" alt=\"" + appName
                        + "\" height=\"36\" style=\"display:block;height:36px;\">"
                : "<span style=\"font-size:18px;font-weight:bold;color:#ffffff;\">" + appName + "</span>";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                             style="max-width:560px;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                        <tr><td style="background:%s;border-radius:12px 12px 0 0;padding:20px 28px;">%s</td></tr>
                        <tr><td style="background:#ffffff;border-radius:0 0 12px 12px;padding:28px;">%s</td></tr>
                        <tr><td style="padding:18px 8px;text-align:center;">
                          <p style="margin:0 0 4px;font-size:13px;color:#586a82;">Regards,<br><strong style="color:%s;">KBV Education Team</strong></p>
                          <p style="margin:8px 0 0;font-size:12px;color:#8494a8;">%s &middot; This is an automated message, please do not reply.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(title), BACKGROUND, BACKGROUND, PRIMARY, headerContent, bodyHtml, PRIMARY, appName);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "To be announced" : DATE_FMT.format(date);
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    public record RenderedEmail(String subject, String html) {
    }
}
