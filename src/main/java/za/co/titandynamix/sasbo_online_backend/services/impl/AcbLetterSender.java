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
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup;
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

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/**
 * Emails the union's debit order letters (ACB {@code 03.3}; D23: "it was posted before, now we need it emailed") --
 * a job, never a request thread, each letter sent once.
 *
 * <h2>From members@sasbo.org.za, under the letter's own subject</h2>
 * Stephne named the address the letters come from and members reply to (DOD-34); it is the default of
 * {@code acb.letters.from}, shown as "SASBO Membership Administration" -- a position, never a person (DOD-35). The
 * e-mail's subject is the letter's subject line, so a member sees what it is about before opening it.
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
 * changed in between is not sent a letter nobody recorded. That letter FAILS, saying why, for an administrator --
 * and so does one whose PDF cannot be made (a character no letter font prints): {@code LETTER_NOT_RENDERABLE}.
 *
 * <h2>Once</h2>
 * Each letter is CLAIMED by a conditional update on its attempt count, so two senders never both send it, and it
 * is sent only while RENDERED -- never again once SENT (R-ROB-G, the A50 lesson).
 *
 * <h2>A letter that does not go is seen, and sent again by a person</h2>
 * A delivery that fails is FAILED at once, with its failure's class -- never the message, which can carry the
 * address -- beside the attempt its claim counted (EH-E, R3-7). It waits on the run's held-letters worklist, where
 * an administrator sends it again (the next claim makes the count 2) or marks it handled. Nothing here retries it
 * unseen: the sender runs when an unpaid is recorded, so a quiet retry could wait for days looking pending. Each
 * run of the sender writes one INFO line of counts, never a line per letter.
 */
@Slf4j
@Component
public class AcbLetterSender {

    /** The actors whose letters are never sent: the anonymous API, and the seed. */
    static final Set<String> NEVER_SENT_FOR = Set.of(AcbActor.SYSTEM, "seed");

    /** Where the letters come from and members reply to (Stephne, DOD-34). */
    static final String DEFAULT_FROM = "members@sasbo.org.za";
    /** The name shown beside it: a position, never a person (DOD-35). */
    static final String FROM_NAME = "SASBO Membership Administration";
    /** The e-mail's subject when a letter has no subject line of its own. */
    static final String SUBJECT = "A letter from SASBO about your debit order";
    static final String ATTACHMENT = "SASBO debit order letter.pdf";
    static final String COVER_NOTE = """
            Please find attached a letter from SASBO about your subscription debit order.

            If you have any questions, reply to this e-mail or call us on (011) 467-0192.

            Membership Administration
            SASBO, The Finance Union
            """;

    private final AcbLetterRepository letters;
    private final AcbRunLineRepository lines;
    private final AcbRunRepository runs;
    private final AcbReturnsRepository returns;
    private final AcbUnpaidCodeRepository codes;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectProvider<JavaMailSender> mail;
    private final LetterDocument documents;
    private final boolean enabled;
    private final String from;

    public AcbLetterSender(AcbLetterRepository letters, AcbRunLineRepository lines, AcbRunRepository runs,
                           AcbReturnsRepository returns, AcbUnpaidCodeRepository codes, JdbcTemplate jdbc, Clock clock,
                           ObjectProvider<JavaMailSender> mail, LetterDocument documents,
                           @Value("${acb.letters.send-enabled:false}") boolean enabled,
                           @Value("${acb.letters.from:" + DEFAULT_FROM + "}") String from) {
        this.letters = letters;
        this.lines = lines;
        this.runs = runs;
        this.returns = returns;
        this.codes = codes;
        this.jdbc = jdbc;
        this.clock = clock;
        this.mail = mail;
        this.documents = documents;
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
            log.warn("Debit order letters: sending is switched on, but no mail server or from-address is configured "
                    + "-- nothing sent");
            return 0;
        }
        int due = 0;
        int sent = 0;
        for (AcbLetter letter : letters.findByStatusAndChannel(LetterStatus.RENDERED, LetterChannel.EMAIL)) {
            if (letter.getQueuedBy() == null || NEVER_SENT_FOR.contains(letter.getQueuedBy())) {
                continue;
            }
            if (!claim(letter)) {
                continue;
            }
            due++;
            if (deliver(letter, sender)) {
                sent++;
            }
        }
        if (due > 0) {
            // One line per run, never one per letter -- and counts only: no address, no name (EH-E).
            log.info("Debit order letters: {} sent, {} failed, of {} due", sent, due - sent, due);
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
            fail(letter, "LETTER_NOT_RENDERABLE: " + e.getClass().getSimpleName());
            return false;
        }
        if (!LetterRenderer.bodySha256(body).equals(letter.getBodySha256())) {
            // The member's details moved since the letter was recorded: it is not the letter anybody recorded.
            fail(letter, "LETTER_CHANGED_SINCE_RENDERED");
            return false;
        }
        byte[] pdf;
        try {
            pdf = documents.render(body);
        } catch (RuntimeException e) {
            // A character no letter font prints, above all: FAILED where a person sees it, never retried unseen.
            fail(letter, "LETTER_NOT_RENDERABLE: " + e.getClass().getSimpleName());
            return false;
        }
        String subject = LetterMarkup.subjectOf(body);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, FROM_NAME);
            helper.setReplyTo(from, FROM_NAME);
            helper.setTo(letter.getAddressSnapshot());
            helper.setSubject(subject == null ? SUBJECT : subject);
            helper.setText(COVER_NOTE, false);
            helper.addAttachment(ATTACHMENT, new ByteArrayDataSource(pdf, "application/pdf"));
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            fail(letter, e.getClass().getSimpleName());
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

    /** A letter that did not go: FAILED at once, with its failure's class, for a person to see (EH-E, R3-7). */
    private void fail(AcbLetter letter, String failure) {
        jdbc.update("UPDATE acb_letters SET status = 'FAILED', failure_class = ? WHERE id = ? AND status = 'RENDERED'",
                failure.length() > 200 ? failure.substring(0, 200) : failure, letter.getId());
    }
}
