package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * A run's debit days -- one returns report comes back for each (Stephne, DOD-06) -- with how far each has come back.
 * Bounded by nature: a month has at most a dozen debit days.
 */
public record AcbDebitDaysDto(Long runId, List<Day> days) {

    /**
     * @param actionDate the debit day
     * @param lines      debits on it
     * @param unpaid     of them recorded unpaid
     * @param batches    returns reports typed in for it
     */
    public record Day(String actionDate, long lines, long unpaid, long batches) {
    }
}
