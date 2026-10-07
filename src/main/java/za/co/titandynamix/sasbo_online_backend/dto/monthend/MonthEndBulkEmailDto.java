package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * What e-mailing the month's packs did, a recipient at a time -- never failed as a whole: {@code SENT}, or
 * {@code REFUSED} or {@code FAILED} with its code and a sentence, and the delivery recorded where one was.
 */
public record MonthEndBulkEmailDto(String month, List<Result> results) {

    /** One recipient's result. */
    public record Result(String setCode, String outcome, String code, String message, MonthEndDeliveryDto delivery) {
    }
}
