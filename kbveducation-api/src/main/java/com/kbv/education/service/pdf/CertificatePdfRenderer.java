package com.kbv.education.service.pdf;

import com.kbv.education.entity.enums.CertificateType;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a certificate to PDF via openhtmltopdf (HTML+CSS in, PDF out).
 *
 * <p>Two rendering paths live here:
 * <ul>
 *   <li>{@link #renderTierCertificate} — the customer-approved KBV tier designs
 *       (27 Aug 2026 design system, folder {@code KBV-Graduation-Certificates}) for
 *       {@link CertificateType#TIER_1}, {@link CertificateType#TIER_2} and
 *       {@link CertificateType#TIER_3}. Each tier's markup/inline-styles are a
 *       byte-for-byte port of that tier's approved {@code certificate.html} (with
 *       {@code var(--token)} references from {@code tokens.css} resolved to literal
 *       values, since the design is fixed and never customized per-template). The
 *       only variable content is student name, composite score, award date and the
 *       cohort/term-year + school footer line — the four spots the customer marked
 *       with {@code data-field} / {@code [Cohort — Term Year]}. Do not restyle these
 *       templates; if the design changes, re-port from the customer's approved HTML.</li>
 *   <li>{@link #render} — the original generic bordered-frame template, driven by an
 *       admin-edited {@code bodyTemplate}. Kept only for {@link CertificateType#COMPLETION},
 *       which the customer's design handoff does not cover (only three tier designs were
 *       supplied). See {@code CertificateServiceImpl} for how the two paths are selected.</li>
 * </ul>
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

    // ------------------------------------------------------------------
    // Fixed KBV tier designs (customer-approved 27.08.2026). Structure and
    // colors are ported from each tier's approved certificate.html, with
    // three engine-forced adaptations from the source markup (openhtmltopdf
    // 1.0.10 is built on Flying Saucer's CSS2.1 box model, not a browser
    // engine):
    //   1. No CSS flexbox support at all (verified: no flex-* classes in the
    //      openhtmltopdf-core jar). Every `display:flex` layout is redone with
    //      block flow + text-align:center / margin:auto for centering,
    //      inline-block + vertical-align:top for the side-by-side
    //      Awarded/Signature columns, and position:absolute (bottom:0) in
    //      place of `margin-top:auto` to pin the footer.
    //   2. No rgba() color support (confirmed by "Value for color must be an
    //      identifier or a color" parse warnings) — every translucent white/
    //      gold rgba() text color from the source is pre-blended against its
    //      tier's solid background into an equivalent flat hex so the visual
    //      color is unchanged.
    //   3. No `inset` shorthand support — replaced with explicit top/left/
    //      width/height. The fine engraved-ring gradient texture inside each
    //      rosette (repeating-conic-gradient / repeating-radial-gradient) and
    //      the soft background glow behind tier 1/2's rosette are dropped —
    //      openhtmltopdf does not support these gradient functions either,
    //      and they are purely decorative (no data, no structural role); the
    //      ring border, fill and rosette number are kept exactly.
    // @@TOKEN@@ markers are the only per-certificate substitution points.
    // ------------------------------------------------------------------

    /** Fixed footer school line, identical across all three approved designs (not a data-field). */
    private static final String SCHOOL_LINE = "Raffles World Academy";

    // 793px, not the customer file's rounded 794px: A4 landscape at 96dpi is really
    // 793.7px tall, and openhtmltopdf's pagination (unlike a browser's forgiving
    // sub-pixel clipping) spills a blank second page if the box is a hair taller
    // than the actual page box. 793px is visually identical and keeps this to one page.
    private static final String CERT_HEAD_STYLE = """
            <style>
              html,body { margin:0; padding:0; width:1123px; height:793px; overflow:hidden; }
              body { background:#ffffff; }
              .certificate {
                width:1123px; height:793px; box-sizing:border-box; overflow:hidden;
                position:relative; padding:44px 96px 32px; font-feature-settings:'liga' 1,'kern' 1;
              }
              @page { size:A4 landscape; margin:0; }
            </style>
            """;

    private static final String TIER_1_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8"/>
            <title>KBV — Certificate of graduation · Tier one</title>
            %s
            </head>
            <body>
            <section class="certificate" style="background:#1B3A6B;">
              <div style="text-align:center;">
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif;font-weight:500;font-size:26px;line-height:1;letter-spacing:0.14em;color:#F2F6FA;padding-left:0.14em;">KBV</div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif;font-weight:500;font-size:0.75rem;letter-spacing:0.22em;text-transform:uppercase;white-space:nowrap;color:#CBD4E0;"><span style="display:inline-block; width:32px; height:2px; background:#C4972A; vertical-align:middle; margin-right:16px;"></span><span style="vertical-align:middle;">Certificate of graduation</span></div>
              </div>
              <div style="margin:14px auto 0; width:130px; height:130px; border-radius:50%%; position:relative; border:1px solid #786D47;">
                <div style="position:absolute; top:21px; left:21px; width:88px; height:88px; box-sizing:border-box; border-radius:50%%; background:#C4972A; text-align:center; line-height:88px;">
                  <span style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:32px; color:#241a05;">1</span>
                </div>
              </div>
              <div style="margin-top:16px; font-family:'General Sans',system-ui,sans-serif; font-weight:600; font-size:12px; letter-spacing:0.16em; text-transform:uppercase; color:#C4972A; text-align:center;">Tier one <span style="color:#7185A4; margin:0 6px;">·</span>With distinction</div>
              <div style="margin-top:20px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:16px; color:#9CABC1; text-align:center;">This certifies that</div>
              <div style="margin-top:10px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:4rem; line-height:1.05; letter-spacing:-0.01em; color:#F2F6FA; text-align:center;">@@STUDENT_NAME@@</div>
              <div style="margin:12px auto 0; width:52px; height:1.5px; background:#C4972A;"></div>
              <div style="margin-top:18px; font-family:'General Sans',system-ui,sans-serif; font-size:15px; line-height:1.5; color:#B6C1D2; max-width:460px; margin-left:auto; margin-right:auto; text-align:center;">has completed the KBV Foundations Program for MYP at the highest level of Graduation</div>
              <div style="margin-top:12px; font-family:'General Sans',system-ui,sans-serif; font-size:10px; font-weight:500; letter-spacing:0.14em; text-transform:uppercase; color:#7589A7; text-align:center;">Mindset · Time &amp; environment · Study systems — completed</div>
              <div style="margin-top:20px; text-align:center;">
                <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#7589A7;">Composite score</div>
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:30px; line-height:1; color:#C4972A; margin-top:4px;">@@COMPOSITE_SCORE@@</div>
              </div>
              <div style="position:absolute; left:0; right:0; bottom:32px; text-align:center;">
                <div style="width:480px; height:1px; margin:0 auto; background:#404E5D;"></div>
                <div style="margin-top:18px;"><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#AB8934;">Awarded</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:13px; color:#D2DAE5; margin-top:5px;">@@DATE@@</div>
                  </div><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#AB8934;">Signature</div>
                    <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:18px; color:#F2F6FA; margin-top:5px;">Bhavya Madan</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.12em; text-transform:uppercase; color:#7185A4; margin-top:3px;">KBV Education</div>
                  </div></div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.14em; text-transform:uppercase; color:#7185A4;">@@FOOTER_LINE@@</div>
              </div>
            </section>
            </body>
            </html>
            """.formatted(CERT_HEAD_STYLE);

    private static final String TIER_2_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8"/>
            <title>KBV — Certificate of graduation · Tier two</title>
            %s
            </head>
            <body>
            <section class="certificate" style="background:#1B3A6B;">
              <div style="text-align:center;">
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif;font-weight:500;font-size:26px;line-height:1;letter-spacing:0.14em;color:#F2F6FA;padding-left:0.14em;">KBV</div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif;font-weight:500;font-size:0.75rem;letter-spacing:0.22em;text-transform:uppercase;white-space:nowrap;color:#CBD4E0;"><span style="display:inline-block; width:32px; height:2px; background:#C4972A; vertical-align:middle; margin-right:16px;"></span><span style="vertical-align:middle;">Certificate of graduation</span></div>
              </div>
              <div style="margin:14px auto 0; width:130px; height:130px; border-radius:50%%; position:relative; border:1px solid #5F5F51;">
                <div style="position:absolute; top:21px; left:21px; width:88px; height:88px; box-sizing:border-box; border-radius:50%%; border:2px solid #C4972A; text-align:center; line-height:84px;">
                  <span style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:32px; color:#C4972A;">2</span>
                </div>
              </div>
              <div style="margin-top:16px; font-family:'General Sans',system-ui,sans-serif; font-weight:600; font-size:12px; letter-spacing:0.16em; text-transform:uppercase; color:#C4972A; text-align:center;">Tier two <span style="color:#7185A4; margin:0 6px;">·</span>Graduate</div>
              <div style="margin-top:20px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:16px; color:#9CABC1; text-align:center;">This certifies that</div>
              <div style="margin-top:10px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:4rem; line-height:1.05; letter-spacing:-0.01em; color:#F2F6FA; text-align:center;">@@STUDENT_NAME@@</div>
              <div style="margin:12px auto 0; width:52px; height:1.5px; background:#C4972A;"></div>
              <div style="margin-top:18px; font-family:'General Sans',system-ui,sans-serif; font-size:15px; line-height:1.5; color:#B6C1D2; max-width:460px; margin-left:auto; margin-right:auto; text-align:center;">has completed the KBV Method — mindset, time and environment, study systems — taught in person, one cohort at a time.</div>
              <div style="margin-top:12px; font-family:'General Sans',system-ui,sans-serif; font-size:10px; font-weight:500; letter-spacing:0.14em; text-transform:uppercase; color:#7589A7; text-align:center;">Mindset · Time &amp; environment · Study systems — completed</div>
              <div style="margin-top:20px; text-align:center;">
                <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#7589A7;">Composite score</div>
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:30px; line-height:1; color:#C4972A; margin-top:4px;">@@COMPOSITE_SCORE@@</div>
              </div>
              <div style="position:absolute; left:0; right:0; bottom:32px; text-align:center;">
                <div style="width:480px; height:1px; margin:0 auto; background:#404E5D;"></div>
                <div style="margin-top:18px;"><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#AB8934;">Awarded</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:13px; color:#D2DAE5; margin-top:5px;">@@DATE@@</div>
                  </div><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#AB8934;">Signature</div>
                    <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:18px; color:#F2F6FA; margin-top:5px;">Bhavya Madan</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.12em; text-transform:uppercase; color:#7185A4; margin-top:3px;">KBV Education</div>
                  </div></div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.14em; text-transform:uppercase; color:#7185A4;">@@FOOTER_LINE@@</div>
              </div>
            </section>
            </body>
            </html>
            """.formatted(CERT_HEAD_STYLE);

    private static final String TIER_3_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8"/>
            <title>KBV — Certificate of completion · Tier three</title>
            %s
            </head>
            <body>
            <section class="certificate" style="background:#F2F6FA;">
              <div style="text-align:center;">
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif;font-weight:500;font-size:26px;line-height:1;letter-spacing:0.14em;color:#1B3A6B;padding-left:0.14em;">KBV</div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif;font-weight:500;font-size:0.75rem;letter-spacing:0.22em;text-transform:uppercase;white-space:nowrap;color:#1B3A6B;"><span style="display:inline-block; width:32px; height:2px; background:#C4972A; vertical-align:middle; margin-right:16px;"></span><span style="vertical-align:middle;">Certificate of completion</span></div>
              </div>
              <div style="margin:14px auto 0; width:130px; height:130px; border-radius:50%%; position:relative; border:1px solid #B2BECF;">
                <div style="position:absolute; top:21px; left:21px; width:88px; height:88px; box-sizing:border-box; border-radius:50%%; border:1.5px solid #1B3A6B; text-align:center; line-height:85px;">
                  <span style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:32px; color:#1B3A6B;">3</span>
                </div>
              </div>
              <div style="margin-top:16px; font-family:'General Sans',system-ui,sans-serif; font-weight:600; font-size:12px; letter-spacing:0.16em; text-transform:uppercase; color:#1B3A6B; text-align:center;">Tier three <span style="color:#8A93A3; margin:0 6px;">·</span>Completion</div>
              <div style="margin-top:20px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:16px; color:#4A5568; text-align:center;">This certifies that</div>
              <div style="margin-top:10px; font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:4rem; line-height:1.05; letter-spacing:-0.01em; color:#1B3A6B; text-align:center;">@@STUDENT_NAME@@</div>
              <div style="margin:12px auto 0; width:52px; height:1.5px; background:#1B3A6B;"></div>
              <div style="margin-top:18px; font-family:'General Sans',system-ui,sans-serif; font-size:15px; line-height:1.5; color:#232c3b; max-width:460px; margin-left:auto; margin-right:auto; text-align:center;">has completed the KBV Method — mindset, time and environment, study systems — taught in person, one cohort at a time.</div>
              <div style="margin-top:12px; font-family:'General Sans',system-ui,sans-serif; font-size:10px; font-weight:500; letter-spacing:0.14em; text-transform:uppercase; color:#8A93A3; text-align:center;">Mindset · Time &amp; environment · Study systems — completed</div>
              <div style="margin-top:20px; text-align:center;">
                <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#8A93A3;">Composite score</div>
                <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-weight:500; font-size:30px; line-height:1; color:#1B3A6B; margin-top:4px;">@@COMPOSITE_SCORE@@</div>
              </div>
              <div style="position:absolute; left:0; right:0; bottom:32px; text-align:center;">
                <div style="width:480px; height:1px; margin:0 auto; background:#dde2ea;"></div>
                <div style="margin-top:18px;"><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#9a7420;">Awarded</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:13px; color:#1A2230; margin-top:5px;">@@DATE@@</div>
                  </div><div style="display:inline-block; vertical-align:top; text-align:center; width:220px; margin:0 32px;">
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.16em; text-transform:uppercase; color:#9a7420;">Signature</div>
                    <div style="font-family:'EB Garamond','Garamond','Cambria',Georgia,serif; font-style:italic; font-size:18px; color:#1B3A6B; margin-top:5px;">Bhavya Madan</div>
                    <div style="font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.12em; text-transform:uppercase; color:#8A93A3; margin-top:3px;">KBV Education</div>
                  </div></div>
                <div style="margin-top:14px; font-family:'General Sans',system-ui,sans-serif; font-size:9px; letter-spacing:0.14em; text-transform:uppercase; color:#8A93A3;">@@FOOTER_LINE@@</div>
              </div>
            </section>
            </body>
            </html>
            """.formatted(CERT_HEAD_STYLE);

    /**
     * Renders one of the three fixed, customer-approved KBV tier designs.
     *
     * @param certificateType   {@code TIER_1}, {@code TIER_2} or {@code TIER_3} — {@code COMPLETION}
     *                          has no design here, see class Javadoc
     * @param studentName       substituted into {@code data-field="student-name"}
     * @param compositeScoreDisplay substituted into {@code data-field="composite-score"} — the
     *                          real per-student number (e.g. {@code "93%"}), not the tier's default range
     * @param awardDateDisplay  substituted into {@code data-field="date"}
     * @param cohortTermYear    the cohort/term-year line (e.g. {@code "Autumn Cohort — 2026"}), or
     *                          null/blank if the student has no active cohort — the fixed school line
     *                          is always shown regardless
     */
    public byte[] renderTierCertificate(CertificateType certificateType, String studentName,
                                         String compositeScoreDisplay, String awardDateDisplay,
                                         String cohortTermYear) {
        String template = switch (certificateType) {
            case TIER_1 -> TIER_1_TEMPLATE;
            case TIER_2 -> TIER_2_TEMPLATE;
            case TIER_3 -> TIER_3_TEMPLATE;
            case COMPLETION -> throw new IllegalArgumentException(
                    "CertificateType.COMPLETION has no fixed KBV design; use render(...) instead");
        };

        String footerLine = (cohortTermYear == null || cohortTermYear.isBlank())
                ? SCHOOL_LINE
                : escape(cohortTermYear) + " · " + SCHOOL_LINE;

        String html = template
                .replace("@@STUDENT_NAME@@", escape(studentName))
                .replace("@@COMPOSITE_SCORE@@", escape(compositeScoreDisplay))
                .replace("@@DATE@@", escape(awardDateDisplay))
                .replace("@@FOOTER_LINE@@", footerLine);

        return renderPdf(html);
    }

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

        return renderPdf(html);
    }

    private byte[] renderPdf(String html) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        registerFonts(builder);
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render certificate PDF", e);
        }
        return out.toByteArray();
    }

    /**
     * Registers the actual EB Garamond / General Sans static font files bundled under
     * {@code src/main/resources/fonts/} so openhtmltopdf embeds real glyphs instead of
     * falling back to its built-in Base14 fonts. openhtmltopdf does not fetch remote
     * {@code @import url(...)} Google Fonts / Fontshare CSS the way a browser does, so the
     * KBV tier templates above reference these two family names directly via inline
     * {@code font-family} — no {@code @font-face} rule is needed in the HTML because the
     * families are registered Java-side here.
     */
    private void registerFonts(PdfRendererBuilder builder) {
        useFont(builder, "fonts/EBGaramond-Regular.ttf", "EB Garamond", 400, FontStyle.NORMAL);
        useFont(builder, "fonts/EBGaramond-Medium.ttf", "EB Garamond", 500, FontStyle.NORMAL);
        useFont(builder, "fonts/EBGaramond-SemiBold.ttf", "EB Garamond", 600, FontStyle.NORMAL);
        useFont(builder, "fonts/EBGaramond-Italic.ttf", "EB Garamond", 400, FontStyle.ITALIC);
        useFont(builder, "fonts/GeneralSans-Regular.ttf", "General Sans", 400, FontStyle.NORMAL);
        useFont(builder, "fonts/GeneralSans-Medium.ttf", "General Sans", 500, FontStyle.NORMAL);
        useFont(builder, "fonts/GeneralSans-SemiBold.ttf", "General Sans", 600, FontStyle.NORMAL);
        useFont(builder, "fonts/GeneralSans-Bold.ttf", "General Sans", 700, FontStyle.NORMAL);
    }

    private void useFont(PdfRendererBuilder builder, String classpathPath, String family,
                          int weight, FontStyle style) {
        builder.useFont(() -> openFontStream(classpathPath), family, weight, style, true);
    }

    private InputStream openFontStream(String classpathPath) {
        try {
            return new ClassPathResource(classpathPath).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("Missing bundled certificate font on classpath: " + classpathPath, e);
        }
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

    /**
     * XML-safe escaping (the 5 predefined XML entities only). openhtmltopdf parses the
     * document with a strict XML parser, which — unlike a browser's lenient HTML5 parser —
     * rejects named HTML4 character entities (e.g. {@code &Eacute;}, as
     * {@link org.springframework.web.util.HtmlUtils#htmlEscape} would produce for accented
     * characters) as undeclared entities. Non-ASCII characters are left as literal UTF-8,
     * which is valid XML given the {@code UTF-8} charset declared on every template.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private String escapeAttribute(String value) {
        return escape(value);
    }
}
