package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * The job-grade matching rule. One rule, specified once (ux.md §6c clause ①).
 *
 * <h2>Trim, collapse internal whitespace, compare case-insensitively. Store exactly
 * as supplied.</h2>
 *
 * <h2>🔴 Why this is a class and not three lines inside the service</h2>
 * This project already carries FOUR registered defects that are all the same
 * mistake -- a rule implemented twice and allowed to drift:
 *
 * <ul>
 *   <li><b>A6</b> {@code "Payroll"} on the client, {@code "Payroll Deduction"} on the server
 *   <li><b>A7</b> {@code "Passport"} in the rule, {@code "Passport Number"} in the form
 *   <li><b>A17</b> {@code "ID"} on the server, {@code "I.D. Number"} from the browser
 *   <li><b>A9</b> the client re-implementing a rule the server publishes
 * </ul>
 *
 * <p>The job-grade fallback needs the browser and the server to agree on what counts
 * as "the same grade", so a matching rule left in two files would have become the
 * fifth. The client mirrors this exactly in {@code utils/jobGradeMatch.js}, and the
 * SAME fixture is asserted against both.
 *
 * <p>⚠️ The half-rule is what makes this dangerous rather than merely untidy: the
 * server already did {@code equalsIgnoreCase} and nothing else. If the client also
 * folded double spaces and the server did not, {@code "Grade  c"} would match in the
 * browser, be treated as a brand-new grade on save, and the user would be told it
 * worked. That is A17's shape precisely.
 *
 * <h2>Case is compared, never changed</h2>
 * {@code Grade c} is a real Old Mutual grade -- lowercase {@code c}, sitting beside
 * {@code Grade K}. Upper-casing a typed value to "tidy" it would corrupt the union's
 * own reference data, so normalisation is used for COMPARISON only and the stored
 * string keeps the case it arrived with.
 */
public final class JobGradeMatcher {

    private JobGradeMatcher() {
    }

    /**
     * The comparison key: trimmed, internal whitespace collapsed to one space, lower
     * cased. Used to decide whether two grades are the same grade -- never stored.
     */
    public static String comparisonKey(String grade) {
        if (grade == null) {
            return "";
        }
        return grade.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The value to STORE for a grade the user typed: trimmed, internal whitespace
     * collapsed -- and the case left exactly as supplied.
     */
    public static String storedForm(String grade) {
        if (grade == null) {
            return "";
        }
        return grade.trim().replaceAll("\\s+", " ");
    }

    /** Whether two grades are the same grade under this rule. */
    public static boolean matches(String a, String b) {
        String keyA = comparisonKey(a);
        return !keyA.isEmpty() && keyA.equals(comparisonKey(b));
    }

    /** Whether a submitted grade is usable at all. */
    public static boolean isBlank(String grade) {
        return comparisonKey(grade).isEmpty();
    }
}
