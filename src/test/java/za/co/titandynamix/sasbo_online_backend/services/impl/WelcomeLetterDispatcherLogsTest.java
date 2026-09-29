package za.co.titandynamix.sasbo_online_backend.services.impl;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * What the dispatcher writes to the log when a welcome letter fails: the member's id and the
 * exception's TYPES -- never the address, and never the exception's text, because that text is where
 * the address is (register B8: {@code "Failed to send welcome letter to …"}; an SMTP refusal quotes the
 * recipient back too).
 *
 * <p>{@code @Isolated} for the reason {@code PiiInLogsTest} gives at length: Logback is process-global
 * and Spring Boot resets it whenever a context starts, so a capture running beside a booting slice can
 * come back empty and pass having asserted nothing. The canary proves it did not.
 */
@Isolated
@DisplayName("The welcome letter after a capture -- what a failure logs")
class WelcomeLetterDispatcherLogsTest {

    private static final String ADDRESS = "thandi.botha@example.invalid";

    private LogCapture captured;

    @BeforeEach
    void startCapturing() {
        this.captured = LogCapture.on(WelcomeLetterDispatcher.class);
    }

    @AfterEach
    void stopCapturing() {
        this.captured.close();
    }

    @Test
    @DisplayName("🔒 a failed send is logged at WARN with the member's id and the failure's types, and no address")
    void aFailureNamesTheMemberNotTheAddress() {
        EmailService mail = mock(EmailService.class);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("emailService", mail);
        Member member = new Member();
        member.setId(41);
        member.setPersonalEmail(ADDRESS);
        // The shape EmailServiceImpl.send really throws: the address in the message, the SMTP
        // refusal -- quoting it again -- as the cause.
        doThrow(new RuntimeException("Failed to send welcome letter to " + ADDRESS + ": refused",
                new IllegalStateException("550 5.1.1 <" + ADDRESS + ">: Recipient address rejected")))
                .when(mail).sendWelcomeLetter(member);

        new WelcomeLetterDispatcher(beans.getBeanProvider(EmailService.class)).sendAfterCapture(member);

        String warned = this.captured.renderedAt(Level.WARN);
        assertThat(warned)
                .as("the failure should be logged at WARN, naming the member and what went wrong")
                .contains("member 41")
                .contains("RuntimeException <- IllegalStateException");
        assertThat(this.captured.rendered())
                .as("an email address reached the log (B8). Logs are forwarded and pasted into tickets")
                .doesNotContain(ADDRESS)
                .doesNotContain("Recipient address rejected");
    }

    @Test
    @DisplayName("the capture itself works -- otherwise the assertion above is vacuous")
    void theCaptureActuallyCaptures() {
        this.captured.emitCanary("canary-welcome-letter");

        assertThat(this.captured.rendered()).contains("canary-welcome-letter");
    }
}
