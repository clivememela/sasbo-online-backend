package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 A61 — EVERY SEEDED MEMBER LOST ITS MEMBERSHIP NUMBER, AND NOTHING SAID SO.
 *
 * <p>The register's "Membership no." column was blank for all 160 seeded members. Found by the
 * automation arc from a failing run's page snapshot — a Playwright {@code fill} received
 * {@code null}, and {@code typeof null === "object"} is what finally surfaced it.
 *
 * <h2>The mechanism, and why it is invisible</h2>
 * <ol>
 *   <li>{@code data/members.json} carries all 160 numbers ({@code M-100001}…), unique.</li>
 *   <li>{@code Member.membershipNumber} is {@code @JsonProperty(access = READ_ONLY)}.</li>
 *   <li>{@code DataSeeder} reads that file <b>into {@code Member} entities through Jackson</b>, so
 *       READ_ONLY makes Jackson discard all 160 on the way in — silently, because discarding a
 *       read-only property on deserialisation is exactly what that annotation asks for.</li>
 *   <li>{@code saveAll()} then persists 160 members with a null membership number, and
 *       {@code V22} derives the sequence from {@code MAX} of existing {@code M-} numbers, of
 *       which there are now none.</li>
 * </ol>
 *
 * <h2>⚠️ THE ANNOTATION IS CORRECT AND MUST STAY</h2>
 * {@code MemberController} binds {@code @RequestBody Member} — the entity itself — on POST, PUT
 * and PATCH. Without READ_ONLY a caller could supply a membership number and aim a write at
 * another member's row. So the defect is not the annotation; it is that <b>the seeder is not an
 * API caller and must not read the server's own seed file through the request-body rules</b>.
 * Loosening the entity to fix the seeder would trade a blank column for a write-targeting hole.
 *
 * <h2>Why this is a unit test and not an IT</h2>
 * The defect is entirely in the READ, before any database is involved, so a Postgres container
 * would add minutes and prove nothing extra. {@code BankSeederIT} covers the persistence half of
 * seeding; this covers the parse.
 */
class SeedMemberReaderTest {

    private List<Member> read() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("data/members.json")) {
            assertThat(stream).as("data/members.json must be on the test classpath").isNotNull();
            return DataSeeder.readSeedMembers(stream);
        }
    }

    @Test
    @DisplayName("A61: every seeded member keeps the membership number the seed file gives it")
    void everySeededMemberKeepsItsMembershipNumber() throws Exception {
        List<Member> members = read();

        List<Member> missing = members.stream()
                .filter(m -> m.getMembershipNumber() == null || m.getMembershipNumber().isBlank())
                .collect(Collectors.toList());

        assertThat(missing)
                .as("A61: the seed file carries a membership number for every member. A member "
                    + "read without one reaches the register as a blank cell, and V22's sequence "
                    + "derives its start from MAX of the numbers that made it in.")
                .isEmpty();
    }

    @Test
    @DisplayName("and they are the file's own numbers, not invented ones")
    void theNumbersAreTheOnesInTheFile() throws Exception {
        List<Member> members = read();

        // The non-vacuity half. "Not blank" would be satisfied by a reader that minted a
        // placeholder for everybody; these are the values the file actually holds.
        assertThat(members).extracting(Member::getMembershipNumber)
                .as("the first three of the seed file, in order")
                .startsWith("M-100001", "M-100002", "M-100003");
    }

    @Test
    @DisplayName("and no two members share one — the register keys on it")
    void theNumbersAreUnique() throws Exception {
        List<Member> members = read();
        Set<String> distinct = members.stream().map(Member::getMembershipNumber).collect(Collectors.toSet());

        assertThat(distinct)
                .as("A48's other half is that membership numbers are NOT constrained in the "
                    + "database, so a duplicate here would persist and could not be removed (D9).")
                .hasSize(members.size());
    }

    @Test
    @DisplayName("🔑 the must-pass: the rest of the member still binds, so the reader was not simply loosened")
    void everythingElseStillBinds() throws Exception {
        List<Member> members = read();

        // If this reader had been "fixed" by disabling annotations wholesale, the fields that
        // depend on them would break. Asserted on ordinary fields and on the count.
        assertThat(members).as("the seed file's full population").hasSize(160);
        assertThat(members).allSatisfy(m ->
                assertThat(m.getSurname()).as("surname still binds").isNotBlank());
    }

    @Test
    @DisplayName("🔑 and `id` is still NOT bound from the file — READ_ONLY still does its job")
    void idIsStillNotBound() throws Exception {
        // The guard on the fix itself. `Member.id` carries the same READ_ONLY annotation for the
        // same reason, and the seeder nulls it immediately afterwards precisely because a seed
        // file must not choose primary keys. If a future edit widens the reader far enough to
        // bind `id`, that is a different defect wearing this one's clothes, and this reddens.
        assertThat(read()).allSatisfy(m ->
                assertThat(m.getId()).as("the seed file must not choose primary keys").isNull());
    }
}
