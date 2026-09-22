package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The sender's switches (ACB {@code 03.3}): off by default, because SASBO asked that nothing reach a member before the
 * letters review (question 32 c) -- and inert without a mail server. The integration test has the rest.
 */
@DisplayName("AcbLetterSender, switched off and unconfigured")
class AcbLetterSenderTest {

    private final AcbLetterRepository letters = mock(AcbLetterRepository.class);
    private final AcbRunLineRepository lines = mock(AcbRunLineRepository.class);
    private final AcbRunRepository runs = mock(AcbRunRepository.class);
    private final AcbReturnsRepository returns = mock(AcbReturnsRepository.class);
    private final AcbUnpaidCodeRepository codes = mock(AcbUnpaidCodeRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);

    private AcbLetterSender sender(boolean enabled, String from) {
        return new AcbLetterSender(letters, lines, runs, returns, codes, jdbc, Clock.systemUTC(), mail, enabled, from);
    }

    @Test
    @DisplayName("🔒 switched off -- the default until the letters review -- it sends nothing and reads nothing")
    void offSendsNothing() {
        assertThat(sender(false, "noreply@example.org").sendDue()).isZero();
        verifyNoInteractions(letters, lines, runs, returns, codes, jdbc, mail);
    }

    @Test
    @DisplayName("switched on with no mail server, or no from-address, it sends nothing and reads no letter")
    void onWithoutMailSendsNothing() {
        when(mail.getIfAvailable()).thenReturn(null);
        assertThat(sender(true, "noreply@example.org").sendDue()).isZero();

        when(mail.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        assertThat(sender(true, " ").sendDue()).isZero();

        verifyNoInteractions(letters, lines, runs, returns, codes, jdbc);
    }
}
