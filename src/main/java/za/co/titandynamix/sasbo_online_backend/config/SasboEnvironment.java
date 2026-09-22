package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * Whether this is the production system -- the ONE marker that tells it apart from a demo
 * ({@code sasbo.environment}, from {@code SASBO_ENVIRONMENT}).
 *
 * <h2>Why a marker at all</h2>
 * Nothing else in the application can tell production from a demonstration: no profile, no
 * environment name (measured, 2026-09-21). The seed puts synthetic people on debit order at real
 * banks, and a bureau file is an instruction to take money -- a synthetic account number that happens
 * to be somebody's real one is a debit against a stranger. So where this says {@code production},
 * nothing synthetic is ever seeded, and no file is made from a seeded member (SD3-A). A demo sets
 * nothing, which is why the owner's "they must just be there" holds untouched.
 *
 * <p>It is set once, on the real production service, the day that service is created; the deploy
 * arc carries it on the go-live checklist. {@code dev.sasbo.online} is not production.
 *
 * <h2>🔴 A typo stops the start -- here, at binding</h2>
 * A lock that reads one spelling fails open on every other: {@code prod} or {@code production.}
 * would not match, and synthetic members would be seeded into real production. So blank means a
 * demo, {@code production} -- trimmed, any case -- means production, and ANYTHING else is refused as
 * the configuration is bound, before any seeder runs (EH2-D). It is validated here and not in a
 * seeder because a seeder's failure is caught so a demo can still start (SD3-I), and that catch
 * would swallow this refusal (F59). Seeders are handed the parsed mode, never the string.
 */
@ConfigurationProperties(prefix = "sasbo")
public record SasboEnvironment(String environment) {

    /** The only spelling that means production -- compared trimmed and case-insensitively. */
    public static final String PRODUCTION = "production";

    /** What the start-up refusal says is accepted. */
    static final String ACCEPTED = "'" + PRODUCTION + "' on the production service, or unset (blank) anywhere else";

    /** The two things this system can be. */
    public enum Mode {
        DEMO,
        PRODUCTION
    }

    public SasboEnvironment {
        modeOf(environment);
    }

    /** Which one this is. */
    public Mode mode() {
        return modeOf(environment);
    }

    public boolean isProduction() {
        return mode() == Mode.PRODUCTION;
    }

    /**
     * @throws IllegalArgumentException for any value but blank or {@code production} -- saying what
     *         is accepted, so the person reading the failed start knows what to type
     */
    static Mode modeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.DEMO;
        }
        if (raw.trim().toLowerCase(Locale.ROOT).equals(PRODUCTION)) {
            return Mode.PRODUCTION;
        }
        throw new IllegalArgumentException("SASBO_ENVIRONMENT is '" + raw + "'. It must be " + ACCEPTED
                + ". Nothing else is accepted: a misspelt marker would let synthetic members be seeded into "
                + "production.");
    }

    /** The one line start-up says about it (SD4-I). */
    public String describe() {
        return isProduction()
                ? "environment: production -- nothing synthetic is seeded, and no bureau file is made from a seeded member"
                : "environment: demo -- seed data enabled (SASBO_ENVIRONMENT is not set to production)";
    }
}
