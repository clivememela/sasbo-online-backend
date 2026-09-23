package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The register rows the welcome letter carries: <b>A55</b> (a member's membership
 * number never reaches their letter), <b>A66</b> (the PDF drops most of the
 * template), <b>A68</b> (the PDF cannot write every letter of a name) and
 * <b>B8</b>'s leak half (a failed send names the recipient's address).
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
 * <p>So the pins live BESIDE the incumbent's file rather than inside it. Two
 * files, two subjects: his covers who the letter goes to; this covers what the
 * letter SAYS and what a failure discloses.
 *
 * <h2>🔴 Since server 99936a5 the letter is a PDF, and that moved two rows</h2>
 * {@code convertDocxToPdf} rebuilds the letter from {@code doc.getParagraphs()}
 * alone, so the template's six TEXT BOXES -- the membership-number banner and the
 * whole onboarding page -- and its sixteen images are not in what the member
 * receives (A66). That is also why A55's {@code >>>Number<<} stopped appearing:
 * the banner holding it is gone. `99936a5` deleted this file's count of that
 * placeholder rather than re-aiming it; the pins below say what is true now.
 *
 * <p>🔑 The letter is read the way a member reads it: {@code PDFTextStripper}
 * over the ATTACHED bytes, whitespace collapsed so a line break inside a sentence
 * cannot decide a result. (The old raw-{@code document.xml} reading existed
 * because POI's extractor skips text boxes, as the service did; a PDF has no text
 * boxes to skip -- what is drawn is what there is.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("The welcome letter's recorded defects")
class WelcomeLetterDefectsTest {

    /**
     * Text that sits in the template's text boxes -- the banner and the onboarding
     * page -- measured from the template at server main 99936a5.
     */
    private static final List<String> TEMPLATE_TEXT_BOXES = List.of(
            "Your Sasbo Membership No",
            "MEMBER ONBOARDING",
            "Value added Benefits",
            "Congratulations and welcome to Sasbo");

    /**
     * A surname spelled with ḓ (U+1E13), a letter of Tshivenḓa -- one of South
     * Africa's official languages -- and absent from the WinAnsi encoding of the
     * Standard-14 Times-Roman the PDF is drawn in. Escaped, so the source file's
     * encoding cannot decide the test.
     */
    private static final String TSHIVENDA_SURNAME = "Nemaḓaḓa";

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

    /** Send to one member and hand back the PDF attached to the one message sent. */
    private byte[] sentLetterPdf() throws Exception {
        serviceWithRealMessages();
        this.emailService.sendWelcomeLetter(member());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(this.mailSender, times(1)).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();

        List<String> seen = new ArrayList<>();
        for (BodyPart part : partsOf((Multipart) message.getContent())) {
            if ("Sasbo Welcome Letter.pdf".equals(part.getFileName())) {
                try (InputStream in = part.getInputStream()) {
                    return in.readAllBytes();
                }
            }
            seen.add(String.valueOf(part.getFileName()));
        }
        throw new AssertionError("the message carries no \"Sasbo Welcome Letter.pdf\" -- its parts: " + seen);
    }

    private String sentLetter() throws Exception {
        return textOf(sentLetterPdf());
    }

    /** Every word drawn on every page, whitespace collapsed to single spaces. */
    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    /** How many images the pages draw from their resources. */
    private static int imagesIn(byte[] pdf) throws IOException {
        int images = 0;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.isImageXObject(name)) {
                        images++;
                    }
                }
            }
        }
        return images;
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
        // Must-pass, and the non-vacuity half of everything below: the member's own
        // values ARE in the letter, so no pin here can pass on an empty or unreadable
        // attachment.
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
    @DisplayName("🔴 A55 still reproduces: a member WITH a membership number is sent a letter without it")
    void aMemberWithANumberIsSentALetterWithoutIt() throws Exception {
        String letter = sentLetter();

        assertThat(letter)
                .as("A55 has changed: the member's number is in the letter. If every placeholder is "
                        + "filled, enable everyPlaceholderIsFilled and strike the row")
                .doesNotContain("M-100002");
        assertThat(letter)
                .as("the SHAPE has changed: since 99936a5 the banner is not in the PDF at all (A66), so "
                        + "neither is its placeholder. If \">>>Number<<\" is back, so is the banner -- "
                        + "re-read A55 and A66 together")
                .doesNotContain(">>>Number<<");
    }

    @Test
    @Disabled(KnownDefects.WELCOME_LETTER_PDF_DROPS_THE_TEMPLATE)
    @DisplayName("🔴 A66: the PDF carries the template's banner, its onboarding page and its images")
    void thePdfCarriesTheWholeTemplate() throws Exception {
        byte[] pdf = sentLetterPdf();

        assertThat(textOf(pdf)).contains(TEMPLATE_TEXT_BOXES);
        assertThat(imagesIn(pdf)).as("the letterhead and the onboarding page's pictures").isPositive();
    }

    @Test
    @DisplayName("🔴 A66 still reproduces: the PDF has no banner, no onboarding page and no image")
    void thePdfStillDropsTheTemplate() throws Exception {
        byte[] pdf = sentLetterPdf();
        String text = textOf(pdf);

        // The body IS there -- the letter still begins as the template does -- so the
        // absences below cannot be an empty PDF's.
        assertThat(text).contains("Welcome to Sasbo, thank you for joining");
        for (String box : TEMPLATE_TEXT_BOXES) {
            assertThat(text)
                    .as("A66 has changed: \"%s\" is in the PDF now. If every text box and image is, "
                            + "enable thePdfCarriesTheWholeTemplate and strike the row", box)
                    .doesNotContain(box);
        }
        assertThat(imagesIn(pdf)).as("A66 has changed: the PDF draws an image now").isZero();
    }

    @Test
    @Disabled(KnownDefects.WELCOME_LETTER_PDF_CANNOT_WRITE_SOME_NAMES)
    @DisplayName("🔴 A68: a member whose name holds a letter outside Latin-1 still gets their letter")
    void aNameOutsideLatin1IsWritten() throws Exception {
        Member member = member();
        member.setSurname(TSHIVENDA_SURNAME);

        assertThat(textOf(new EmailServiceImpl(this.mailSender).buildWelcomeLetterPdf(member)))
                .contains(TSHIVENDA_SURNAME);
    }

    @Test
    @DisplayName("🔴 A68 still reproduces: a name the PDF's font cannot encode aborts the letter")
    void aNameOutsideLatin1StillThrows() {
        Member member = member();
        member.setSurname(TSHIVENDA_SURNAME);

        // Since 99936a5 the capture IS the send (A64), so this throw is not only a
        // lost letter: POST /api/members?sendWelcome=true rolls the member back.
        assertThatThrownBy(() -> new EmailServiceImpl(this.mailSender).buildWelcomeLetterPdf(member))
                .as("A68 has changed: the letter was written. If the surname is in it, enable "
                        + "aNameOutsideLatin1IsWritten and strike the row")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not available");
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
