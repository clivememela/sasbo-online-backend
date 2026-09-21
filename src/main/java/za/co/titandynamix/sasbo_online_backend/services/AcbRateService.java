package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRatesDto;

import java.math.BigDecimal;

/**
 * The yearly rate's way in (Q43): SASBO captures January's amount in December. Reading is open; adding
 * sets what thousands of members are charged, so its route refuses every caller until B1 (D13).
 */
public interface AcbRateService {

    /** Every dated rate, and the January notice. */
    AcbRatesDto rates();

    /**
     * Adds a rate from the first of a month still to come. {@code RATE_NOT_FUTURE},
     * {@code RATE_ALREADY_SET} and {@code RATE_MONTH_CHARGED} as their names say; a past row is never
     * edited (D25). Audited run-less, in the same transaction (SD4-A).
     */
    AcbRatesDto add(String effectiveFrom, BigDecimal amount, String source, String actor);
}
