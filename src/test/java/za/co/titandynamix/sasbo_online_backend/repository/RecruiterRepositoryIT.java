package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RecruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc} against the real migration chain.
 *
 * <h2>Why this needs a real database and not a mock</h2>
 * It is the lookup the capture form's Recruiter field resolves through — a NAME on
 * the wire, a row in {@code recruiters}, and {@code members.recruiter_id} pointing
 * at it since V25. Three things have to be true at once and only Postgres can say
 * so: the derived query has to compile, the case-insensitive match has to behave the
 * way the browser assumes, and the foreign key has to accept the id.
 *
 * <p>🔴 The defect this closes is worth restating, because it survived a mock-tested
 * service for a long time: {@code resolveRecruiter} used to look names up in
 * {@code representatives}, a table seeded by no migration and no seeder. Every
 * lookup returned empty, every capture stored a null recruiter, and every unit test
 * passed — because a mock repository returns whatever the test tells it to. Only a
 * real schema with real seed data makes "this table has nothing in it" visible.
 */
@DisplayName("RecruiterRepository against the real migration chain")
class RecruiterRepositoryIT extends AbstractWritingIT {

    @Autowired
    private RecruiterRepository recruiterRepository;

    private Recruiter given(String name) {
        Recruiter recruiter = new Recruiter();
        recruiter.setName(name);
        recruiter.setMembersRecruited(0);
        return this.recruiterRepository.saveAndFlush(recruiter);
    }

    @Test
    @DisplayName("finds a recruiter by the exact name the form submitted")
    void findsByExactName() {
        given("Testperson Recruiter");

        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Testperson Recruiter"))
                .isPresent()
                .get()
                .extracting(Recruiter::getName)
                .isEqualTo("Testperson Recruiter");
    }

    @Test
    @DisplayName("🔴 ignores case — the seed is SHOUTED and the drop-down is not")
    void ignoresCase() {
        // ⚠️ SYNTHETIC NAMES, and that is not fussiness. The 33 seeded recruiters are
        // present in this lane, and the first draft of this test inserted "Wendy
        // Maluleke" beside the seed's "WENDY MALULEKE" -- two case-insensitive
        // matches, NonUniqueResultException, red. That collision is what exposed the
        // three "Vacant" rows in the seed, so the accident was worth more than the
        // test. See RecruiterSeedTest.
        //
        // recruiters.json holds names in capitals. Whatever the browser
        // sends back is whatever the drop-down rendered, and the two must match
        // regardless. A case-sensitive lookup here would drop the recruiter on every
        // capture, silently, exactly as the old representatives lookup did.
        given("SHOUTED TESTPERSON");

        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("shouted testperson")).isPresent();
        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Shouted Testperson")).isPresent();
    }

    @Test
    @DisplayName("answers empty for a name that is not a recruiter, rather than guessing")
    void answersEmptyForAnUnknownName() {
        given("Testperson Recruiter");

        // No fuzzy matching. A near-miss must not resolve to a different person --
        // this is the record of WHO recruited a member, and a helpful guess here is
        // a wrong answer that nothing downstream can detect.
        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Testperson")).isEmpty();
        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Testperson Recruiter Jr")).isEmpty();
        assertThat(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Nobody At All")).isEmpty();
    }

    @Test
    @DisplayName("the canary — the finder is looking at rows that exist")
    void theFinderIsNonVacuous() {
        // Every assertion above is "isPresent" or "isEmpty" over one table. If saving
        // silently did nothing, the isEmpty cases would still pass and the isPresent
        // ones would be the only thing standing between this file and vacuity. Stated
        // directly so the failure names the cause rather than the symptom.
        given("Another Testperson");

        assertThat(this.recruiterRepository.findAll())
                .as("the recruiters table accepted a write")
                .isNotEmpty();
    }
}
