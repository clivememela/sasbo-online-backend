package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import java.util.List;

/**
 * The payroll module's words, for a screen to build its filters and choices from: every kind of discrepancy with its
 * label, Stephne's rule, what to do, the query action and the outcomes that fit; every outcome with its label; the
 * statuses and match words; the limits.
 */
public record PayrollVocabularyDto(
        List<DiscrepancyType> discrepancyTypes,
        List<Outcome> outcomes,
        List<String> statuses,
        List<String> matchRules,
        List<String> confidences,
        List<String> amountStatuses,
        Limits limits,
        List<String> accepted) {

    /** One kind of discrepancy. {@code rule} and {@code queryAction} are {@code null} where there is none. */
    public record DiscrepancyType(String type, String label, String rule, String whatToDo, String queryAction,
                                  List<String> outcomes) {
    }

    /** One outcome. {@code systemOnly}: only ever set by the system, never offered to a person. */
    public record Outcome(String outcome, String label, boolean systemOnly) {
    }

    /** The limits a request is held to. */
    public record Limits(long maxBytes, int maxRows, int maxTypedRows, int noteMaxLength, int pageMaxSize) {
    }
}
