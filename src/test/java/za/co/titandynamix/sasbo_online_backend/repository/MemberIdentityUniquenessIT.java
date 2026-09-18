package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SR-5 -- THE SAME PERSON CANNOT BE CAPTURED TWICE, PROVED IN THE DATABASE.
 *
 * <h2>Why this cannot be a unit test</h2>
 * The claim is about a partial unique index that {@code V26} creates in
 * PostgreSQL. A mocked repository would agree with whatever the test author
 * expected, and a Spring context with {@code ddl-auto} generating the schema
 * would be testing Hibernate's opinion of the entity rather than the migration
 * that actually runs against the production database. This extends
 * {@code AbstractWritingIT}, so it is the real migration chain, V1 to V26, in a
 * real PostgreSQL.
 *
 * <p>🔑 That is not a preference. This arc's two worst defects -- a resolver that
 * had been reading an empty table since 03.3, and a lookup that threw on a value
 * the screen itself offered -- were both green under mocks for days.
 *
 * <h2>Both directions, because only one of them is the interesting failure</h2>
 * A constraint that rejects everything also rejects duplicates. The accepted
 * cases below are what tell "duplicates are refused" apart from "saving a member
 * is broken", and the passport case is what tells it apart from "every member
 * must now have an identity number" -- which would be a regression against the
 * spec's own P3 rule, on a screen where Passport is a supported choice.
 */
@DisplayName("members.identity_number is unique (SR-5, V26)")
class MemberIdentityUniquenessIT extends AbstractWritingIT {

    @Autowired
    private MemberRepository memberRepository;

    /** A member carrying exactly this identity number, saved. */
    private Member savedWithIdentity(String surname, String identityNumber) {
        Member member = MemberFixture.withSurname(surname);
        member.setIdentityNumber(identityNumber);
        return this.memberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("🔴 a second member with the same identity number is REFUSED")
    void aDuplicateIdentityNumberIsRefused() {
        // The sequence is used on purpose here. Every fixture member now gets its
        // own number, which is what let V26 exist at all -- so proving a collision
        // is refused requires asking for the same number twice, deliberately.
        String shared = MemberFixture.syntheticIdFor(9001);

        savedWithIdentity("Khumalo", shared);

        // saveAndFlush, not save: without the flush the insert sits in the
        // persistence context until the transaction ends and the exception is
        // raised somewhere this assertion cannot see it -- the test would pass for
        // the wrong reason and the rollback would hide the evidence.
        assertThatThrownBy(() -> savedWithIdentity("Naidoo", shared))
                .as("the database is the only place this rule can hold -- CM-16's "
                        + "browser check passes for two administrators at two desks")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("...and two DIFFERENT members still save, which is the half that can fail quietly")
    void twoDistinctMembersStillSave() {
        assertThatCode(() -> {
            savedWithIdentity("Abrahams", MemberFixture.syntheticIdFor(9101));
            savedWithIdentity("Botha", MemberFixture.syntheticIdFor(9102));
        }).doesNotThrowAnyException();

        assertThat(this.memberRepository.findAll())
                .extracting(Member::getIdentityNumber)
                .contains(MemberFixture.syntheticIdFor(9101), MemberFixture.syntheticIdFor(9102));
    }

    @Test
    @DisplayName("🔴 passport holders have NO identity number, and there may be many of them")
    void manyMembersWithoutAnIdentityNumberAreFine() {
        // The reason V26's index is PARTIAL. "No South African identity number" is
        // a legitimate, repeatable state -- it is exactly why the spec's P3 rule
        // makes Date of Birth required once Passport is selected. A plain UNIQUE
        // column would make the second passport holder a duplicate of the first,
        // and the screen would say "this member already exists" about two people
        // who share nothing at all.
        assertThatCode(() -> {
            savedWithIdentity("Khumalo", null);
            savedWithIdentity("Naidoo", null);
        }).as("NULL: distinct by definition in a unique index").doesNotThrowAnyException();

        // ⚠️ AND THE EMPTY STRING, WHICH IS NOT THE SAME CASE AND IS THE ONE THAT
        // BITES. The capture form sends "" for a field left alone, and to a unique
        // index "" is an ordinary value that collides with itself. Without the
        // `<> ''` clause in V26 this is the assertion that goes red -- which is
        // precisely why it is here and not left to NULL to stand for both.
        assertThatCode(() -> {
            savedWithIdentity("Abrahams", "");
            savedWithIdentity("Botha", "");
        }).as("the empty string, which the form really does send").doesNotThrowAnyException();
    }
}
