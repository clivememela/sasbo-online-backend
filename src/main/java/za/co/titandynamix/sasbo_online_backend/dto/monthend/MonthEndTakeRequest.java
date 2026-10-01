package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * A take of a month (month end 04.2) -- or a take again (04.11, MM51), which names the take it replaces: the month's
 * newest, as the months answer it ({@code takeId}). Nobody overwrites a take they have not seen.
 *
 * @param replaces the take this one replaces; {@code null}, or no body at all, for a month's first take
 */
public record MonthEndTakeRequest(Long replaces) {
}
