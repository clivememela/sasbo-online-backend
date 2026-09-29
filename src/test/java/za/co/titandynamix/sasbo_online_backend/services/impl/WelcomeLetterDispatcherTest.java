package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.WelcomeLetterOutcome;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 A64 -- the welcome letter a capture asks for, attempted once the member is saved, and reported
 * rather than thrown. What it LOGS is {@code WelcomeLetterDispatcherLogsTest}, which must run alone.
 *
 * <p>Each outcome is paired with the case that must NOT produce it, so a dispatcher that answered one
 * constant cannot pass.
 */
@DisplayName("The welcome letter after a capture (A64)")
class WelcomeLetterDispatcherTest {

    private static final String ADDRESS = "thandi.botha@example.invalid";

    /** Where mail is configured: an {@link EmailService} bean exists. */
    private static WelcomeLetterDispatcher withMail(EmailService mail) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("emailService", mail);
        return new WelcomeLetterDispatcher(beans.getBeanProvider(EmailService.class));
    }

    /** Where it is not -- dev.sasbo.online, the test profiles: no bean at all. */
    private static WelcomeLetterDispatcher withoutMail() {
        return new WelcomeLetterDispatcher(new DefaultListableBeanFactory().getBeanProvider(EmailService.class));
    }

    private static Member member(String personalEmail, String workEmail, String preferred) {
        Member member = new Member();
        member.setId(41);
        member.setSurname("Botha");
        member.setPersonalEmail(personalEmail);
        member.setWorkEmail(workEmail);
        member.setPreferredEmail(preferred);
        return member;
    }

    @Test
    @DisplayName("with mail, a member with an address is sent the letter: SENT")
    void sent() {
        EmailService mail = mock(EmailService.class);
        Member member = member(ADDRESS, null, "personal");

        assertThat(withMail(mail).sendAfterCapture(member)).isEqualTo(WelcomeLetterOutcome.SENT);
        verify(mail).sendWelcomeLetter(member);
    }

    @Test
    @DisplayName("without mail, nothing is attempted and nothing is thrown: EMAIL_OFF")
    void emailOff() {
        assertThat(withoutMail().sendAfterCapture(member(ADDRESS, null, "personal")))
                .isEqualTo(WelcomeLetterOutcome.EMAIL_OFF);
    }

    @Test
    @DisplayName("a member with no address is NO_ADDRESS, with mail or without, and the sender is never asked")
    void noAddress() {
        EmailService mail = mock(EmailService.class);
        Member none = member(null, "  ", "both");

        assertThat(withMail(mail).sendAfterCapture(none)).isEqualTo(WelcomeLetterOutcome.NO_ADDRESS);
        assertThat(withoutMail().sendAfterCapture(none)).isEqualTo(WelcomeLetterOutcome.NO_ADDRESS);
        verify(mail, never()).sendWelcomeLetter(any());
    }

    @Test
    @DisplayName("🔑 the address rule is the SENDER'S: a work address reaches a member who prefers personal")
    void theFallbackAddressCounts() {
        // The drift-prone case. The sender falls back to whichever address exists when the preference
        // selects none; a capture that read the preference literally would say NO_ADDRESS here for a
        // member the letter could reach. One rule (EmailService.recipientsOf) is what keeps them level.
        EmailService mail = mock(EmailService.class);
        Member workOnly = member(null, ADDRESS, "personal");

        assertThat(EmailService.recipientsOf(workOnly)).containsExactly(ADDRESS);
        assertThat(withMail(mail).sendAfterCapture(workOnly)).isEqualTo(WelcomeLetterOutcome.SENT);
    }

    @Test
    @DisplayName("🔴 a send that throws is FAILED -- reported, never rethrown, so it cannot undo the capture")
    void failedIsReportedNotThrown() {
        EmailService mail = mock(EmailService.class);
        Member member = member(ADDRESS, null, "personal");
        doThrow(new RuntimeException("Failed to send welcome letter to " + ADDRESS + ": 535"))
                .when(mail).sendWelcomeLetter(member);

        assertThat(withMail(mail).sendAfterCapture(member)).isEqualTo(WelcomeLetterOutcome.FAILED);
    }

    @Test
    @DisplayName("emailEnabled answers whether mail is configured, both ways")
    void emailEnabled() {
        assertThat(withMail(mock(EmailService.class)).emailEnabled()).isTrue();
        assertThat(withoutMail().emailEnabled()).isFalse();
    }
}
