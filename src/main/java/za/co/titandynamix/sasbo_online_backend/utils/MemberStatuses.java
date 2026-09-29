package za.co.titandynamix.sasbo_online_backend.utils;

import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The member statuses the ACB module moves a member to (03.5), spelled as the member record stores them and the
 * browser's {@code STATUS_OPTIONS} offers them. A member's status is text, not an enum: these two are the ones the
 * returns loop writes.
 *
 * <p>Month end 03.2 adds the six the record takes, each with its KIND ({@link #KINDS}) -- the one thing a figure reads
 * of a status (PA-4). No status is renamed: the six stay as the screens have them until the table with SASBO says
 * otherwise (MM49), and a renaming then moves no figure.
 */
public final class MemberStatuses {

    /** "Still pending and not terminated as yet" -- an unpaid whose code allows no re-debit (Q40, Q44). */
    public static final String SUSPENDED = "Suspended";

    /** Three unpaid presentations (Q27): the membership is cancelled, for the reason SUBS_IN_ARREARS (Q25). */
    public static final String TERMINATED = "Terminated";

    /** Month end 03.2: a member on record and paying. */
    public static final String ACTIVE = "Active";

    /** Month end 03.2: captured and not yet confirmed -- on record, and counted as paying until SASBO says (MM49). */
    public static final String PENDING = "Pending";

    /** Month end 03.2: resigned -- a leaver, with SASBO's deletion code (2, and the reason given). */
    public static final String RESIGNED = "Resigned";

    /** Month end 03.2: retired -- a leaver, with SASBO's deletion code (1, left service, until SASBO says, MM49). */
    public static final String RETIRED = "Retired";

    /**
     * Every status the member record takes, in the order the screens offer them, with its kind. A status that is
     * not here -- the recruiters screen's {@code Inactive} (A49) -- is one the figures do not know, and say so
     * (rule X, ME-17).
     */
    public static final Map<String, MemberStatusKind> KINDS;

    static {
        Map<String, MemberStatusKind> kinds = new LinkedHashMap<>();
        kinds.put(ACTIVE, MemberStatusKind.NEITHER);
        kinds.put(PENDING, MemberStatusKind.NEITHER);
        kinds.put(SUSPENDED, MemberStatusKind.SUSPENDED);
        kinds.put(RESIGNED, MemberStatusKind.LEAVING);
        kinds.put(RETIRED, MemberStatusKind.LEAVING);
        kinds.put(TERMINATED, MemberStatusKind.LEAVING);
        KINDS = Collections.unmodifiableMap(kinds);
    }

    private MemberStatuses() {
    }

    /** The kind of a status as the record stores it; {@code null} for a status nobody knows. */
    public static MemberStatusKind kindOf(String status) {
        return status == null ? null : KINDS.get(status);
    }

    /** The statuses of one kind, in the screens' order. */
    public static java.util.List<String> ofKind(MemberStatusKind kind) {
        return KINDS.entrySet().stream().filter(e -> e.getValue() == kind).map(Map.Entry::getKey).toList();
    }
}
