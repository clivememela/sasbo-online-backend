package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The sender (ACB {@code 03.3}): off by default, because SASBO asked that nothing reach a member before the letters
 * review (question 32 c), and inert without a mail server -- and, switched on, what a member receives: from
 * members@sasbo.org.za, under the letter's own subject, the letter attached. The integration test has the database.
 */
@DisplayName("AcbLetterSender")
class AcbLetterSenderTest {

    private static final String ADDRESS = "invented.member@example.org";

    private final LetterSenderFixture fixture = new LetterSenderFixture();

    @Test
    @DisplayName("🔒 switched off -- the default until the letters review -- it sends nothing and reads nothing")
    void offSendsNothing() {
        assertThat(fixture.sender(false, AcbLetterSender.DEFAULT_FROM).sendDue()).isZero();
        verifyNoInteractions(fixture.letters, fixture.lines, fixture.runs, fixture.returns, fixture.codes, fixture.jdbc,
                fixture.mail);
    }

    @Test
    @DisplayName("switched on with no mail server, or no from-address, it sends nothing and reads no letter")
    void onWithoutMailSendsNothing() {
        when(fixture.mail.getIfAvailable()).thenReturn(null);
        assertThat(fixture.sender(true, AcbLetterSender.DEFAULT_FROM).sendDue()).isZero();

        when(fixture.mail.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        assertThat(fixture.sender(true, " ").sendDue()).isZero();

        verifyNoInteractions(fixture.letters, fixture.lines, fixture.runs, fixture.returns, fixture.codes, fixture.jdbc);
    }

    @Test
    @DisplayName("DOD-34, DOD-35: a letter goes from members@sasbo.org.za as SASBO Membership Administration, replies to "
            + "the same, under the letter's own subject, with the letter attached")
    void aLetterGoesFromMembersAddress() throws Exception {
        JavaMailSender server = fixture.mailServer();
        AcbLetter letter = fixture.renderedLetter("Exemplé", "12 Invented Street", "6200999911", ADDRESS);

        assertThat(fixture.sender(true, AcbLetterSender.DEFAULT_FROM).sendDue()).isEqualTo(1);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(server).send(sent.capture());
        MimeMessage message = sent.getValue();
        InternetAddress from = new InternetAddress("members@sasbo.org.za", "SASBO Membership Administration");
        assertThat(message.getFrom()).containsExactly(from);
        assertThat(message.getReplyTo()).containsExactly(from);
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly(ADDRESS);
        assertThat(message.getSubject()).isEqualTo("Your debit order for September 2026 was returned unpaid");
        byte[] pdf = attachment(message);
        assertThat(pdf).as("the letter, attached as " + AcbLetterSender.ATTACHMENT).isNotNull();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Dear Ms Exemplé")
                    .contains("Membership Administration");
        }
        verify(fixture.jdbc).update(startsWith("UPDATE acb_letters SET status = 'SENT'"), any(), eq(letter.getId()));
    }

    @Test
    @DisplayName("🔒 A68: a letter no font can print is FAILED as LETTER_NOT_RENDERABLE for a person -- never sent, never "
            + "retried unseen, and the reason names no member")
    void anUnprintableLetterFails() {
        JavaMailSender server = fixture.mailServer();
        // U+4E2D, a CJK character: in neither the typeface nor DejaVu Sans (which has the emoji, so it would print).
        AcbLetter letter = fixture.renderedLetter("Exemplé中", "12 Invented Street", "6200999911", ADDRESS);

        assertThat(fixture.sender(true, AcbLetterSender.DEFAULT_FROM).sendDue()).isZero();

        verify(server, never()).send(any(MimeMessage.class));
        verify(fixture.jdbc).update(startsWith("UPDATE acb_letters SET status = 'FAILED'"),
                eq("LETTER_NOT_RENDERABLE: UnprintableCharacterException"), eq(letter.getId()));
    }

    static byte[] attachment(Part part) throws Exception {
        if (AcbLetterSender.ATTACHMENT.equals(part.getFileName())) {
            return part.getInputStream().readAllBytes();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                byte[] found = attachment(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
