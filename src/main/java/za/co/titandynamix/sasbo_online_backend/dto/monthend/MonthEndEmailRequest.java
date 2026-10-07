package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/** E-mail a region's pack to its recipient: the formats to attach, {@code PDF}, {@code XLSX} or both. */
public record MonthEndEmailRequest(List<String> formats) {
}
