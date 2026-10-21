package za.co.titandynamix.sasbo_online_backend.security;

import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a password must be (signin.md): at least ten characters; not the username; not one of a short list of the most
 * common passwords. And at most 72 bytes -- BCrypt reads no further, so a longer password would have a tail that
 * counts for nothing, and the encoder refuses one.
 *
 * <p>A breach of a rule is {@code PASSWORD_RULES}, naming the rule ({@link Rule}) and never the password.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    /** The rule a refused password broke -- {@code details.rule}. */
    public enum Rule { TOO_SHORT, TOO_LONG, SAME_AS_USERNAME, TOO_COMMON, SAME_AS_CURRENT }

    /**
     * The commonest passwords of ten characters or more (shorter ones fail on length), lower case -- a short list on
     * purpose, of the ones an attacker tries first, with SASBO's own name among them.
     */
    static final Set<String> COMMON = Set.of(
            "1234567890", "0123456789", "0987654321", "1111111111", "1234512345", "1q2w3e4r5t", "1qaz2wsx3edc",
            "qwertyuiop", "qwerty1234", "qwerty12345", "asdfghjkl1", "zxcvbnm123", "password12", "password123",
            "password1234", "passw0rd123", "p@ssw0rd123", "p@ssword123", "iloveyou12", "letmein123", "welcome123",
            "welcome1234", "changeme123", "administrator", "admin12345", "admin123456", "abc1234567", "abcd123456",
            "sasbo12345", "sasbo123456", "sasbo@12345", "sasboonline", "sasboonline1", "sasbo online",
            "thefinanceunion", "financeunion", "southafrica", "southafrica1");

    private static final Map<Rule, String> SENTENCES = Map.of(
            Rule.TOO_SHORT, "A password needs at least " + MIN_LENGTH + " characters.",
            Rule.TOO_LONG, "A password can be at most " + MAX_BYTES + " characters long.",
            Rule.SAME_AS_USERNAME, "A password cannot be the username.",
            Rule.TOO_COMMON, "That password is one of the most common, and is the first an attacker would try. "
                    + "Choose another.",
            Rule.SAME_AS_CURRENT, "The new password must be different from the one it replaces.");

    private PasswordRules() {
    }

    /**
     * @throws SignInRefusal {@code PASSWORD_RULES}, naming the first rule {@code candidate} breaks
     */
    public static void check(String username, String candidate) {
        Rule broken = broken(username, candidate);
        if (broken != null) {
            throw refusal(broken);
        }
    }

    /** The first rule {@code candidate} breaks, or {@code null}. The password is compared as typed, never trimmed. */
    public static Rule broken(String username, String candidate) {
        if (candidate == null || candidate.codePointCount(0, candidate.length()) < MIN_LENGTH) {
            return Rule.TOO_SHORT;
        }
        if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Rule.TOO_LONG;
        }
        if (username != null && candidate.trim().equalsIgnoreCase(username.trim())) {
            return Rule.SAME_AS_USERNAME;
        }
        if (COMMON.contains(candidate.toLowerCase(Locale.ROOT))) {
            return Rule.TOO_COMMON;
        }
        return null;
    }

    /** The refusal for {@code rule}. */
    public static SignInRefusal refusal(Rule rule) {
        return new SignInRefusal(SignInRefusalCode.PASSWORD_RULES, SENTENCES.get(rule),
                Map.of("rule", rule.name(), "minLength", String.valueOf(MIN_LENGTH)));
    }
}
