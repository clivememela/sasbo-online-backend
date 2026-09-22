package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterDocument;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRenderer;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/**
 * Emails the union's letters (ACB {@code 03.3}; D23: "it was posted before, now we need it emailed") -- a job,
 * never a request thread, each letter sent once.
 *
 * <h2>What it sends, and what it never sends</h2>
 * A letter RENDERED for an email address, queued by a named administrator, while sending is switched on
 * ({@code acb.letters.send-enabled}, off by default). So nothing leaves while:
 * <ul>
 *   <li>SASBO has not agreed the letters -- "nothing is sent to a member before" the letters review they asked
 *       for (question 32 c), and the switch is what the review turns on;</li>
 *   <li>the API is anonymous (register B1, D13, invariant 431): every unpaid recorded through it is queued by
 *       {@code system}, and a letter an anonymous caller caused is never sent, even after sign-in arrives;</li>
 *   <li>the seed wrote the letter ({@code seed}), which writes its letters already sent (F62);</li>
 *   <li>no mail server is configured, or no from-address.</li>
 * </ul>
 *
 * <h2>The letter sent is the letter recorded</h2>
 * The body is never stored -- its SHA-256 and the address are (R-SEC-C). So the sender renders it again from the
 * same line, on the date it was first rendered, and sends it only if the hash matches: a member whose details
 * changed in between is not sent a letter nobody recorded. That letter FAILS, saying why, for an administrator.
 *
 * <h2>Once</h2>
 * Each letter is CLAIMED by a conditional update on its attempt count, so two senders never both send it, and it
 * is sent only while RENDERED -- never again once SENT (R-ROB-G, the A50 lesson). A delivery that fails is tried
 * again on the next run, up to {@link #MAX_ATTEMPTS}, and then left FAILED with its failure's class.
 */
@Slf4j
@Component
public class AcbLetterSender {

    static final int MAX_ATTEMPTS = 3;

    /** The actors whose letters are never sent: the anonymous API, and the seed. */
    static final Set<String> NEVER_SENT_FOR = Set.of(AcbActor.SYSTEM, "seed");

    static final String SUBJECT = "SASBO: your union subscription debit order";
    static final String ATTACHMENT = "SASBO letter.pdf";
    static final String COVER_NOTE = """
            Please find attached a letter from SASBO about your union subscription debit order.

            SASBO Administration
            """;

    private final AcbLetterRepository letters;
    private final AcbRunLineRepository lines;
    private final AcbRunRepository runs;
    private final AcbReturnsRepository returns;
    private final AcbUnpaidCodeRepository codes;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean enabled;
    private final String from;

    public AcbLetterSender(AcbLetterRepository letters, AcbRunLineRepository lines, AcbRunRepository runs,
                           AcbReturnsRepository returns, AcbUnpaidCodeRepository codes, JdbcTemplate jdbc, Clock clock,
                           ObjectProvider<JavaMailSender> mail,
                           @Value("${acb.letters.send-enabled:false}") boolean enabled,
                           @Value("${mail.from:}") String from) {
        this.letters = letters;
        this.lines = lines;
        this.runs = runs;
        this.returns = returns;
        this.codes = codes;
        this.jdbc = jdbc;
        this.clock = clock;
        this.mail = mail;
        this.enabled = enabled;
        this.from = from;
    }

    /** Sends every letter due, once each; answers how many were sent. */
    public int sendDue() {
        if (!enabled) {
            return 0;
        }
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from == null || from.isBlank()) {
            log.warn("ACB letters: sending is switched on, but no mail server or from-address is configured -- "
                    + "nothing sent");
            return 0;
        }
        int sent = 0;
        for (AcbLetter letter : letters.findByStatusAndChannel(LetterStatus.RENDERED, LetterChannel.EMAIL)) {
            if (letter.getQueuedBy() == null || NEVER_SENT_FOR.contains(letter.getQueuedBy())) {
                continue;
            }
            if (claim(letter) && deliver(letter, sender)) {
                sent++;
            }
        }
        return sent;
    }

    /** Takes the letter for this run: one more attempt, only if nobody else has taken it since it was read. */
    private boolean claim(AcbLetter letter) {
        return jdbc.update("UPDATE acb_letters SET attempts = attempts + 1 "
                + "WHERE id = ? AND status = 'RENDERED' AND attempts = ?", letter.getId(), letter.getAttempts()) == 1;
    }

    private boolean deliver(AcbLetter letter, JavaMailSender sender) {
        String body;
        try {
            body = renderAgain(letter);
        } catch (RuntimeException e) {
            fail(letter, "LETTER_NOT_RENDERABLE: " + e.getClass().getSimpleName(), true);
            return false;
        }
        if (!LetterRenderer.bodySha256(body).equals(letter.getBodySha256())) {
            // The member's details moved since the letter was recorded: it is not the letter anybody recorded.
            fail(letter, "LETTER_CHANGED_SINCE_RENDERED", true);
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(letter.getAddressSnapshot());
            helper.setSubject(SUBJECT);
            helper.setText(COVER_NOTE, false);
            helper.addAttachment(ATTACHMENT, new ByteArrayDataSource(LetterDocument.render(body), "application/pdf"));
            sender.send(message);
        } catch (MailException | MessagingException e) {
            fail(letter, e.getClass().getSimpleName(), false);
            return false;
        }
        jdbc.update("UPDATE acb_letters SET status = 'SENT', sent_at = ?, failure_class = NULL "
                + "WHERE id = ? AND status = 'RENDERED'", Timestamp.from(AcbTime.instant(clock)), letter.getId());
        return true;
    }

    /** The letter's text again, from its line, on the date it was first rendered. */
    private String renderAgain(AcbLetter letter) {
        AcbRunLine line = lines.findById(letter.getLineId()).orElseThrow();
        AcbRun run = runs.findById(line.getRunId()).orElseThrow();
        LetterRecipient recipient = returns.findRecipient(line.getMemberId()).orElseThrow();
        UnpaidReason reason = UnpaidReason.valueOf(line.getUnpaidReason());
        String wording = codes.findById(line.getUnpaidCode()).map(AcbUnpaidCode::getWording).orElse(reason.wording());
        LocalDate rendered = LocalDate.ofInstant(letter.getRenderedAt(), AcbTime.ZONE);
        return LetterRenderer.render(letter.getTemplate(), LetterRenderer.dataFor(rendered, run, line, recipient, wording));
    }

    /** A failed delivery: said on the letter, FAILED at once when retrying cannot help, or after the last attempt. */
    private void fail(AcbLetter letter, String failure, boolean final_) {
        jdbc.update("UPDATE acb_letters SET failure_class = ?, "
                        + "status = CASE WHEN ? OR attempts >= ? THEN 'FAILED' ELSE status END "
                        + "WHERE id = ? AND status = 'RENDERED'",
                failure.length() > 200 ? failure.substring(0, 200) : failure, final_, MAX_ATTEMPTS, letter.getId());
        log.warn("ACB letter {} not sent: {}", letter.getId(), failure);
    }
}
