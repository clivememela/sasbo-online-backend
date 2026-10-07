package za.co.titandynamix.sasbo_online_backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Session tokens: 256 random bits, base64url without padding (43 characters), and the SHA-256 the server keeps of one.
 *
 * <p>A plain SHA-256, unsalted, is right for THIS secret and wrong for a password: a token is 256 bits from
 * {@link SecureRandom}, so there is no dictionary to try and nothing to slow down, and the lookup must be one indexed
 * equality. A password is chosen by a person, and gets BCrypt.
 */
public final class SessionTokens {

    /** 32 bytes: 256 bits. */
    static final int BYTES = 32;

    /** What a token looks like: exactly 43 characters of the base64url alphabet. Anything else is not looked up. */
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SessionTokens() {
    }

    /** A new token, from {@code random}. */
    public static String newToken(SecureRandom random) {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Whether {@code token} is shaped like one this server hands out. */
    public static boolean wellFormed(String token) {
        return token != null && SHAPE.matcher(token).matches();
    }

    /** The SHA-256 of {@code token}, as 64 lower-case hex digits -- what {@code user_sessions.token_hash} holds. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", absent);
        }
    }
}
