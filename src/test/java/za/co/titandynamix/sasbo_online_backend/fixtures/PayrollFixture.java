package za.co.titandynamix.sasbo_online_backend.fixtures;

import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerRef;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

import java.util.List;
import java.util.Map;

/** Invented payroll answers for the controller slices -- the shapes of {@code contracts/payroll-api.md}. */
public final class PayrollFixture {

    public static final PayrollEmployerRef ABSA = new PayrollEmployerRef(1, "ABSA", "Absa");

    private PayrollFixture() {
    }

    public static PayrollFileDto file() {
        return new PayrollFileDto(12, ABSA, "2026-08", "Absa payroll August 2026.xlsx", "UPLOAD", "XLSX", 18_234,
                "9f2c" + "0".repeat(60), "RECONCILED", "system", "2026-09-15T08:00:00Z", "2027-09-15", 24, 21, 9, 7,
                "4168.65", "EXACT", "2026-09-15T08:00:02Z", 1);
    }

    public static PayrollFileSummaryDto summary(boolean alreadyLoaded) {
        return new PayrollFileSummaryDto(file(), alreadyLoaded,
                new PayrollFileSummaryDto.Layout("absa", "Sheet1", 1, List.of("Pers.No.", "Amount"),
                        Map.of("staffNumber", "Pers.No.", "amount", "Amount")),
                new PayrollFileSummaryDto.Counts(24, 0, 21, 19, 0, 2, 1, 1, 1, 1, 0, 0),
                List.of(new PayrollFileSummaryDto.TypeCount("NOT_A_MEMBER", 1, 0, 0)),
                new PayrollFileSummaryDto.Totals("4168.65", null, null,
                        new PayrollFileSummaryDto.Subscription("174.00", "2026-01-01"), "4176.00", "3.00", "125.06",
                        "4043.59", "4043.59", "0.00", "EXACT"),
                List.of(payment(false)), List.of(new PayrollFileSummaryDto.QueryRef(2, "2026-09-15T09:00:00Z", "system",
                1, 2, 0)), 1, "system");
    }

    public static PayrollPaymentDto payment(boolean alreadyRecorded) {
        return new PayrollPaymentDto(3, ABSA, "2026-08", "4043.59", "2026-08-31", "EFT ABSA 2026-08", "system",
                "2026-09-15T08:30:00Z", alreadyRecorded);
    }

    public static PayrollDiscrepancyDto discrepancy() {
        return new PayrollDiscrepancyDto(77, 12, ABSA, "2026-08", "AMOUNT_DIFFERS", "Amount differs", "PAY-16",
                "The amount differs from the subscription.", "OPEN", "Line 8: R166.65 deducted; R174.00 expected: "
                + "R7.35 short.", new PayrollDiscrepancyDto.Member(123, "M-095005", "T.D. Ledgerwood", "Active",
                "Payroll Deduction", "99010005"), new PayrollDiscrepancyDto.Row(8, "99010005", "****9086", "TD",
                "LEDGERWOOD", "166.65"), "166.65", "174.00", "-7.35", null, "CORRECT", null,
                List.of("CORRECTED_BY_EMPLOYER", "ARREARS_RECORDED", "CREDIT_RECORDED", "OTHER"), null, 0, null,
                List.of(), "2026-09-15T08:00:02Z", 0);
    }

    public static PayrollQueryDto query() {
        return new PayrollQueryDto(2, 12, ABSA, "2026-08", "system", "2026-09-15T09:00:00Z",
                new PayrollQueryDto.Counts(1, 2, 0), List.of(new PayrollQueryDto.Line("ADD", 71, "MISSING_FROM_FILE",
                null, null, "R.T. Tallyburn", "M-095020", null, "174.00",
                "A member of SASBO not deducted this month: please deduct the subscription.")), List.of());
    }

    /** A signed-in caller limited to one region, as the sign-in filter would put one on the request. */
    public record ScopedCaller(String username, String displayName, CallerScope scope) implements ScopedPrincipal {

        public static ScopedCaller regional() {
            return new ScopedCaller("regional", "A Regional Secretary", CallerScope.regions(java.util.Set.of("DBN")));
        }
    }
}
