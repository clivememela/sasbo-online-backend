package za.co.titandynamix.sasbo_online_backend.support;

/**
 * The defects this suite asserts deliberately, and does not fix.
 *
 * <h2>Why a disabled test is ever acceptable here</h2>
 * There is no flaky list and nothing is skipped for unreliability. A
 * {@code @Disabled} test in this module means exactly one thing: <b>a test
 * written to the correct behaviour, against a defect that is real, left
 * visibly failing by design.</b> Fixing the defect is a production change, and
 * this suite's job is to make it visible rather than to repair it while nobody
 * is looking.
 *
 * <p>Untested, the defect is invisible. Asserted-but-disabled with its reason
 * travelling beside it, the day someone fixes it the test turns green and
 * {@code DisabledPopulationTest} reddens to say so. <b>A defect nobody notices
 * being fixed is the failure a register exists to prevent.</b>
 *
 * <h2>Every constant here has exactly one disabled test, and vice versa</h2>
 * {@link za.co.titandynamix.sasbo_online_backend.guards.DisabledPopulationTest}
 * asserts the correspondence in both directions and asserts the counts match.
 * Adding a disabled test without a row here reddens. Fixing a defect and
 * leaving its test disabled reddens too -- and that second half is the one that
 * actually rots, because nothing else in a suite ever complains about it.
 *
 * <p>Individually each of these is justified. Collectively, before this file
 * existed, nothing was counting them -- and a suite carrying a dozen disabled
 * tests with no register reads as neglected rather than deliberate, which is
 * how the next one gets added quietly.
 *
 * <h2>These are constants, not an enum, on purpose</h2>
 * {@code @Disabled} takes a compile-time constant, so the reason travels INTO
 * the annotation. A reader who opens the test sees the whole story without
 * leaving the file, and the guard can still recover which row was cited by
 * reading the source.
 */
public final class KnownDefects {

    private KnownDefects() {
    }

    /**
     * {@code handleGeneralException} puts {@code ex.getMessage()} straight into
     * the response body. For a constraint violation or any JDBC failure that
     * string carries the SQL, the constraint name, the table and column names
     * and often the bound parameter values -- which on this application means
     * identity numbers and bank account numbers. Combined with
     * {@code permitAll()}, a caller who can trigger a 500 reads internals
     * anonymously.
     */
    public static final String CATCH_ALL_LEAKS_INTERNALS =
            "KNOWN DEFECT: the catch-all exception handler copies the exception message into the "
                    + "response body, so a 500 can carry SQL, table and column names and bound "
                    + "parameter values to an anonymous caller. This test asserts the 500 body "
                    + "carries none of them. The 404 and 400 handlers keep their messages "
                    + "deliberately -- those are caller-facing, and narrowing the finding to the "
                    + "catch-all is what makes it a one-line fix.";

    /**
     * Two of nineteen services carry {@code @Transactional}. A service method
     * issuing more than one repository write without a boundary can half-land,
     * and this is a data-capture system holding records for real people.
     */
    public static final String MULTI_WRITE_WITHOUT_A_TRANSACTION =
            "KNOWN DEFECT: service methods issue more than one repository write with no "
                    + "transaction boundary, so a failure between the writes leaves the data half "
                    + "written, silently. This test asserts every multi-write method is "
                    + "transactional and names the ones that are not.";

    /**
     * Nothing paginates. Every list endpoint returns the whole table, so the
     * response grows with the database and the first symptom is a timeout.
     */
    public static final String COLLECTION_ENDPOINTS_ARE_UNBOUNDED =
            "KNOWN DEFECT: no list endpoint paginates or caps its result, so each returns the "
                    + "whole table and the response grows with the data. This test asserts a "
                    + "bound exists, and fails until one does.";


    /**
     * {@code handleValidationFailures} casts every element of
     * {@code getAllErrors()} to {@code FieldError}. A class-level constraint
     * produces an {@code ObjectError}, which is not one -- so the 400 handler
     * throws {@code ClassCastException} and the caller receives a 500 instead
     * of the validation response.
     *
     * <p>Latent today: no DTO carries a class-level constraint. It becomes live
     * the first time anyone writes one, and the symptom -- a 500 with a
     * ClassCastException on a payload the browser rejected -- points nowhere
     * near the cause.
     *
     * <p>Found on 2026-09-04 by the first test ever written against this
     * handler.
     */
    public static final String VALIDATION_HANDLER_CANNOT_TAKE_A_GLOBAL_ERROR =
            "KNOWN DEFECT: the validation handler casts every binding error to FieldError, so a "
                    + "class-level constraint -- which produces an ObjectError -- makes it throw "
                    + "ClassCastException and the caller gets a 500 instead of a 400. This test "
                    + "asserts a global error is handled, and fails until the cast is guarded.";
}
