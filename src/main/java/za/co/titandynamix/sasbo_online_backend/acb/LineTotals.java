package za.co.titandynamix.sasbo_online_backend.acb;

import java.math.BigDecimal;

/**
 * A run's control totals, counted from its lines. The sum is {@code null} for a run with no lines,
 * which is what SQL's {@code sum} answers over nothing; {@link #total()} makes that zero.
 */
public record LineTotals(Long count, BigDecimal sum) {

    public int lineCount() {
        return count == null ? 0 : count.intValue();
    }

    public BigDecimal total() {
        return sum == null ? BigDecimal.ZERO.setScale(2) : sum.setScale(2);
    }
}
