package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/** E-mail the month's packs: to the recipients named -- every active one in scope where none is -- in these formats. */
public record MonthEndBulkEmailRequest(List<String> sets, List<String> formats) {
}
