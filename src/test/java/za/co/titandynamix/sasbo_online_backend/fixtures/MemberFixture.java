package za.co.titandynamix.sasbo_online_backend.fixtures;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The member and representative domains' fixture builders.
 *
 * <h2>Synthetic identity numbers only</h2>
 * Every identity number here is invented. Coverage reports, failure output,
 * stack traces and CI artefacts all get forwarded and pasted into tickets, so a
 * real South African ID in a fixture is a privacy breach with a URL attached --
 * not a tidiness question.
 *
 * <p>The numbers are still <b>well-formed</b>: thirteen digits, a real date, a
 * sequence on the correct side of the 5000 gender boundary. A malformed one
 * would make a test pass for the wrong reason, because the parser rejects it
 * before reaching whatever was being tested.
 *
 * <h2>Collation-neutral surnames</h2>
 * Plainly separated ASCII, no leading lowercase particle ({@code de Villiers},
 * {@code van Zyl}), no apostrophe or hyphen ({@code O'Brien}, {@code Smit-Nel}),
 * no diacritic, no case-only pair. Those are exactly the names {@code C},
 * {@code en_ZA.UTF-8} and {@code en_US.UTF-8} disagree about -- and they are
 * ordinary South African surnames, which is why this needs a rule and not good
 * intentions. A sort test proves the {@code ORDER BY} clause; the names are an
 * implementation detail of that proof.
 */
public final class MemberFixture {

    /** Four surnames whose order all three collations agree on. */
    public static final String[] COLLATION_NEUTRAL_SURNAMES = {
        "Abrahams", "Botha", "Khumalo", "Naidoo",
    };

    /**
     * Synthetic. Thirteen digits, 27 April 1994, sequence 5800 -- which is on
     * the male side of the 5000 boundary the parser uses. Stated here because
     * a test that expects the other answer looks correct until it runs.
     *
     * <p>🔴 <b>ITS VALUE CHANGED WITH SR-5, from {@code ...086} to whatever
     * {@link #syntheticIdFor} computes for sequence 0 -- and the old one was not
     * a valid South African identity number.</b> Run the Luhn check over
     * {@code 9404275800086} and it comes to 57, not a multiple of ten; the check
     * digit should have been 9. Nothing on the server verifies the checksum, so
     * it never mattered here -- but {@code validateSAID.js} on the client does,
     * and the fixture had been handing the back end a number the front end would
     * have refused to send. Now every fixture identity number passes both.
     */
    public static final String SYNTHETIC_ID = syntheticIdFor(0);

    /**
     * The next distinct identity number, so two fixture members are two people.
     *
     * <p>🔴 <b>THIS IS WHAT BLOCKED SR-5, AND SR-5 IS WHY IT EXISTS.</b> There is
     * no {@code UNIQUE} on {@code members.identity_number}, so a duplicate member
     * is possible -- and with no {@code DELETE} on this API (D9) it is permanent.
     * The constraint is one migration. What made it "not one migration" is that
     * every member this fixture built carried the SAME identity number, across
     * <b>nine test files</b> and <b>23 save sites in {@code MemberRepositoryIT}
     * alone</b>, including {@code seedTo(5)} / {@code seedTo(50)}. Adding the
     * constraint without this reddens all of them, at which point the constraint
     * looks like the problem and gets reverted.
     *
     * <p>🔑 <b>MINTED HERE RATHER THAN PASSED IN BY 23 CALL SITES.</b> The plan
     * proposed {@code syntheticIdFor(seq)} plus a call-site sweep. A sweep is 23
     * chances to reuse a number by accident and it has to be repeated by every
     * test written afterwards; a counter inside {@code withSurname} makes
     * "distinct" a property of the fixture instead of a habit of its callers.
     * The sequence stays available for the tests that need a NAMED, stable value
     * -- proving a duplicate is rejected needs the same number twice, on purpose.
     *
     * <p>⚠️ The counter is per JVM and therefore order-dependent. That is fine and
     * deliberate: what the constraint requires is DISTINCTNESS, which holds under
     * any order. No test asserts a fixture identity number by literal -- the two
     * that name one go through {@link #SYNTHETIC_ID}, and the handler tests carry
     * their own strings.
     */
    private static final AtomicInteger NEXT_SEQUENCE = new AtomicInteger(0);

    /**
     * A valid synthetic South African identity number for {@code sequence}.
     *
     * <p>{@code YYMMDD} is fixed at 27 April 1994 so the date-of-birth derivation
     * has one answer; only the four gender/sequence digits move, and they start at
     * <b>5800 -- above the 5000 boundary</b>, so every generated number stays MALE
     * and {@code createDerivesFromTheIdentityNumber} keeps its expected answer. The
     * citizenship digit stays {@code 0} (SA citizen) and the eleventh stays
     * {@code 8}; the thirteenth is <b>computed</b>, by Luhn, rather than carried
     * over -- which is the whole point, since changing any earlier digit invalidates
     * the one that was there.
     *
     * @param sequence 0-based; 4199 distinct numbers before it would wrap
     */
    public static String syntheticIdFor(int sequence) {
        int genderDigits = 5800 + Math.floorMod(sequence, 4200);
        String withoutCheck = "940427" + genderDigits + "08";
        return withoutCheck + luhnCheckDigit(withoutCheck);
    }

    /**
     * The thirteenth digit: whatever makes the whole number a multiple of ten
     * under Luhn.
     *
     * <p>Written out rather than pulled from a library because the test tree has
     * no dependency that offers it, and because a fixture that generates invalid
     * identity numbers is worse than one that repeats a valid one -- the failure
     * would surface later, as a validation error in a test about something else.
     * {@code MemberFixtureTest} checks it against the two numbers this repository
     * already has an independent opinion about.
     */
    private static int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubling = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return Math.floorMod(-sum, 10);
    }

    /** Synthetic. A South African account number is 11 digits; this one is invented. */
    public static final String SYNTHETIC_ACCOUNT = "62001234567";

    /** Fixed. Nothing here reads the wall clock. */
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 1, 15, 8, 0);

    private MemberFixture() {
    }

    /** A valid, unsaved member. Override only the field the test is about. */
    public static Member aMember() {
        return withSurname("Khumalo");
    }

    public static Member withSurname(String surname) {
        Member member = new Member();
        member.setFirstNames("Thandi");
        member.setSurname(surname);
        // A DISTINCT number per member, not the shared constant -- see
        // NEXT_SEQUENCE for why, and for what it unblocked.
        member.setIdentityNumber(syntheticIdFor(NEXT_SEQUENCE.getAndIncrement()));
        member.setPaymentType("Payroll");
        member.setStatus("ACTIVE");
        member.setIsRepresentative(Boolean.FALSE);
        return member;
    }

    /** A member flagged as a representative, in the given status. */
    public static Member representativeMember(String surname, String status) {
        Member member = withSurname(surname);
        member.setIsRepresentative(Boolean.TRUE);
        member.setStatus(status);
        return member;
    }

    /**
     * A valid, unsaved representative.
     *
     * <p>{@code createdAt} is set explicitly: unlike {@code Member}, this entity
     * has no {@code @PrePersist} and the column is {@code nullable = false}, so
     * a fixture that forgets it fails at flush with a constraint error that
     * says nothing about the test.
     */
    public static Representative representative(String firstNames, String surname, Member member) {
        Representative representative = new Representative();
        representative.setFirstNames(firstNames);
        representative.setSurname(surname);
        representative.setMember(member);
        representative.setIsElectedRep(Boolean.FALSE);
        representative.setCreatedAt(CREATED);
        representative.setUpdatedAt(CREATED);
        return representative;
    }

    public static RepresentativeRole role(String name) {
        RepresentativeRole role = new RepresentativeRole();
        role.setName(name);
        role.setRequiresStructure(Boolean.FALSE);
        role.setIsElectedRole(Boolean.FALSE);
        return role;
    }

    public static UnionStructure structure(String name) {
        UnionStructure structure = new UnionStructure();
        structure.setName(name);
        structure.setRequiresElectionDate(Boolean.FALSE);
        return structure;
    }
}
