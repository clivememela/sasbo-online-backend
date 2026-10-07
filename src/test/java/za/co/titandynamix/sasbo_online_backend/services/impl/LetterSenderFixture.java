package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterDocument;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRenderer;
import za.co.titandynamix.sasbo_online_backend.documents.LetterTypeface;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The letter sender with its repositories mocked and a real renderer: one RENDERED letter for an invented member's
 * unpaid (the bank's code 2, September 2026), its hash true, queued by an administrator.
 */
final class LetterSenderFixture {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T07:30:00Z"), ZoneOffset.UTC);

    final AcbLetterRepository letters = mock(AcbLetterRepository.class);
    final AcbRunLineRepository lines = mock(AcbRunLineRepository.class);
    final AcbRunRepository runs = mock(AcbRunRepository.class);
    final AcbReturnsRepository returns = mock(AcbReturnsRepository.class);
    final AcbUnpaidCodeRepository codes = mock(AcbUnpaidCodeRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
    final LetterDocument documents = new LetterDocument(LetterTypeface.bundled(), CLOCK);

    AcbLetterSender sender(boolean enabled, String from) {
        return new AcbLetterSender(letters, lines, runs, returns, codes, jdbc, CLOCK, mail, documents, enabled, from);
    }

    /** A mail server that keeps what it is handed, and claims and updates that succeed. */
    JavaMailSender mailServer() {
        JavaMailSender server = mock(JavaMailSender.class);
        when(server.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        when(mail.getIfAvailable()).thenReturn(server);
        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
        return server;
    }

    /** The letter, for a member called {@code surname} at {@code address} with account {@code account}. */
    AcbLetter renderedLetter(String surname, String street, String account, String email) {
        AcbRun run = new AcbRun();
        run.setId(4L);
        run.setActionMonth(LocalDate.of(2026, 9, 1));
        AcbRunLine line = new AcbRunLine();
        line.setId(9L);
        line.setRunId(4L);
        line.setMemberId(7);
        line.setInitials("Z");
        line.setSurname(surname);
        line.setMembershipNumber("M-099116");
        line.setAccountNumber(account);
        line.setBankName("Alpha Bank");
        line.setDebitDay(25);
        line.setAmount(new BigDecimal("174.00"));
        line.setActionDate(LocalDate.of(2026, 9, 25));
        line.setUnpaidCode("2");
        line.setUnpaidReason("NOT_PROVIDED_FOR");
        LetterRecipient recipient = new LetterRecipient(7, "Ms", street + ", Testville", email, null, "personal", "L");

        AcbLetter letter = new AcbLetter();
        letter.setId(31L);
        letter.setLineId(9L);
        letter.setTemplate(LetterTemplate.RE_DEBIT);
        letter.setStatus(LetterStatus.RENDERED);
        letter.setChannel(LetterChannel.EMAIL);
        letter.setAttempts(0);
        letter.setQueuedBy("administrator");
        letter.setAddressSnapshot(email);
        letter.setRenderedAt(CLOCK.instant());
        String body = LetterRenderer.render(LetterTemplate.RE_DEBIT, LetterRenderer.dataFor(AcbTime.today(CLOCK), run,
                line, recipient, "Not Provided For"));
        letter.setBodySha256(LetterRenderer.bodySha256(body));

        when(letters.findByStatusAndChannel(LetterStatus.RENDERED, LetterChannel.EMAIL)).thenReturn(List.of(letter));
        when(lines.findById(9L)).thenReturn(Optional.of(line));
        when(runs.findById(4L)).thenReturn(Optional.of(run));
        when(returns.findRecipient(7)).thenReturn(Optional.of(recipient));
        when(codes.findById("2")).thenReturn(Optional.empty());
        return letter;
    }
}
