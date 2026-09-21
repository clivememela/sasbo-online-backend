package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.math.BigDecimal;
import java.util.List;

/**
 * The flat debit-order rate, dated (Q43): every row, oldest first -- one a year, so bounded by the
 * calendar -- and the January notice.
 *
 * @param januaryNotice from December, when next month is a January with no rate dated that January:
 *                      which amount it will charge and since when -- a notice, never a refusal, since
 *                      the rate may honestly not change (SD3-G). Null otherwise
 */
public record AcbRatesDto(List<Rate> rates, String januaryNotice) {

    /** One dated rate. {@code effectiveFrom} is the first day of its first month, ISO-8601. */
    public record Rate(String effectiveFrom, BigDecimal amount, String source) {
    }
}
