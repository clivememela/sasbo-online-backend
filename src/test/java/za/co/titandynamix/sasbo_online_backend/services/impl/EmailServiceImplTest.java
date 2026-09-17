package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The welcome letter: who it goes to, and what the attached letter says.
 *
 * <p>Arrived on main in {@code 6b2bd0c}..{@code 0c2d373} with no test, which
 * {@code CoverageFloorTest} reported. No SMTP server is involved: the mail
 * sender is a mock that hands out real {@link MimeMessage}s, so every sent
 * message is captured and its attachment is opened with POI, the same library
 * that filled it. The template itself is the real one on the classpath.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl -- the welcome letter")
class EmailServiceImplTest {

    private static final String FROM = "noreply@sasbo.co.za";

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        this.emailService = new EmailServiceImpl(this.mailSender);
        ReflectionTestUtils.setField(this.emailService, "from", FROM);
    }

    private void messagesAreReal() {
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
        member.setWorkEmail("t.mokoena@bank.example");
        return member;
    }

    private List<MimeMessage> sent(int count) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(this.mailSender, times(count)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String recipient(MimeMessage message) throws Exception {
        return message.getAllRecipients()[0].toString();
    }

    /**
     * Every text node of the attached .docx, in document order, read from the raw
     * {@code word/document.xml}.
     *
     * <p>🔑 NOT {@code XWPFWordExtractor}: it skips text boxes, and so does the code
     * under test -- a check built on the extractor would agree with the service about
     * exactly the text it cannot see. Word renders text boxes, so this reads them.
     */
    private static String letterText(MimeMessage message) throws Exception {
        message.saveChanges();
        Multipart outer = (Multipart) message.getContent();
        for (BodyPart part : partsOf(outer)) {
            if ("Sasbo Welcome Letter.docx".equals(part.getFileName())) {
                try (InputStream in = part.getInputStream();
                     ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(in.readAllBytes()))) {
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                        if ("word/document.xml".equals(entry.getName())) {
                            String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                            StringBuilder text = new StringBuilder();
                            Matcher node = TEXT_NODE.matcher(xml);
                            while (node.find()) {
                                text.append(unescapeXml(node.group(1))).append('\n');
                            }
                            return text.toString();
                        }
                    }
                }
                throw new AssertionError("the attachment has no word/document.xml");
            }
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
    @DisplayName("sends to the personal address when that is the member's preference")
    void preferredPersonal() throws Exception {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail("personal");

        this.emailService.sendWelcomeLetter(member);

        MimeMessage message = sent(1).get(0);
        assertThat(recipient(message)).isEqualTo("thandi@example.org");
        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
    }

    @Test
    @DisplayName("sends one message to EACH address when the preference is both")
    void preferredBoth() throws Exception {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail("both");

        this.emailService.sendWelcomeLetter(member);

        assertThat(sent(2)).extracting(EmailServiceImplTest::recipientUnchecked)
                .containsExactly("thandi@example.org", "t.mokoena@bank.example");
    }

    @Test
    @DisplayName("falls back to the work address when there is no preference and no personal address")
    void fallsBackToWork() throws Exception {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail(null);
        member.setPersonalEmail("  ");

        this.emailService.sendWelcomeLetter(member);

        assertThat(recipient(sent(1).get(0))).isEqualTo("t.mokoena@bank.example");
    }

    @Test
    @DisplayName("refuses, and sends nothing, when the member has no address at all")
    void noAddressIsRefused() {
        Member member = member();
        member.setPersonalEmail(null);
        member.setWorkEmail("");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(this.mailSender, never()).send(any(MimeMessage.class));
    }

    private String sentLetter() throws Exception {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail("personal");
        this.emailService.sendWelcomeLetter(member);
        return letterText(sent(1).get(0));
    }

    @Test
    @DisplayName("fills the placeholders in the letter's BODY: date, name, address and salutation")
    void bodyPlaceholdersAreFilled() throws Exception {
        // Must-pass: the member's own values ARE in the letter, so the two tests below
        // cannot pass or fail on an empty or unreadable attachment.
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

        assertThat(letter.split(">>>Number<<", -1).length - 1)
                .as("A55 has changed. If this is 0 and the number is in the letter, A55 is fixed: "
                        + "enable everyPlaceholderIsFilled and delete the KnownDefects row. The "
                        + "banner sits in a TEXT BOX, which doc.getParagraphs() and doc.getTables() "
                        + "never reach")
                .isEqualTo(2);
        assertThat(letter).doesNotContain("M-100002");
    }

    @Test
    @Disabled(KnownDefects.WELCOME_FAILURE_NAMES_THE_RECIPIENT)
    @DisplayName("🔴 B8: a failed send does not put the member's email address in the error")
    void aFailureDoesNotNameTheRecipient() {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail("personal");
        member.setPersonalEmail("thandi at example dot org");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .as("the catch-all handler copies this message into a 500 body on an "
                        + "unauthenticated endpoint, so the address would reach the caller")
                .hasMessageNotContaining("thandi at example dot org");
    }

    @Test
    @DisplayName("🔴 B8 still reproduces: a failed send names the member's email address")
    void aFailureStillNamesTheRecipient() {
        messagesAreReal();
        Member member = member();
        member.setPreferredEmail("personal");
        member.setPersonalEmail("thandi at example dot org");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .as("B8 has changed. If the address is no longer in the message, B8's leak "
                        + "half is fixed: enable aFailureDoesNotNameTheRecipient and update the "
                        + "KnownDefects row")
                .hasMessageContaining("thandi at example dot org");
    }

    private static String recipientUnchecked(MimeMessage message) {
        try {
            return recipient(message);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
