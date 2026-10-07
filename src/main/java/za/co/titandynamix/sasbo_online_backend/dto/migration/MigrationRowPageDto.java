package za.co.titandynamix.sasbo_online_backend.dto.migration;

import java.util.List;
import java.util.Map;

/**
 * A page of a batch's rows, for the person fixing the file: each row's line, its outcome, its problems, and its values
 * -- as the file has them and as they would be written. Identity, passport and account numbers are shown as their last
 * four, as the member's history shows them. Answered only to a holder of {@code MIGRATION_RUN}.
 */
public record MigrationRowPageDto(List<Row> items, int page, int size, long total) {

    /** One row. Its {@code toString} carries its line and outcome, never a value. */
    public record Row(int rowNumber, String outcome, List<ProblemDto> problems, Map<String, String> values,
                      Map<String, String> raw) {

        @Override
        public String toString() {
            return "Row[" + rowNumber + ", " + outcome + "]";
        }
    }

    /** One problem of a row: its code, the field, its severity under the batch's mapping, and what to fix. */
    public record ProblemDto(String code, String field, String severity, String sentence) {
    }
}
