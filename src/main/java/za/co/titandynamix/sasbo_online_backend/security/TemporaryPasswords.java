package za.co.titandynamix.sasbo_online_backend.security;

import java.security.SecureRandom;

/**
 * A temporary password, made by an administrator for a new user or on a reset: shown ONCE, in the answer, and never
 * stored but as its BCrypt hash. Four groups of four from an alphabet without the look-alikes (no 0/O/o, 1/l/I), so it
 * can be read out over the telephone: 16 symbols from 56, about 93 bits.
 */
public final class TemporaryPasswords {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    static final int GROUPS = 4;
    static final int GROUP_LENGTH = 4;

    private TemporaryPasswords() {
    }

    /** A new temporary password from {@code random}, which passes every rule of {@link PasswordRules}. */
    public static String generate(SecureRandom random) {
        StringBuilder password = new StringBuilder(GROUPS * (GROUP_LENGTH + 1));
        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                password.append('-');
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return password.toString();
    }
}
