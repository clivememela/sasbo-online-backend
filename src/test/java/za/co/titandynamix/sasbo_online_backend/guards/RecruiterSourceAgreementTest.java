package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ✅ 03.10 CLOSED — the recruiter drop-down and the recruiter column read the SAME
 * table, and it is the one that actually holds recruiters.
 *
 * <h2>What this file used to be</h2>
 * A tripwire. {@code /api/representatives} fed the drop-down while
 * {@code MemberServiceImpl} resolved the submitted name in a different table, so it
 * asserted that the two were <b>known to differ</b> and failed the day either side
 * moved. That was the correct shape for an open modelling question.
 *
 * <h2>🔴 What measuring it actually found</h2>
 * The question "which of the two tables is the source of truth" had the wrong shape.
 * There were <b>four disjoint people-lists sharing no names at all</b>:
 *
 * <ul>
 *   <li>{@code representatives} — seeded by no migration and no seeder. Empty.</li>
 *   <li>{@code members.is_representative} — the key is <b>absent from
 *       members.json entirely</b>, so {@code findByIsRepresentativeTrue()} returns
 *       nothing and {@code /api/representatives} answers {@code []}.</li>
 *   <li>{@code recruiters} — <b>33 real union names</b> with recruited-counts,
 *       referenced by no foreign key, served by no controller.</li>
 *   <li>{@code members.areas_rep} — 62 distinct names matching none of the above.</li>
 * </ul>
 *
 * <p><b>So the drop-down was empty for every user, whichever table it read.</b> It
 * was not two sources disagreeing; it was a field with no source, next to a table
 * of real recruiters that nothing could reach.
 *
 * <h2>🗣️ The decision, 2026-09-09</h2>
 * A <b>recruiter</b> signed the member up and lives in {@code recruiters}. A
 * <b>representative</b> sits on a structure and stays Edit Member's business,
 * administered through {@code /api/representatives}. One column had been serving
 * both concepts, which is why neither worked.
 *
 * <p>So this guard inverts: it now asserts the two sides <b>agree</b>, and it will
 * fail if anyone re-points either half at the other table.
 */
@DisplayName("Recruiter source agreement (03.10 — closed)")
class RecruiterSourceAgreementTest {

    private static List<String> SEEDED_RECRUITERS;

    @BeforeAll
    static void loadSeed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> names = new ArrayList<>();
        try (InputStream in = RecruiterSourceAgreementTest.class
                .getResourceAsStream("/data/recruiters.json")) {
            assertThat(in).as("the recruiter seed is on the classpath").isNotNull();
            for (JsonNode n : mapper.readTree(in)) {
                names.add(n.path("name").asText(null));
            }
        }
        SEEDED_RECRUITERS = List.copyOf(names);
    }

    private static MainClassSource source(String name) {
        return MainSourceInventory.all().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        name + " not found. If it was renamed, this guard needs re-aiming "
                                + "rather than deleting: it is what stops the recruiter's two "
                                + "sides drifting apart again."));
    }

    /**
     * Does this source resolve a recruiter name through the {@code recruiters} table?
     *
     * <p>♻️ A PATTERN, NOT A LITERAL, and the reason is on the record. The previous
     * version of this guard matched {@code representativeRepository.findByFullName}
     * exactly, and a {@code /simplify} pass rewrote that call as a method reference —
     * same repository, same finder, <b>two characters</b> — and the guard went red for
     * a change that altered nothing. A source guard pinned to one spelling is pinned
     * to an accident, so this accepts either separator and
     * {@link #thePredicateCanTellTheDifference} proves it still discriminates.
     */
    private static boolean resolvesThroughRecruiters(String code) {
        return Pattern.compile("recruiterRepository\\s*(?:\\.|::)\\s*findFirstByNameIgnoreCase")
                .matcher(code)
                .find();
    }

    @Test
    @DisplayName("✅ both sides read the recruiters table")
    void theTwoSidesAgree() {
        // ⚠️ RE-AIMED ONCE MORE, IN THE SAME COMMIT THAT WROTE IT, and the reason is
        // this guard's whole subject. The first draft asserted the CONTROLLER touches
        // `recruiterRepository` -- then LayeringTest correctly refused a controller
        // that queries a repository, the query moved into the service, and this went
        // red for a change that altered nothing about the divergence it tracks. It
        // asks about the LAYER that owns the query now, and about the controller only
        // that it delegates.
        assertThat(source("RecruiterServiceImpl").contains("recruiterRepository"))
                .as("the drop-down's source: /api/recruiters is served from the recruiters table")
                .isTrue();
        assertThat(source("RecruiterController").contains("recruiterService"))
                .as("and the controller goes through the service, per LayeringTest")
                .isTrue();

        assertThat(resolvesThroughRecruiters(source("MemberServiceImpl").code()))
                .as("the column's source: resolveRecruiter looks the submitted name up in "
                        + "the recruiters table, which is what members.recruiter_id points at")
                .isTrue();
    }

    @Test
    @DisplayName("the representative concept is still separate, and still served")
    void representativesAreStillTheirOwnThing() {
        // 🔑 The half that keeps the fix honest. "Make the two agree" is trivially
        // satisfiable by deleting one of them -- and deleting /api/representatives
        // would take the structures screen with it, which the stakeholder keeps.
        MainClassSource representatives = source("RepresentativeServiceImpl");

        assertThat(representatives.contains("findByIsRepresentativeTrue"))
                .as("/api/representatives still answers the structures question")
                .isTrue();
        assertThat(resolvesThroughRecruiters(representatives.code()))
                .as("and it is NOT quietly reading recruiters -- two concepts, two sources")
                .isFalse();
    }

    @Test
    @DisplayName("🔴 the drop-down is no longer empty — 33 recruiters are seeded")
    void theSeedActuallyHasRecruiters() {
        // The defect that started this: a correctly-wired drop-down over an empty
        // table is still an empty drop-down, and every previous candidate source was
        // empty. This asserts the DATA, because the wiring was never the whole story.
        assertThat(SEEDED_RECRUITERS)
                .as("recruiters.json is what fills the capture form's Recruiter field")
                .hasSizeGreaterThan(20)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("the guard's own canary — the predicate rejects the other tables")
    void thePredicateCanTellTheDifference() {
        assertThat(resolvesThroughRecruiters("this.recruiterRepository.findFirstByNameIgnoreCase(n)"))
                .as("a plain call").isTrue();
        assertThat(resolvesThroughRecruiters("this.recruiterRepository::findFirstByNameIgnoreCase"))
                .as("a method reference").isTrue();

        // The half that makes the two above mean something: the shapes 03.10 rejected
        // must read as "no", or this guard would keep passing after someone pointed
        // the resolution back at either of the empty tables.
        assertThat(resolvesThroughRecruiters("this.representativeRepository.findByFullName(n)"))
                .as("the representatives table -- what it used to do").isFalse();
        assertThat(resolvesThroughRecruiters("this.memberRepository.findFirstByNameIgnoreCase(n)"))
                .as("the members table -- the other candidate 03.10 considered").isFalse();
        assertThat(resolvesThroughRecruiters("this.recruiterRepository.findById(id)"))
                .as("the right repository used for something else").isFalse();
    }
}
