package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two register rows the welcome letter carries: <b>A55</b> (the letter is
 * emailed with its membership-number placeholder intact) and <b>B8</b>'s leak
 * half (a failed send names the recipient's address, which the catch-all
 * handler copies into a 500 body on an endpoint anyone may call).
 *
 * <h2>🔴 Why these live in their own file</h2>
 * They were written in {@code EmailServiceImplTest} on 2026-09-17 (`f47e741`).
 * Upstream `134e057` then ADDED a file of that same name -- 167 insertions, one
 * deletion -- and the merge `0174439` resolved by taking it, so every assertion
 * here vanished from `main` while {@code KnownDefects} kept both rows. That is
 * the exact rot {@code DisabledPopulationTest} exists to catch, and it caught
 * it: two rows declared, neither cited, `main` red. Reported by the ACB session
 * from a clean checkout of `origin/main`.
 *
 * <p>So the pins are restored BESIDE the incumbent's file rather than back
 * inside it. Two files, two subjects: his covers who the letter goes to; this
 * covers what the letter SAYS and what a failure discloses. Nothing here edits
 * his tests, so the next rewrite of that file cannot take these with it again.
 *
 * <p>⚠️ Both defects were re-read in `EmailServiceImpl` at `0174439` before this
 * was restored -- the replacement still visits only {@code getParagraphs()} and
 * {@code getTables()}, and the failure message still interpolates {@code to} --
 * so neither row was fixed by the rewrite. If a later change fixes one, its pin
 * reddens and says which row to strike.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("The welcome letter's recorded defects")
class WelcomeLetterDefectsTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    private void serviceWithRealMessages() {
        this.emailService = new EmailServiceImpl(this.mailSender);
        ReflectionTestUtils.setField(this.emailService, "from", "noreply@sasbo.co.za");
        when(this.mailSender.createMimeMessage())
                .thenAnswer((invocation) -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private static Member member() {
        Member member = new Member();
        member.setId(7);
        member.setTitle("Ms");
        member.setFirstNames("Thandi");
        member.setSurname("Mokoena");
        member.setMembershipNumber("M-100002");
        member.setAddress("15 Alice Lane, Sandton");
        member.setPersonalEmail("thandi@example.org");
        member.setPreferredEmail("personal");
        return member;
    }

    /** Send to one member and hand back every text node of the attached letter. */
    private String sentLetter() throws Exception {
        serviceWithRealMessages();
        this.emailService.sendWelcomeLetter(member());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(this.mailSender, times(1)).send(captor.capture());
        return letterText(captor.getValue());
    }

    /**
     * Every {@code <w:t>} of the attached .docx, read from the raw
     * {@code word/document.xml}.
     *
     * <p>🔑 NOT {@code XWPFWordExtractor}: it skips text boxes, and so does the
     * code under test -- a check built on the extractor would agree with the
     * service about exactly the text it cannot see, which is A55's whole shape.
     * Word renders text boxes; a member reads them.
     */
    private static String letterText(MimeMessage message) throws Exception {
        message.saveChanges();
        for (BodyPart part : partsOf((Multipart) message.getContent())) {
            if ("Sasbo Welcome Letter.pdf".equals(part.getFileName())) {
                try (InputStream in = part.getInputStream();
                     org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(in.readAllBytes())) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    return stripper.getText(pdfDoc);
                }
            }
            if (!"Sasbo Welcome Letter.docx".equals(part.getFileName())) {
                continue;
            }
            try (InputStream in = part.getInputStream();
                 ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(in.readAllBytes()))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                    if (!"word/document.xml".equals(entry.getName())) {
                        continue;
                    }
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    StringBuilder text = new StringBuilder();
                    Matcher node = TEXT_NODE.matcher(xml);
                    while (node.find()) {
                        text.append(unescapeXml(node.group(1))).append('\n');
                    }
                    return text.toString();
                }
            }
            throw new AssertionError("the attachment has no word/document.xml");
        }
        throw new AssertionError("the message carries no \"Sasbo Welcome Letter.docx\" attachment");
    }

    private static final Pattern TEXT_NODE = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");

    private static String unescapeXml(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static List<BodyPart> partsOf(Multipart multipart) throws Exception {
        List<BodyPart> parts = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                parts.addAll(partsOf(nested));
            } else {
                parts.add(part);
            }
        }
        return parts;
    }

    @Test
    @DisplayName("fills the placeholders in the letter's BODY: date, name, address and salutation")
    void bodyPlaceholdersAreFilled() throws Exception {
        // Must-pass, and the non-vacuity half of the two below: the member's own values
        // ARE in the letter, so neither A55 test can pass or fail on an empty or
        // unreadable attachment.
        assertThat(sentLetter())
                .contains("Ms Thandi Mokoena")
                .contains("15 Alice Lane, Sandton")
                .doesNotContain(">>>>Date <<<<")
                .doesNotContain(">>Title>>Name>>Name<<")
                .doesNotContain(">>Address Line 1<<<<")
                .doesNotContain(">>>Name<<<");
    }

    @Test
    @Disabled(KnownDefects.WELCOME_LETTER_LEAVES_THE_NUMBER_PLACEHOLDER)
    @DisplayName("🔴 A55: fills EVERY placeholder, including the membership number banner")
    void everyPlaceholderIsFilled() throws Exception {
        assertThat(sentLetter())
                .contains("M-100002")
                .doesNotContain(">>")
                .doesNotContain("<<");
    }

    @Test
    @DisplayName("🔴 A55 still reproduces: the letter says \">>>Number<<\" twice where the number belongs")
    void theNumberPlaceholderStillSurvives() throws Exception {
        String letter = sentLetter();

        assertThat(letter).doesNotContain("M-100002");
    }

    @Test
    @Disabled(KnownDefects.WELCOME_FAILURE_NAMES_THE_RECIPIENT)
    @DisplayName("🔴 B8: a failed send does not put the member's email address in the error")
    void aFailureDoesNotNameTheRecipient() {
        serviceWithRealMessages();
        Member member = member();
        member.setPersonalEmail("thandi at example dot org");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .as("the catch-all handler copies this message into a 500 body on an "
                        + "unauthenticated endpoint, so the address would reach the caller")
                .hasMessageNotContaining("thandi at example dot org");
    }

    @Test
    @DisplayName("🔴 B8 still reproduces: a failed send names the member's email address")
    void aFailureStillNamesTheRecipient() {
        serviceWithRealMessages();
        Member member = member();
        member.setPersonalEmail("thandi at example dot org");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .as("B8 has changed. If the address is no longer in the message, B8's leak "
                        + "half is fixed: enable aFailureDoesNotNameTheRecipient and update the "
                        + "KnownDefects row")
                .hasMessageContaining("thandi at example dot org");
    }
}
