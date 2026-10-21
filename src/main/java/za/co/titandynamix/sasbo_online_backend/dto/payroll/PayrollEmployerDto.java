package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import java.util.List;
import java.util.Map;

/**
 * A payroll employer and how its file is read. {@code readable} is {@code false} for GBS and SA Reserve Bank, whose
 * files are a PDF and a picture (PAY-03): their rows are typed in. {@code adminFeePercent} is the fee the employer keeps
 * when it pays over -- Absa's 3% (PAY-27).
 */
public record PayrollEmployerDto(
        long id,
        String code,
        String name,
        String delivery,
        boolean readable,
        String layoutKey,
        String adminFeePercent,
        Map<String, String> columnMapping,
        List<String> sasboEmployers,
        LatestFile latestFile,
        int version) {

    /** The employer's newest live file, so the list says where each employer stands. */
    public record LatestFile(long id, String period, String status, int openDiscrepancies) {
    }
}
