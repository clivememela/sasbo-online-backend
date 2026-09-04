package za.co.titandynamix.sasbo_online_backend.fixtures;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;

import java.time.LocalDateTime;

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
     */
    public static final String SYNTHETIC_ID = "9404275800086";

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
        member.setIdentityNumber(SYNTHETIC_ID);
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
