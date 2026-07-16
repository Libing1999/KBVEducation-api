package com.kbv.education.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a certificate to PDF via openhtmltopdf (HTML+CSS in, PDF out), so
 * template edits are a string/CSS change rather than hand-computed PDF
 * coordinates. The outer layout (border, title, name, footer) is fixed Java
 * code parameterized by branding fields; only the citation paragraph in the
 * middle comes from the admin-edited {@code bodyTemplate} — see
 * CertificateTemplate's Javadoc for why customization stops there.
 */
@Component
public class CertificatePdfRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final String DOCUMENT = """
            <html>
            <head><meta charset="UTF-8"/><style>
                @page { size: A4 landscape; margin: 0; }
                body { font-family: 'Helvetica', 'Arial', sans-serif; text-align: center;
                       padding: 48px; margin: 0; color: #1e293b; }
                .frame { border: 10px solid %1$s; padding: 48px; height: 100%%; box-sizing: border-box; }
                .logo { max-height: 64px; margin-bottom: 16px; }
                .institution { font-size: 22px; font-weight: bold; color: %1$s;
                               text-transform: uppercase; letter-spacing: 2px; margin-bottom: 4px; }
                .cert-title { font-size: 15px; color: #64748b; margin-bottom: 36px; }
                .lead-in { font-size: 14px; color: #475569; }
                .student-name { font-size: 34px; font-weight: bold; margin: 18px 0 28px;
                                display: inline-block; border-bottom: 2px solid %1$s; padding-bottom: 8px; }
                .body-text { font-size: 15px; line-height: 1.7; color: #334155;
                             max-width: 620px; margin: 0 auto 40px; }
                .footer { display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8;
                          border-top: 1px solid #e2e8f0; padding-top: 16px; }
            </style></head>
            <body>
              <div class="frame">
                %2$s
                <div class="institution">%3$s</div>
                <div class="cert-title">%4$s</div>
                <div class="lead-in">This certifies that</div>
                <div class="student-name">%5$s</div>
                <div class="body-text">%6$s</div>
                <div class="footer">
                  <span>Certificate No. %7$s</span>
                  <span>Issued %8$s</span>
                </div>
              </div>
            </body>
            </html>
            """;

    /**
     * @param bodyTemplate    admin-edited citation text, may contain {@code {{placeholder}}} tokens
     * @param placeholders    values substituted into bodyTemplate (studentName, tierName, cohortName,
     *                        issueDate, certificateNumber — any subset the template chooses to use)
     * @param certTitle       fixed heading derived from certificate type, e.g. "Tier 1 Achievement Certificate"
     * @param institutionName branding, already resolved by the caller (template override or default)
     * @param logoPath        absolute filesystem path to a logo image, or null for none
     * @param primaryColorHex branding accent color, e.g. "#1B3A6B"
     * @param studentName     shown prominently regardless of whether bodyTemplate references it
     * @param certificateNumber footer reference number
     * @param issueDate       footer date, already formatted for display
     */
    public byte[] render(String bodyTemplate, Map<String, String> placeholders, String certTitle,
                          String institutionName, String logoPath, String primaryColorHex,
                          String studentName, String certificateNumber, String issueDate) {
        String bodyHtml = substitute(bodyTemplate, placeholders);
        String logoHtml = (logoPath == null || logoPath.isBlank())
                ? ""
                : "<img class=\"logo\" src=\"" + escapeAttribute(logoPath) + "\"/>";

        String html = DOCUMENT.formatted(
                primaryColorHex,
                logoHtml,
                escape(institutionName),
                escape(certTitle),
                escape(studentName),
                bodyHtml,
                escape(certificateNumber),
                escape(issueDate));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render certificate PDF", e);
        }
        return out.toByteArray();
    }

    private String substitute(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(escape(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private String escapeAttribute(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
