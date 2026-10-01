package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * The months taken, the newest first, and the open month (month end 04.2) -- what the Month end page lists.
 *
 * @param openMonth the clock's month, in SASBO's zone -- never the browser's (MD55)
 * @param taken     every month taken, the newest first
 */
public record MonthEndMonthsDto(String openMonth, List<MonthEndMonthDto> taken) {
}
