package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;
import java.util.Map;

/**
 * Everything one payroll file's page shows: every field of {@link PayrollFileDto} (unwrapped, so the JSON is flat), how
 * the file was read, what the comparison counted, the discrepancies by kind -- every kind, zeros included -- the totals
 * and the payment check (PAY-28, Absa's fee PAY-27), the payments recorded and the queries raised.
 *
 * <p>{@code alreadyLoaded} is {@code true} when an upload found these exact bytes loaded already for the employer and
 * month: nothing was loaded twice.
 */
public record PayrollFileSummaryDto(
        @JsonUnwrapped PayrollFileDto file,
        boolean alreadyLoaded,
        Layout layout,
        Counts counts,
        List<TypeCount> discrepanciesByType,
        Totals totals,
        List<PayrollPaymentDto> payments,
        List<QueryRef> queries,
        int runs,
        String lastRunBy) {

    /**
     * How the file was read: the sheet, the row of headings, every heading on it as printed ({@code columns}, what
     * "Change the columns" offers), and the heading taken for each field ({@code headings}).
     */
    public record Layout(String key, String sheet, Integer headingRow, List<String> columns,
                         Map<String, String> headings) {
    }

    /** What the last comparison counted. */
    public record Counts(int rowsRead, int rowsUnreadable, int matched, int matchedByStaffNumber,
                         int matchedByIdNumber, int matchedByName, int notMembers, int missingMembers,
                         int catchUps, int listedTwice, int rateUnknown, int linesNotRead) {
    }

    /** One kind of discrepancy, by status. */
    public record TypeCount(String type, int open, int queried, int resolved) {
    }

    /** The subscription in force for the month (from the dated rates), or {@code null} where none is on record. */
    public record Subscription(String amount, String effectiveFrom) {
    }

    /**
     * The money: the file's own total, the total it states where it states one, what the matched members' subscriptions
     * come to, the employer's fee, what should be paid over, and what was.
     */
    public record Totals(String fileTotal, String statedTotal, Boolean statedTotalAgrees, Subscription subscription,
                         String expectedTotal, String adminFeePercent, String adminFee, String expectedPayment,
                         String received, String paymentDifference, String paymentStatus) {
    }

    /** A query raised for the file, and how many lines of each kind it carries. */
    public record QueryRef(long id, String raisedAt, String raisedBy, int add, int delete, int correct) {
    }
}
