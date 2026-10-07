package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/** A page of a month's deliveries, the newest first. */
public record MonthEndDeliveriesPageDto(String month, int page, int size, long total, List<MonthEndDeliveryDto> items) {
}
