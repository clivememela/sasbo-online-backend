package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
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
public class EmailServiceImpl implements EmailService {

    private static final String TEMPLATE = "documents/Sasbo Welcome Letter.docx";
    private static final String SUBJECT = "Welcome to SASBO – Your Membership is Confirmed";
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

        byte[] docx = buildFilledDocx(member);
        String fullName = buildFullName(member);

        for (String to : recipients) {
            send(to, fullName, docx);
        }
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

    private void send(String to, String fullName, byte[] docxBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(buildBody(fullName), false);

            DataSource attachment = new ByteArrayDataSource(
                docxBytes,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            helper.addAttachment("Sasbo Welcome Letter.docx", attachment);
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

                Welcome to SASBO – South African Buried Services Officers.

                Your membership has been successfully captured. Please find your personalised welcome letter attached.

                Should you have any questions, please contact your nearest SASBO office.

                Yours sincerely,
                SASBO Membership Administration
                """.formatted(fullName);
    }
}
