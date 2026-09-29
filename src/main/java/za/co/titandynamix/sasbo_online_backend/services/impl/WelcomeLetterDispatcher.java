package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.WelcomeLetterOutcome;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;

/**
 * The welcome letter a capture asks for, attempted AFTER the member is on record -- and never able to
 * take the capture down with it.
 *
 * <p>🔴 A64. Since server {@code 99936a5} a capture that asked for the letter answered 503 before saving
 * wherever mail was not configured, and sent inside the capture's own transaction everywhere else, so a
 * failed send rolled the member back. On {@code dev.sasbo.online}, which has no mail, nobody could
 * capture anyone. The capture now commits first; this class is only ever handed a member that exists,
 * and it reports what happened to the letter instead of throwing it.
 *
 * <p>{@link EmailService} is optional: {@code EmailServiceImpl} exists only where
 * {@code spring.mail.username} is set. Asked for at each call rather than held, so this bean needs no
 * mail to exist -- which is the whole point.
 */
@Slf4j
@Service
public class WelcomeLetterDispatcher {

    private final ObjectProvider<EmailService> emailService;

    public WelcomeLetterDispatcher(ObjectProvider<EmailService> emailService) {
        this.emailService = emailService;
    }

    /** Whether this server can email a welcome letter at all: mail is configured. */
    public boolean emailEnabled() {
        return this.emailService.getIfAvailable() != null;
    }

    /**
     * Attempts the welcome letter for a member who is ALREADY SAVED, and says what happened.
     *
     * <p>An address is checked first, by the sender's own rule ({@link EmailService#recipientsOf}): a
     * member with none gets {@link WelcomeLetterOutcome#NO_ADDRESS} wherever they are captured, which is a
     * fact about the member rather than about this server.
     *
     * <p>🔒 A failure is logged with the member's id and the exception's TYPE only. The exception's
     * message names the recipient ({@code "Failed to send welcome letter to …"}, register B8), and an SMTP
     * refusal quotes the address back, so no message text goes into the log from here.
     */
    public WelcomeLetterOutcome sendAfterCapture(Member member) {
        if (EmailService.recipientsOf(member).isEmpty()) {
            return WelcomeLetterOutcome.NO_ADDRESS;
        }
        EmailService mail = this.emailService.getIfAvailable();
        if (mail == null) {
            return WelcomeLetterOutcome.EMAIL_OFF;
        }
        try {
            mail.sendWelcomeLetter(member);
            return WelcomeLetterOutcome.SENT;
        } catch (RuntimeException failure) {
            log.warn("The welcome letter for member {} was not sent ({}); the member is saved",
                    member.getId(), causeChain(failure));
            return WelcomeLetterOutcome.FAILED;
        }
    }

    /** "RuntimeException <- AuthenticationFailedException": the types, outermost first, and never a message. */
    private static String causeChain(Throwable failure) {
        StringBuilder chain = new StringBuilder(failure.getClass().getSimpleName());
        Throwable cause = failure.getCause();
        // Bounded, because a cause chain can loop (A caused by B caused by A) and the JDK only stops self-loops.
        for (int depth = 0; cause != null && depth < 5; depth++) {
            chain.append(" <- ").append(cause.getClass().getSimpleName());
            cause = cause.getCause();
        }
        return chain.toString();
    }
}
