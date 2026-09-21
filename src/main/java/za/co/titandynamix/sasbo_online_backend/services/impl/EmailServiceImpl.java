package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;

import jakarta.activation.DataSource;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.mail.username:}'.length() > 0")
public class EmailServiceImpl implements EmailService {

    private static final String TEMPLATE = "documents/Sasbo Welcome Letter.docx";
    private static final String SUBJECT = "Welcome to SASBO – Your Membership is Confirmed";
    private static final String ATTACHMENT_NAME = "Sasbo Welcome Letter.pdf";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    @Override
    public void sendWelcomeLetter(Member member) {
        List<String> recipients = resolveRecipients(member);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No email address is available for this member.");
        }

        byte[] pdf = buildWelcomeLetterPdf(member);
        String fullName = buildFullName(member);

        for (String to : recipients) {
            send(to, fullName, pdf);
        }
    }

    @Override
    public byte[] buildWelcomeLetterPdf(Member member) {
        byte[] docx = buildFilledDocx(member);
        return convertDocxToPdf(docx);
    }

    // ── Placeholder substitution ──────────────────────────────────────────────

    private byte[] buildFilledDocx(Member member) {
        String fullName = buildFullName(member);
        String today = LocalDate.now().format(DATE_FMT);
        String membershipNo = member.getMembershipNumber() != null ? member.getMembershipNumber() : "";

        // The docx has these exact <w:t> text nodes (HTML-encoded in XML, plain here):
        //   >>>>Date <<<<          → today's date
        //   >>Title>>Name>>Name<< → title + full name
        //   >>Address Line 1<<<<  → address line 1
        //   >>Address Line 2 <<<< → address line 2
        //   >>>Number<<           → membership number (appears twice)
        //   >>Address Line 3<<<<  → address line 3
        //   >>>Postal Code<<<     → postal code
        //   >>>Name<<<            → "Dear Name"
        Map<String, String> replacements = Map.of(
            ">>>>Date <<<<",         today,
            ">>Title>>Name>>Name<<", fullName,
            ">>Address Line 1<<<<",  member.getAddress() != null ? member.getAddress() : "",
            ">>Address Line 2 <<<<", "",
            ">>>Number<<",           membershipNo,
            ">>Address Line 3<<<<",  "",
            ">>>Postal Code<<<",     "",
            ">>>Name<<<",            fullName
        );

        try (var in = new ClassPathResource(TEMPLATE).getInputStream();
             XWPFDocument doc = new XWPFDocument(in)) {

            // Replace in body paragraphs
            doc.getParagraphs().forEach(p ->
                p.getRuns().forEach(run -> replaceInRun(run, replacements))
            );
            // Replace in tables
            doc.getTables().forEach(table ->
                table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell ->
                        cell.getParagraphs().forEach(p ->
                            p.getRuns().forEach(run -> replaceInRun(run, replacements))
                        )
                    )
                )
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to fill welcome letter template: " + e.getMessage(), e);
        }
    }

    private byte[] convertDocxToPdf(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(docxBytes));
             PDDocument pdf = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            float fontSize = 12f;
            float leading = 14.5f;
            float margin = 48f;
            float width = page.getMediaBox().getWidth() - 2 * margin;
            float y = page.getMediaBox().getHeight() - margin;

            PDPageContentStream stream = new PDPageContentStream(pdf, page);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(margin, y);

            for (String paragraph : extractParagraphs(doc)) {
                List<String> wrapped = wrapLines(paragraph, font, fontSize, width);
                for (String line : wrapped) {
                    if (y - leading <= margin) {
                        stream.endText();
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        pdf.addPage(page);
                        y = page.getMediaBox().getHeight() - margin;
                        stream = new PDPageContentStream(pdf, page);
                        stream.beginText();
                        stream.setFont(font, fontSize);
                        stream.newLineAtOffset(margin, y);
                    }
                    stream.showText(line);
                    stream.newLineAtOffset(0, -leading);
                    y -= leading;
                }
                if (y - leading <= margin) {
                    stream.endText();
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    pdf.addPage(page);
                    y = page.getMediaBox().getHeight() - margin;
                    stream = new PDPageContentStream(pdf, page);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(margin, y);
                }
                stream.newLineAtOffset(0, -leading);
                y -= leading;
            }

            stream.endText();
            stream.close();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert welcome letter to PDF: " + e.getMessage(), e);
        }
    }

    private static List<String> extractParagraphs(XWPFDocument document) {
        List<String> paragraphs = new ArrayList<>();
        document.getParagraphs().forEach(paragraph -> {
            String text = paragraph.getText();
            if (text != null && !text.isBlank()) {
                paragraphs.add(text.trim());
            }
        });
        return paragraphs;
    }

    private static List<String> wrapLines(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        if (text.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
            if (candidateWidth <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static void replaceInRun(XWPFRun run, Map<String, String> replacements) {
        String text = run.getText(0);
        if (text == null) return;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (text.contains(entry.getKey())) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }
        run.setText(text, 0);
    }

    // ── Email dispatch ────────────────────────────────────────────────────────

    private void send(String to, String fullName, byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(buildBody(fullName), false);

            DataSource attachment = new ByteArrayDataSource(
                pdfBytes,
                "application/pdf"
            );
            helper.addAttachment(ATTACHMENT_NAME, attachment);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send welcome letter to " + to + ": " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> resolveRecipients(Member member) {
        String pref = member.getPreferredEmail();
        List<String> out = new ArrayList<>();
        boolean wantPersonal = "personal".equalsIgnoreCase(pref) || "both".equalsIgnoreCase(pref);
        boolean wantWork     = "work".equalsIgnoreCase(pref)     || "both".equalsIgnoreCase(pref);
        if (wantPersonal && hasValue(member.getPersonalEmail())) out.add(member.getPersonalEmail().trim());
        if (wantWork     && hasValue(member.getWorkEmail()))     out.add(member.getWorkEmail().trim());
        // Fallback when no preference is set
        if (out.isEmpty()) {
            if (hasValue(member.getPersonalEmail())) out.add(member.getPersonalEmail().trim());
            else if (hasValue(member.getWorkEmail())) out.add(member.getWorkEmail().trim());
        }
        return out;
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    private static String buildFullName(Member member) {
        return List.of(
            member.getTitle()      != null ? member.getTitle()      : "",
            member.getFirstNames() != null ? member.getFirstNames() : "",
            member.getSurname()    != null ? member.getSurname()    : ""
        ).stream().filter(s -> !s.isBlank())
         .reduce("", (a, b) -> a.isBlank() ? b : a + " " + b);
    }

    private static String buildBody(String fullName) {
        return """
                Dear %s,

                Welcome to SASBO – Sasbo – The Finance Union.

                Your membership has been successfully captured. Please find your personalised welcome letter attached.

                Should you have any questions, please contact your nearest SASBO office.

                Yours sincerely,
                SASBO Membership Administration
                """.formatted(fullName);
    }
}
