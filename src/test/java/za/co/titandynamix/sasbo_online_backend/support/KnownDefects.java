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

    // CATCH_ALL_LEAKS_INTERNALS was here. FIXED in 01.2: the catch-all now
    // logs the exception against a short correlation id and returns only
    // that id, so the 500 body carries no SQL, no column name and no bound
    // value. The test it pinned -- GlobalExceptionHandlerTest
    // #theCatchAllDoesNotLeakInternals -- is enabled and is now the guard.

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

    /**
     * {@code Member.isRepresentative} is a {@code Boolean}; {@code MemberDto}
     * declares it {@code boolean}. Lombok names the DTO's property
     * {@code representative} and the entity's {@code isRepresentative}, so
     * MapStruct finds no match -- and {@code unmappedTargetPolicy = IGNORE}
     * means it never says so.
     *
     * <p>The field is therefore {@code false} on every member response,
     * including for members who are representatives. Worse than a null: a
     * primitive default reads as a confident answer rather than an absence, so
     * nothing downstream can tell the difference.
     *
     * <p>Found on 2026-09-04 by the field-exhaustive mapper guard, on its first
     * run against this mapper.
     */
    public static final String MEMBER_IS_REPRESENTATIVE_NEVER_MAPPED =
            "KNOWN DEFECT: MemberDto.isRepresentative is never populated, so it reads false for "
                    + "every member including actual representatives. The entity's Boolean and "
                    + "the DTO's primitive get different Lombok property names, MapStruct finds "
                    + "no match, and unmappedTargetPolicy = IGNORE silences the warning. This "
                    + "test asserts the value arrives.";

    /**
     * The catch-all handler is registered for {@code Exception}, and Spring
     * raises {@code NoResourceFoundException} for a path no controller handles.
     * So it is caught, and <b>every unknown URL answers 500 instead of 404</b>.
     *
     * <p>Found on 2026-09-04 by a test asserting that a controller with no
     * methods exposes nothing.
     */
    public static final String UNKNOWN_PATH_ANSWERS_500_NOT_404 =
            "KNOWN DEFECT: a request to a path no controller handles answers 500, not 404. "
                    + "Spring raises NoResourceFoundException, the catch-all handler is "
                    + "registered for Exception and catches it. Every typo in a URL therefore "
                    + "looks like a server fault, and real 500s cannot be told apart from "
                    + "wrong addresses in any log or alert. This test asserts the 404.";

    /**
     * {@code JacksonConfig} replaces Spring Boot's auto-configured
     * {@code ObjectMapper} with a hand-built one. Boot's disables
     * {@code WRITE_DATES_AS_TIMESTAMPS}; this one does not -- so every
     * {@code LocalDate} on the wire is {@code [2023,6,15]} rather than
     * {@code "2023-06-15"}, and every consumer has to special-case it.
     *
     * <p>Found on 2026-09-04 by the first controller test to serialise a date.
     */
    public static final String DATES_SERIALISE_AS_ARRAYS =
            "KNOWN DEFECT: LocalDate and LocalDateTime serialise as numeric arrays rather than "
                    + "ISO-8601 strings, because JacksonConfig replaces Boot's ObjectMapper with "
                    + "one that never disables WRITE_DATES_AS_TIMESTAMPS. This test asserts the "
                    + "ISO-8601 form. Changing it is a breaking wire change and needs the client "
                    + "moved in the same step, which is why it is recorded rather than fixed.";

    /**
     * Every endpoint under {@code /api/**} is {@code permitAll()}, so a member
     * register containing names, identity numbers, dates of birth and bank
     * account numbers is readable by anyone who can reach the port.
     */
    public static final String POPIA_ANONYMOUS_READ_IS_ALLOWED =
            "KNOWN DEFECT: /api/** is permitAll(), so an anonymous caller can read the member "
                    + "register. This test asserts the rule the standards require -- an "
                    + "anonymous read is denied -- and fails until authentication is enforced.";

    /**
     * The browser runs the full Luhn check on a South African ID; the server
     * checks {@code \d{13}} and nothing else, while its own Javadoc documents
     * the checksum digit. With {@code permitAll()} on {@code /api/**}, a POST
     * straight to the API bypasses the only validation that exists.
     */
    public static final String SA_ID_CHECKSUM_NOT_VALIDATED =
            "KNOWN DEFECT: the server does not verify the SA ID checksum. It checks the length "
                    + "and the digits only, while the browser runs the full Luhn check -- so a "
                    + "direct POST accepts an identity number the UI would reject. This test "
                    + "uses the same named vectors as the browser's.";

    /**
     * {@code /api/job-grades} answers with an empty list because the lookup
     * filters on a column that holds no matching value. A repository-level bug,
     * invisible to a mocked service test.
     */
    public static final String JOB_GRADES_QUERY_FILTERS_WRONG_COLUMN =
            "KNOWN DEFECT: the job-grades lookup filters on a column that holds no matching "
                    + "value, so the endpoint returns 0 rows where the table holds 27. This test "
                    + "asserts the correct count and fails until the query is corrected.";

    /**
     * The seeder matches bank names exactly and uses {@code .ifPresent()} with
     * no {@code else}, so a record whose bank name does not match is written
     * with a blank bank, no log and no error. One such row is in the live
     * database today.
     */
    public static final String UNMATCHED_BANK_NAME_IS_DROPPED_SILENTLY =
            "KNOWN DEFECT: resolving an unmatchable bank name succeeds silently -- the record is "
                    + "written with no bank, no log and no error, which is 'a field that reads "
                    + "but does not write' in its purest form. This test asserts the miss is "
                    + "noisy, and fails until it is.";

    /**
     * Century inference reads {@code LocalDate.now()} inside a {@code static}
     * method, so the same identity number yields a different date of birth as
     * years pass, and no test can pin the rollover without moving the machine's
     * clock. The fix is to inject a {@code Clock}.
     */
    public static final String CENTURY_INFERENCE_READS_THE_WALL_CLOCK =
            "KNOWN DEFECT: century inference reads LocalDate.now() inside a static method, so "
                    + "the same input yields a different answer as years pass and no test can "
                    + "pin it without moving the machine's clock. This test asserts the rollover "
                    + "behaviour and fails until a Clock is injected.";

    /**
     * The server derives date of birth and gender from the identity number only
     * when {@code identityType} equals {@code "ID"}. The browser's dropdown
     * offers {@code "I.D. Number"} and submits it verbatim, so the condition is
     * never true and <b>neither field is ever derived</b>.
     *
     * <p>Vocabulary drift between the two halves, of exactly the kind a static
     * comparison of the two lists would show and only an executed test can
     * convict. Found on 2026-09-04 by the first test of this derivation.
     *
     * <p>There is a second drift in the same place, on the browser's side: it
     * branches on {@code "Passport"} while its own options offer
     * {@code "Passport Number"}.
     */
    public static final String IDENTITY_TYPE_VOCABULARY_DRIFT =
            "KNOWN DEFECT: date of birth and gender are never derived from the identity number, "
                    + "because the server tests identityType against \"ID\" while the browser "
                    + "sends \"I.D. Number\". Neither side is wrong on its own; they simply do "
                    + "not agree. This test sends what the browser sends and asserts the "
                    + "derivation happens.";
}
