package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * THE SWITCH -- whether SASBO's workplaces, and everything of SASBO's shape that stands on them, are seeded AND
 * served on this deployment ({@code sasbo.workplaces}, from {@code SASBO_WORKPLACES}). Month end, MD45, MD50, MD53,
 * MD62.
 *
 * <h2>Why a switch, and why it is off unless it is set</h2>
 * SASBO's branch list is their own record: 5,507 real workplaces, each with its staff complement and the dates it was
 * visited. The hosted demonstration has no sign-in (register B1), and every read this arc adds is open, so the list
 * goes on a hosted stack only when SASBO has said it may (MM79). An application cannot know that it is on a public
 * host, so the safe value has to be the one nobody set (MD50): blank is {@code none}. The suite's compose, which is
 * also the local run, sets it on.
 *
 * <h2>A word of three values, and what is served follows it (MD62)</h2>
 * <ul>
 *   <li>{@code none} -- blank means this. No workplace is seeded, and none is answered: the routes that answer
 *       anything of the workplaces say they are not loaded here. The lists of names and codes are unaffected.</li>
 *   <li>{@code names} -- the workplaces are on record and answered by name, and NOTHING MADE FROM A STAFF NUMBER is
 *       answered: no complement, no visit, no penetration, no invented member, no figure grouped finer than the
 *       whole (MR-SEC-19). It is SASBO's third possible answer to MM79: <i>yes, without the staff numbers</i>.</li>
 *   <li>{@code all} -- the workplaces, the invented members who stand at them, their past and the seed's taken
 *       months are seeded, and all of it is answered.</li>
 * </ul>
 * Set back, the rows stay at rest and stop being answered: what was published can be taken back (MD62). Every route
 * of this arc that answers anything of the workplaces asks this first.
 *
 * <h2>🔴 A word it does not know stops the start</h2>
 * As the production marker does ({@link SasboEnvironment}): {@code al} or {@code yes} would otherwise be read as
 * something, and the something would be a guess about SASBO's word. So it is refused as the configuration is bound,
 * before any seeder runs, saying what is accepted.
 */
@ConfigurationProperties(prefix = "sasbo")
public record WorkplaceSwitch(String workplaces) {

    /** What the start-up refusal says is accepted. */
    static final String ACCEPTED = "'none', 'names' or 'all' -- or unset (blank), which is 'none'";

    /** The three things the switch can say. */
    public enum Mode {
        /** No workplace is seeded or served. What blank means. */
        NONE,
        /** The workplaces by name; nothing made from a staff number. */
        NAMES,
        /** Everything of SASBO's shape. */
        ALL
    }

    public WorkplaceSwitch {
        modeOf(workplaces);
    }

    /** What this deployment's switch says. */
    public Mode mode() {
        return modeOf(workplaces);
    }

    /** Are SASBO's workplaces seeded and answered here at all? */
    public boolean workplacesOnRecord() {
        return mode() != Mode.NONE;
    }

    /**
     * May anything made from a staff number be answered here -- a complement, a visit, a penetration, an invented
     * member, a figure grouped finer than the whole? Only at {@code all} (MR-SEC-19).
     */
    public boolean staffNumbersServed() {
        return mode() == Mode.ALL;
    }

    /**
     * @throws IllegalArgumentException for any value but blank, {@code none}, {@code names} or {@code all} --
     *         saying what is accepted, so the person reading the failed start knows what to type
     */
    static Mode modeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.NONE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> Mode.NONE;
            case "names" -> Mode.NAMES;
            case "all" -> Mode.ALL;
            default -> throw new IllegalArgumentException("SASBO_WORKPLACES is not a word the switch knows. It must be "
                    + ACCEPTED + ". Nothing else is accepted: the switch says whether SASBO's own branch list may be "
                    + "on this deployment, and a guess at a misspelt word would be a guess at SASBO's answer.");
        };
    }

    /** The one line start-up says about it. */
    public String describe() {
        return switch (mode()) {
            case NONE -> "SASBO's workplaces: none -- not seeded and not served here (SASBO_WORKPLACES is not set)";
            case NAMES -> "SASBO's workplaces: names -- served by name, and nothing made from a staff number";
            case ALL -> "SASBO's workplaces: all -- seeded and served, with the invented members who stand at them";
        };
    }
}
