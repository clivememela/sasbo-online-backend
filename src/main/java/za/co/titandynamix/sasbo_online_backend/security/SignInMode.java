package za.co.titandynamix.sasbo_online_backend.security;

import java.util.Locale;

/**
 * Whether sign-in is in force ({@code sasbo.signin.mode}, from {@code SASBO_SIGNIN}).
 *
 * <ul>
 *   <li>{@link #OPEN}, the default: today's link keeps working while SASBO has no accounts. A route that names a
 *       permission needs a signed-in user holding it; every other route answers anyone, as it always has. A token,
 *       when one is sent, says who is calling everywhere.</li>
 *   <li>{@link #REQUIRED}: every {@code /api} route needs a session, except signing in, the mode itself and the health
 *       check. The owner switches it on before go-live (6 Oct).</li>
 * </ul>
 *
 * <p>🔴 A typo stops the start. A switch that reads one spelling fails OPEN on every other -- {@code requird} would
 * leave every route answering anyone while the owner believed it closed -- so blank means open, the two words (trimmed,
 * any case) mean what they say, and anything else is refused as the configuration is bound.
 */
public enum SignInMode {

    OPEN("open"),
    REQUIRED("required");

    private final String word;

    SignInMode(String word) {
        this.word = word;
    }

    /** The word the API answers at {@code GET /api/auth/mode}. */
    public String word() {
        return word;
    }

    /**
     * @throws IllegalArgumentException for anything but blank, {@code open} or {@code required}, saying what is
     *                                  accepted
     */
    public static SignInMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        String word = raw.trim().toLowerCase(Locale.ROOT);
        for (SignInMode mode : values()) {
            if (mode.word.equals(word)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("SASBO_SIGNIN is '" + raw + "'. It must be 'open', 'required', or unset "
                + "(which is open). Nothing else is accepted: a misspelt mode would leave every route open while it "
                + "was believed closed.");
    }
}
