package za.co.titandynamix.sasbo_online_backend.monthend;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * E-mails a region's month-end pack to its recipient (October build; Stephne MEN-04: "We need to have the options" --
 * download, or the system e-mails each recipient, chosen each month).
 *
 * <p>MAIL MAY NOT BE SET UP ON A HOST, and that is never a failure of the month: {@link #configured()} says whether it
 * is -- the application's own rule, a mail server with a user ({@code MAIL_USERNAME}) and a from-address -- and a
 * caller refuses an e-mail with {@code MAIL_NOT_CONFIGURED} while downloading still works. The mail server is asked for
 * no bean here: it is looked up when a message goes, so this exists on every host.
 */
@Component
public class MonthEndMailer {

    /** One attachment: its name, its media type and its bytes. */
    public record Attachment(String name, String mediaType, byte[] bytes) {

        /** Never the bytes: an attachment names members. */
        @Override
        public String toString() {
            return "Attachment[" + name + ", " + bytes.length + " bytes]";
        }
    }

    private final ObjectProvider<JavaMailSender> mail;
    private final String username;
    private final String from;

    public MonthEndMailer(ObjectProvider<JavaMailSender> mail, @Value("${spring.mail.username:}") String username,
                          @Value("${mail.from:}") String from) {
        this.mail = mail;
        this.username = username == null ? "" : username.trim();
        this.from = from == null ? "" : from.trim();
    }

    /** Whether this server can send mail: a mail server, its user and a from-address are all set. */
    public boolean configured() {
        return mail.getIfAvailable() != null && !username.isEmpty() && !from.isEmpty();
    }

    /**
     * Sends one message. Only where {@link #configured()}.
     *
     * @throws MailException      the mail server refused it or could not be reached
     * @throws MessagingException the message could not be put together
     */
    public void send(String to, String subject, String body, List<Attachment> attachments) throws MessagingException {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || !configured()) {
            throw new IllegalStateException("mail is not set up on this server");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        for (Attachment attachment : attachments) {
            helper.addAttachment(attachment.name(), new ByteArrayDataSource(attachment.bytes(), attachment.mediaType()));
        }
        sender.send(message);
    }
}
