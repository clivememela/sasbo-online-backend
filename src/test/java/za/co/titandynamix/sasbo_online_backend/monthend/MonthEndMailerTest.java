package za.co.titandynamix.sasbo_online_backend.monthend;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-mailing a region's pack (October build; Stephne MEN-04): mail is set up only where a mail server, its user and a
 * from-address all are -- the application's own rule -- and a message carries the pack's files as attachments, to the
 * recipient's address alone.
 */
@DisplayName("The mailer")
class MonthEndMailerTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> providing(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    @DisplayName("set up only with a mail server, a user and a from-address -- any one missing, it is not")
    void configured() {
        JavaMailSender sender = mock(JavaMailSender.class);
        assertThat(new MonthEndMailer(providing(null), "invented.user", "noreply@example.org").configured()).isFalse();
        assertThat(new MonthEndMailer(providing(sender), " ", "noreply@example.org").configured()).isFalse();
        assertThat(new MonthEndMailer(providing(sender), "invented.user", "").configured()).isFalse();
        assertThat(new MonthEndMailer(providing(sender), "invented.user", "noreply@example.org").configured()).isTrue();
    }

    @Test
    @DisplayName("a message to the recipient alone, from the server's address, with the pack's files attached")
    void aMessageWithItsAttachments() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        MonthEndMailer mailer = new MonthEndMailer(providing(sender), "invented.user", "noreply@example.org");

        mailer.send("regional.secretary.jhb@example.org", "SASBO month-end pack", "Dear Regional Secretary",
                List.of(new MonthEndMailer.Attachment("sasbo-month-end-2026-09-JHB.pdf", "application/pdf",
                        new byte[] {'%', 'P', 'D', 'F'})));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("regional.secretary.jhb@example.org");
        assertThat(message.getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@example.org");
        assertThat(message.getSubject()).isEqualTo("SASBO month-end pack");
        assertThat(attachments(message)).containsExactly("sasbo-month-end-2026-09-JHB.pdf");
    }

    @Test
    @DisplayName("asked to send where mail is not set up, it refuses and sends nothing")
    void refusesWhereNotSetUp() {
        MonthEndMailer mailer = new MonthEndMailer(providing(null), "", "");
        assertThatThrownBy(() -> mailer.send("someone@example.org", "s", "b", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an attachment never prints its bytes")
    void anAttachmentPrintsNoBytes() {
        assertThat(new MonthEndMailer.Attachment("a.pdf", "application/pdf", new byte[] {'Q', 'X'}).toString())
                .isEqualTo("Attachment[a.pdf, 2 bytes]");
    }

    private static List<String> attachments(Part part) throws Exception {
        List<String> names = new ArrayList<>();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) {
                    names.add(child.getFileName());
                } else {
                    names.addAll(attachments(child));
                }
            }
        }
        return names;
    }
}
