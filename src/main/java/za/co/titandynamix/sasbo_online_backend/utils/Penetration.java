package za.co.titandynamix.sasbo_online_backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Penetration (rule P, PA-8, MM44): the members paying at a place over the staff complement on record there -- and the
 * ONE place it is worked out. Home's figures and the branches' statistic (04.6) both ask it here, so another
 * denominator, if SASBO names one (HOLD-6), is one change.
 */
public final class Penetration {

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
}
