package za.co.titandynamix.sasbo_online_backend.dto.migration;

import java.util.List;

/**
 * A batch's report after its check: how many rows of each outcome, and every problem found -- by its code and field,
 * how many rows have it, the first of them by their line in the file, and the sentence that says what to fix. Codes and
 * numbers only: never a value of the file.
 */
public record MigrationReportDto(long batchId, String status, int rowsRead, int rowsValid, int rowsWarned,
                                 int rowsInvalid, int rowsCommitted, List<CodeCount> errors, List<CodeCount> warnings) {

    /** One problem: how many rows have it, and the first of them (at most twenty) by line. */
    public record CodeCount(String code, String field, String severity, long rows, List<Integer> firstRows,
                            String sentence) {
    }
}
