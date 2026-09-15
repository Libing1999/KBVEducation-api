package com.kbv.education.service.pdf;

import com.kbv.education.entity.enums.CertificateType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the fixed KBV tier designs render to a well-formed PDF with the actual
 * EB Garamond / General Sans glyphs embedded — not silently falling back to
 * openhtmltopdf's built-in Base14 fonts (Helvetica/Times), which was flagged as the
 * highest-risk step of wiring these designs in (openhtmltopdf does not fetch remote
 * Google Fonts / Fontshare {@code @import url(...)} CSS the way a browser does).
 */
class CertificatePdfRendererTest {

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer();

    @ParameterizedTest
    @EnumSource(value = CertificateType.class, names = {"TIER_1", "TIER_2", "TIER_3"})
    void rendersTierCertificateWithRealFontsEmbedded(CertificateType type) throws IOException {
        byte[] pdf = renderer.renderTierCertificate(type, "Jordan Émile O'Connor",
                "93%", "01 September 2026", "Autumn Cohort — 2026");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // The 14 built-in "Base14" fonts openhtmltopdf silently falls back to when it can't
        // resolve a font-family to one of our registered fonts (the failure mode this test
        // guards against - unstyled Helvetica/Times instead of the brand fonts).
        Set<String> base14Names = Set.of("Helvetica", "Helvetica-Bold", "Helvetica-Oblique",
                "Helvetica-BoldOblique", "Times-Roman", "Times-Bold", "Times-Italic",
                "Times-BoldItalic", "Courier", "Courier-Bold", "Courier-Oblique",
                "Courier-BoldOblique", "Symbol", "ZapfDingbats");

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDPage page = doc.getPage(0);

            Set<String> embeddedBaseFontNames = new HashSet<>();
            for (var fontName : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(fontName);
                assertThat(font.isEmbedded())
                        .as("font %s must be embedded (not a fallback Base14 font)", font.getName())
                        .isTrue();
                assertThat(base14Names)
                        .as("font resource %s (%s) must not be a Base14 fallback", fontName, font.getName())
                        .doesNotContain(font.getName());
                embeddedBaseFontNames.add(font.getName());
            }

            // EB Garamond keeps its real PostScript name in the embedded subset. General Sans'
            // free Fontshare download strips/obfuscates its internal name table to the literal
            // string "false" (confirmed by inspecting the downloaded TTF directly and by visually
            // rendering these PDFs to PNG - the sans-serif body text is unmistakably General Sans,
            // not a Base14 fallback) so it can't be matched by name; instead we assert enough
            // distinct embedded font programs are present to cover both families' weights used
            // on this page (2 EB Garamond weights/styles + at least 3 General Sans weights).
            assertThat(embeddedBaseFontNames)
                    .as("expected the real EB Garamond display font to be embedded, found: %s", embeddedBaseFontNames)
                    .anyMatch(n -> n.toLowerCase().contains("garamond"));
            assertThat(embeddedBaseFontNames)
                    .as("expected at least 5 distinct embedded font programs (EB Garamond + General Sans "
                            + "weights), found: %s", embeddedBaseFontNames)
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void rejectsCompletionTypeWhichHasNoFixedDesign() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                renderer.renderTierCertificate(CertificateType.COMPLETION, "Name", "90%", "date", "cohort"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
