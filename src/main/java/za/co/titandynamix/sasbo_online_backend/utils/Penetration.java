package za.co.titandynamix.sasbo_online_backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Penetration (rule P, PA-8, MM44): the members paying at a place over the staff complement on record there -- and the
 * ONE place it is worked out. Home's figures and the branches' statistic (04.6) both ask it here, so another
 * denominator, if SASBO names one (HOLD-6), is one change.
 */
public final class Penetration {

    /**
     * Stephne, MEN-26 -- "This usually happens when the compliment is not updated or correct. This should be flagged so
     * we can fix it." The sentence beside a flagged figure, which is shown as it is, never in place of the number.
     */
    public static final String OUT_OF_DATE_NOTE = "Where more members pay than the staff complement on record, the "
            + "penetration is shown as it is and flagged: the complement is out of date and should be updated.";

    private Penetration() {
    }

    /**
     * {@code paying} over {@code complement}, to four places, half up -- above one where more pay than the complement
     * counts, never capped. None where no complement is on record: {@code null}, or nought.
     */
    public static BigDecimal of(long paying, Long complement) {
        return complement == null || complement <= 0 ? null
                : BigDecimal.valueOf(paying).divide(BigDecimal.valueOf(complement), 4, RoundingMode.HALF_UP);
    }

    /**
     * Whether the complement on record is out of date (MEN-26): more members pay at the place than its complement
     * counts. Never where no complement is on record -- nothing is compared with nothing.
     */
    public static boolean complementOutOfDate(long paying, Long complement) {
        return complement != null && complement > 0 && paying > complement;
    }
}
