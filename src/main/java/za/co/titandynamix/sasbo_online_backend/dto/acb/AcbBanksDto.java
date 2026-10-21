package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;
import java.util.Map;

/**
 * The banks SASBO collects debit orders from, with how many debit-order members each has on each day -- what the
 * move screen chooses from (DOD-19). Bounded by nature: about seventy banks.
 */
public record AcbBanksDto(List<Bank> banks) {

    /**
     * @param defaultDay        the bank's own debit day, from its active subscription row; null when it has none
     * @param debitOrderMembers the bank's debit-order members on record, whatever their status
     * @param byDay             how many on each day, by day
     */
    public record Bank(Integer bankId, String name, Integer defaultDay, int debitOrderMembers,
                       Map<String, Integer> byDay) {
    }
}
