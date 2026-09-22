package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * The letters, emailed (ACB {@code 03.3}; D23), on PostgreSQL -- sending switched on, as the letters review will, and
 * the mail server a mock that keeps what it is handed. The job launcher runs inline in tests, so the send an unpaid's
 * recording triggers has happened by the time {@code recordUnpaid} returns.
 */
@TestPropertySource(properties = {"acb.letters.send-enabled=true", "mail.from=noreply@example.org"})
@DisplayName("The letter sender, on PostgreSQL")
class AcbLetterSenderIT extends AbstractAcbIT {

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
    private static final BigDecimal RATE = new BigDecimal("174.00");
    private static final String ADDRESS = "synthetic.member@example.org";

    @MockitoBean
    private JavaMailSender mail;

    @Autowired
    private AcbReturnsService returns;

    @Autowired
    private AcbLetterSender sender;

    @Autowired
    private AcbLetterRepository letters;

    private final List<MimeMessage> sent = new ArrayList<>();

    @BeforeEach
    void theMailbox() {
        sent.clear();
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(mail).send(any(MimeMessage.class));
    }

    /** An unpaid recorded by {@code actor} for the fixture's first member, who has an address: its letter RENDERED. */
    private long unpaidRecordedBy(String actor) {
        AcbRunFixture.insert(jdbc, 15, 2);
        int member = AcbRunFixture.FIRST_MEMBER_ID;
        jdbc.update("UPDATE members SET personal_email = ?, preferred_email = 'personal' WHERE id = ?", ADDRESS, member);
        long run = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long line = insertLine(run, member, null, "PENDING", null, RATE);
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), actor);
        return line;
    }

    private static byte[] attachment(Part part) throws Exception {
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

    @Test
    @DisplayName("D23: a named administrator's recording is emailed the same day -- a PDF on the letterhead -- and once")
    void aNamedRecordingIsSentOnce() throws Exception {
        long line = unpaidRecordedBy("administrator");

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly(ADDRESS);
            assertThat(message.getSubject()).isEqualTo(AcbLetterSender.SUBJECT);
            byte[] pdf = attachment(message);
            assertThat(pdf).as("the letter, attached").isNotNull();
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        });
        AcbLetter letter = letters.findByLineId(line).orElseThrow();
        assertThat(letter.getStatus()).isEqualTo(LetterStatus.SENT);
        assertThat(letter.getSentAt()).isNotNull();
        assertThat(letter.getAttempts()).isEqualTo(1);
        assertThat(letter.getQueuedBy()).isEqualTo("administrator");

        // R-ROB-G: never again once SENT.
        assertThat(sender.sendDue()).isZero();
        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("🔒 B1: a letter the anonymous API queued is never sent -- and neither is one queued before V51")
    void anAnonymousRecordingIsNeverSent() {
        long line = unpaidRecordedBy(AcbActor.SYSTEM);

        assertThat(sender.sendDue()).isZero();
        assertThat(sent).isEmpty();
        assertThat(letters.findByLineId(line).orElseThrow().getStatus()).isEqualTo(LetterStatus.RENDERED);

        jdbc.update("UPDATE acb_letters SET queued_by = NULL WHERE line_id = ?", line);
        assertThat(sender.sendDue()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("🔴 R3-7, EH-E: a send that fails is FAILED at once -- its class, never its message, attempt 1 -- on the worklist; sent again, it counts 2 and goes once")
    void aFailedSendIsSeenThenSentAgain() {
        doThrow(new MailSendException("refused for member@example.org")).when(mail).send(any(MimeMessage.class));

        long line = unpaidRecordedBy("administrator");

        AcbLetter failed = letters.findByLineId(line).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(LetterStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getFailureClass()).as("the class, never the message").isEqualTo("MailSendException");
        assertThat(sender.sendDue()).as("nothing tries a FAILED letter again unseen").isZero();
        long run = jdbc.queryForObject("SELECT run_id FROM acb_run_lines WHERE id = ?", Long.class, line);
        assertThat(returns.heldLetters(run, 0, 50).content()).singleElement().satisfies(held -> {
            assertThat(held.status()).isEqualTo(LetterStatus.FAILED);
            assertThat(held.reason()).isEqualTo("MailSendException");
            assertThat(held.canSend()).isTrue();
        });

        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(mail).send(any(MimeMessage.class));
        returns.sendHeldLetter(run, failed.getId(), "administrator");

        AcbLetter again = letters.findByLineId(line).orElseThrow();
        assertThat(again.getStatus()).isEqualTo(LetterStatus.SENT);
        assertThat(again.getAttempts()).as("a retry makes it 2").isEqualTo(2);
        assertThat(again.getFailureClass()).isNull();
        assertThat(sent).hasSize(1);
        assertThat(returns.heldLetters(run, 0, 50).content()).isEmpty();
    }

    @Test
    @DisplayName("the letter sent is the letter recorded: a member whose details moved since is not sent another")
    void aChangedLetterIsNotSent() {
        // The first attempt fails, so the letter is still waiting when the member moves house.
        doThrow(new MailSendException("down"))
                .doAnswer(invocation -> sent.add(invocation.getArgument(0)))
                .when(mail).send(any(MimeMessage.class));
        long line = unpaidRecordedBy("administrator");
        jdbc.update("UPDATE members SET address = '9 Moved Street' WHERE id = ?", AcbRunFixture.FIRST_MEMBER_ID);

        assertThat(sender.sendDue()).isZero();

        assertThat(sent).isEmpty();
        AcbLetter letter = letters.findByLineId(line).orElseThrow();
        assertThat(letter.getStatus()).isEqualTo(LetterStatus.FAILED);
        assertThat(letter.getFailureClass()).isEqualTo("LETTER_CHANGED_SINCE_RENDERED");
    }
}
