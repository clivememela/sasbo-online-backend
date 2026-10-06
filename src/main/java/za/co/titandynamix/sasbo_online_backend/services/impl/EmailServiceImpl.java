package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class EmailServiceImpl implements EmailService {
    private static final String SUBJECT = "Welcome to SASBO – Your Membership is Confirmed";
    private static final String TEMPLATE = "documents/Sasbo Welcome Letter.pdf";

    private static final String ATTACHMENT_NAME = "Sasbo Welcome Letter.pdf";
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 48f;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.from:noreply@sasbo.co.za}")
    private String from;

    public EmailServiceImpl() {
    }

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendWelcomeLetter(Member member) {
        List<String> recipients = resolveRecipients(member);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No email address is available for this member.");
        }
        byte[] pdf = buildWelcomeLetterPdf(member);
        String fullName = fullName(member);
        for (String recipient : recipients) send(recipient, fullName, pdf);
    }

    @Override
    public byte[] buildWelcomeLetterPdf(Member member) {
        try (var input = new ClassPathResource(TEMPLATE).getInputStream();
             PDDocument document = Loader.loadPDF(input.readAllBytes());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            if (document.getNumberOfPages() == 0) document.addPage(new PDPage(PDRectangle.A4));
            PDPage page = document.getPage(0);
            float pageHeight = page.getMediaBox().getHeight();
            try (PDPageContentStream overlay = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                overlay.setNonStrokingColor(1f, 1f, 1f);
                for (float top : new float[]{252f, 275f, 289f, 303f, 317f, 335f, 378f}) {
                    overlay.addRect(64f, pageHeight - top - 12f, 480f, 16f);
                    overlay.fill();
                }
                overlay.beginText();
                overlay.setNonStrokingColor(0.08f, 0.32f, 0.25f);
                overlay.setFont(bold, 10);
                overlay.newLineAtOffset(72f, pageHeight - 262f);
                overlay.showText(safe(LocalDate.now().format(LONG_DATE)));
                overlay.newLineAtOffset(0, -23f);
                overlay.showText(safe(fullName(member)));
                overlay.setFont(regular, 10);
                for (int i = 0; i < 3; i++) {
                    overlay.newLineAtOffset(0, -14f);
                    List<String> lines = addressLines(member.getAddress());
                    overlay.showText(safe(i < lines.size() ? lines.get(i) : ""));
                }
                overlay.newLineAtOffset(0, -14f);
                overlay.setFont(bold, 10);
                overlay.showText(safe("Your Sasbo Membership No: " + value(member.getMembershipNumber(), "(no membership number yet)")));
                overlay.newLineAtOffset(0, -43f);
                overlay.setFont(regular, 10);
                overlay.showText(safe("Dear " + fullName(member) + ","));
                overlay.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate welcome letter PDF: " + e.getMessage(), e);
        }
    }

    private void send(String to, String name, byte[] pdf) {
        if (this.mailSender == null) {
            throw new IllegalStateException("Mail service is not configured.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(buildBody(name), false);
            DataSource attachment = new ByteArrayDataSource(pdf, "application/pdf");
            helper.addAttachment(ATTACHMENT_NAME, attachment);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send welcome letter: " + e.getMessage(), e);
        }
    }

    private static List<String> resolveRecipients(Member member) {
        List<String> recipients = new ArrayList<>();
        String preference = member.getPreferredEmail();
        if (("personal".equalsIgnoreCase(preference) || "both".equalsIgnoreCase(preference)) && hasValue(member.getPersonalEmail())) recipients.add(member.getPersonalEmail().trim());
        if (("work".equalsIgnoreCase(preference) || "both".equalsIgnoreCase(preference)) && hasValue(member.getWorkEmail())) recipients.add(member.getWorkEmail().trim());
        if (recipients.isEmpty() && hasValue(member.getPersonalEmail())) recipients.add(member.getPersonalEmail().trim());
        else if (recipients.isEmpty() && hasValue(member.getWorkEmail())) recipients.add(member.getWorkEmail().trim());
        return recipients;
    }

    private static List<String> addressLines(String address) {
        return hasValue(address) ? List.of(address.split("\\s*,\\s*")) : List.of("");
    }

    private static String fullName(Member member) {
        return Stream.of(member.getTitle(), member.getFirstNames(), member.getSurname()).filter(EmailServiceImpl::hasValue).reduce((a, b) -> a + " " + b).orElse("Member");
    }

    private static String safe(String value) { return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?"); }
    private static String value(String value, String fallback) { return hasValue(value) ? value.trim() : fallback; }
    private static boolean hasValue(String value) { return value != null && !value.isBlank(); }
    private static String date(java.time.LocalDate value) { return value == null ? "—" : value.format(SHORT_DATE); }
    private static List<String> splitAddress(String address) { return hasValue(address) ? List.of(address.split("\\s*,\\s*")) : List.of(); }
    private static String buildBody(String name) { return "Dear " + name + ",\n\nWelcome to SASBO – Sasbo – The Finance Union.\n\nYour membership has been successfully captured. Please find your personalised welcome letter attached.\n\nShould you have any questions, please contact your nearest SASBO office.\n\nYours sincerely,\nSASBO Membership Administration"; }

    private static final class PageWriter {
        private final PDDocument document;
        private final PDType1Font regular;
        private final PDType1Font bold;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private final float width;
        private final float leading = 14f;

        private PageWriter(PDDocument document, PDType1Font regular, PDType1Font bold) throws IOException {
            this.document = document; this.regular = regular; this.bold = bold;
            newPage(); this.width = PDRectangle.A4.getWidth() - 2 * MARGIN;
        }
        private void newPage() throws IOException {
            if (stream != null) { stream.endText(); stream.close(); }
            page = new PDPage(PDRectangle.A4); document.addPage(page); stream = new PDPageContentStream(document, page);
            stream.beginText(); stream.setFont(regular, 10); stream.newLineAtOffset(MARGIN, PDRectangle.A4.getHeight() - MARGIN); y = PDRectangle.A4.getHeight() - MARGIN;
        }
        private void ensure(float height) throws IOException { if (y - height < MARGIN) newPage(); }
        private void line(String text, boolean isBold) throws IOException { ensure(leading); stream.setFont(isBold ? bold : regular, isBold ? 10.5f : 10f); stream.showText(safe(text)); stream.newLineAtOffset(0, -leading); y -= leading; }
        private void right(String text) throws IOException { ensure(leading); stream.setFont(regular, 10); stream.newLineAtOffset(Math.max(0, width - regular.getStringWidth(text) / 100f), 0); line(text, false); stream.newLineAtOffset(-Math.max(0, width - regular.getStringWidth(text) / 100f), 0); }
        private void heading(String text) throws IOException { ensure(leading); stream.setFont(bold, 13); stream.showText(safe(text)); stream.newLineAtOffset(0, -18); y -= 18; }
        private void paragraph(String text) throws IOException { for (String line : wrap(text)) line(line, false); gap(); }
        private void detail(String label, String value) throws IOException { ensure(leading); stream.setFont(bold, 9); stream.showText(safe(label)); stream.newLineAtOffset(145, 0); stream.setFont(regular, 9); stream.showText(safe(value)); stream.newLineAtOffset(-145, -leading); y -= leading; }
        private void box(String text) throws IOException { ensure(28); stream.setFont(bold, 10.5f); stream.showText(safe(text)); stream.newLineAtOffset(0, -leading); y -= leading + 8; }
        private void gap() throws IOException { ensure(leading); stream.newLineAtOffset(0, -7); y -= 7; }
        private List<String> wrap(String text) throws IOException { List<String> lines = new ArrayList<>(); String current = ""; for (String word : text.split("\\s+")) { String candidate = current.isEmpty() ? word : current + " " + word; if (regular.getStringWidth(candidate) / 100f <= width) current = candidate; else { lines.add(current); current = word; } } if (!current.isEmpty()) lines.add(current); return lines; }
        private void close() throws IOException { stream.endText(); stream.close(); }
        private static String safe(String value) { return value.replaceAll("[^\\x20-\\x7E]", "?"); }
    }
}
