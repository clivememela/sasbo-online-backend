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
     * Most services carry no {@code @Transactional} at all. A service method
     * issuing more than one repository write without a boundary can half-land,
     * and this is a data-capture system holding records for real people.
     *
     * <p>Which ones do is in {@code TransactionBoundaryTest}, measured rather than
     * counted here: this sentence said "two of nineteen" while the answer was four
     * of twenty-four, and nothing failed, because a comment cannot.
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

    /**
     * Production runs {@code spring.jpa.hibernate.ddl-auto: update}, so Hibernate
     * alters the live schema at boot to match the entities -- outside the
     * migration chain, and without ever dropping a column. A mapped field with no
     * migration becomes a column in production that no migration-built database
     * has. Register row C1; {@code is_bargaining_council} (C13) is the instance
     * that arrived on 2026-09-16.
     */
    public static final String PRODUCTION_HIBERNATE_WRITES_THE_SCHEMA =
            "KNOWN DEFECT: production runs ddl-auto: update, so Hibernate alters the live schema "
                    + "outside the migration chain and production drifts from every database "
                    + "the migrations build. This test asserts production only validates.";

    /**
     * {@code ace7297} turned on {@code spring.jpa.show-sql}, which prints every
     * statement to stdout regardless of the {@code org.hibernate.SQL} level set
     * beside it, and the root logger has run at DEBUG since before this suite
     * existed. Register row C11.
     */
    public static final String PRODUCTION_LOGS_EVERY_STATEMENT =
            "KNOWN DEFECT: production echoes every SQL statement (show-sql: true) and runs the "
                    + "root logger at DEBUG, on a service holding identity and bank account "
                    + "numbers. This test asserts neither is on.";

    /**
     * {@code SBV Services (Pty) Ltd} and {@code South African Reserve Bank} were
     * both seeded with the placeholder branch code {@code 000000}, and both carry
     * job grades. The client resolves a bank name to its code and asks for
     * grades by code; {@code JobGradeServiceImpl.findByBankIdentifier} tries the
     * code first -- so choosing either bank offers both banks' grades. Register
     * row A44.
     */
    public static final String BANKS_WITH_GRADES_SHARE_A_BRANCH_CODE =
            "KNOWN DEFECT: two banks that carry job grades share one universal branch code, and "
                    + "grades are looked up by code first, so choosing either bank offers both "
                    + "banks' grades. This test asserts every bank with grades has its own code.";

    /**
     * Twelve seeded members name a bank as no bank on record is named --
     * "Nedbank", "Standard Bank", "Absa Bank" and four more. The server resolves
     * a member's subscription, debit day and job grade by bank NAME, so all
     * three miss for those members. Main's Capitec rename fixed one such name.
     * Register row A46.
     */
    public static final String SEEDED_MEMBERS_NAME_BANKS_NOT_ON_RECORD =
            "KNOWN DEFECT: seeded members name banks that match no bank on record, so every "
                    + "by-name lookup for them (subscription, debit day, job grade) misses. This "
                    + "test asserts every seeded member's bank is on record.";

    /**
     * The welcome letter template's "Your Sasbo Membership No: >>>Number<<" banner
     * (twice) sits in a text box. {@code EmailServiceImpl} replaces placeholders
     * only in {@code doc.getParagraphs()} and {@code doc.getTables()}, which do not
     * include text boxes, so every letter is emailed with the literal placeholder.
     * Register row A55. Read from the attachment's raw document.xml, 2026-09-17.
     */
    public static final String WELCOME_LETTER_LEAVES_THE_NUMBER_PLACEHOLDER =
            "KNOWN DEFECT: the emailed welcome letter still reads \">>>Number<<\" (twice), because the "
                    + "membership-number banner is in a text box and placeholder replacement only visits "
                    + "body paragraphs and tables. This test asserts no placeholder survives.";

    /**
     * {@code EmailServiceImpl} wraps a failed send as "Failed to send welcome letter
     * to &lt;address&gt;"; the catch-all handler copies that into a 500 body, on an
     * endpoint that is anonymous (B1). Register row B8.
     */
    public static final String WELCOME_FAILURE_NAMES_THE_RECIPIENT =
            "KNOWN DEFECT: EmailServiceImpl wraps a failed send as \"Failed to send welcome letter to "
                    + "<address>\", and the catch-all handler copies exception messages into the 500 "
                    + "body of an endpoint anyone may call. This test asserts the error names no "
                    + "address. Register row B8, whose other half is that the endpoint is anonymous.";

    /**
     * Nothing makes {@code members.membership_number} unique, so two members can be
     * stored under one membership number. On 2026-09-16 production held all 160 seeded
     * members twice (ids 1-160 and 161-320) after its tables were dropped (C12) and
     * re-seeded. Register row A48.
     *
     * <p>HALF FIXED on capture-member: V26 adds a partial unique index on
     * {@code identity_number} (SR-5), asserted by {@code MemberIdentityUniquenessIT}, so
     * the same PERSON can no longer be stored twice. The membership-number half is open.
     */
    public static final String DUPLICATE_MEMBERS_ARE_ACCEPTED =
            "KNOWN DEFECT: members.membership_number is not unique in the schema, so two members "
                    + "can be stored under one membership number -- production held every member "
                    + "twice on 2026-09-16. (The identity-number half is fixed by V26.) This test "
                    + "asserts the database refuses a repeated membership number.";

    /**
     * {@code GlobalExceptionHandler} does not extend {@code ResponseEntityExceptionHandler},
     * so its catch-all {@code @ExceptionHandler(Exception.class)} claims Spring MVC's own
     * request-shape exceptions before {@code DefaultHandlerExceptionResolver} can answer
     * them: a non-numeric id, an unreadable body, a missing parameter and an unsupported
     * method all answer 500. Register row A63.
     */
    public static final String MALFORMED_REQUESTS_ANSWER_500 =
            "KNOWN DEFECT: a malformed request -- a non-numeric id, an unreadable body, a missing "
                    + "parameter, an unsupported method -- is claimed by the catch-all and answers 500, "
                    + "so a caller's mistake reads as a server fault. This test asserts 400 (405 for "
                    + "the method).";

    /**
     * Since server 99936a5 the welcome letter is emailed as a PDF that
     * {@code EmailServiceImpl.convertDocxToPdf} rebuilds from the template's BODY
     * paragraphs only, so the six text boxes (the membership-number banner and the
     * whole onboarding page) and every image are not in it. Register row A66.
     */
    public static final String WELCOME_LETTER_PDF_DROPS_THE_TEMPLATE =
            "KNOWN DEFECT: the emailed welcome letter is a PDF rebuilt from the template's body "
                    + "paragraphs only, so the membership-number banner, the onboarding page and every "
                    + "image are missing. This test asserts the PDF carries them.";

    /**
     * The letter PDF is drawn in Standard-14 Times-Roman, whose WinAnsi encoding holds
     * Latin-1 and little more, so a name or address with any other letter -- Tshivenḓa's
     * ḓ and ṱ, Afrikaans ŉ, a Polish Ł -- cannot be written as spelled.
     *
     * <p>⚠️ RE-READ 2026-09-23, AND IT CHANGED SHAPE. It used to THROW, and since server
     * 99936a5 the send is part of the capture (A64), so the member was not captured
     * either. The ACB arc's {@code 848db29} draws every letter through {@code LetterPdf},
     * whose {@code printable()} substitutes '?' for each character the font cannot encode.
     * So the capture now succeeds and the member reads "Nema?a?a" on their own welcome
     * letter -- a LOUD failure became a SILENT one. Nothing records it: the letter's
     * SHA-256 is taken over the TEXT, so the sender's fingerprint matches the mangled PDF,
     * and every ACB letter draws through the same path. Register row A68.
     */
    public static final String WELCOME_LETTER_PDF_CANNOT_WRITE_SOME_NAMES =
            "KNOWN DEFECT: the letter PDF's Standard-14 font cannot encode letters outside Latin-1, so "
                    + "a member whose name holds one (a Tshivenda letter, say) reads '?' in place of it "
                    + "-- silently, since 848db29 substitutes rather than throws. This test asserts the "
                    + "name is written as spelled.";

    /**
     * {@code MemberServiceImpl.applyBankDebitDay} replaces the member's debit day with
     * the bank's subscription day whenever the bank's day is inside 20-31 -- on create,
     * update and updateBanking -- so a day the administrator chose is lost without a
     * word. SASBO, 2026-09-21 (Q42): the day must be flexible. Register row A67.
     */
    public static final String MEMBER_DEBIT_DAY_OVERWRITTEN_BY_THE_BANKS =
            "KNOWN DEFECT: a member's chosen debit day is replaced by the bank's subscription day "
                    + "whenever the bank's day is in range. This test asserts the member's choice stands.";
}
