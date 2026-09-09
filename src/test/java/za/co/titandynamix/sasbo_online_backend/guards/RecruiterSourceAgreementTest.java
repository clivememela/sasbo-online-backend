package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recruiter drop-down and the recruiter column must read the same thing.
 *
 * <h2>🔴 The defect this exists for, which arrived from upstream</h2>
 * {@code origin/main}'s {@code 4e41ed5} changed {@code RepresentativeServiceImpl.findAll()} to
 * derive representatives from the <b>members</b> table:
 *
 * <pre>{@code this.memberRepository.findByIsRepresentativeTrue()}</pre>
 *
 * <p>That endpoint fills the capture form's <b>Recruiter</b> drop-down. But the value it
 * produces has to resolve into
 * {@code members.recruitment_representative_id → representatives.id}, and
 * {@code MemberServiceImpl.resolveRecruiter} looks the name up in the
 * <b>representatives</b> table.
 *
 * <p><b>Two different tables.</b> A member flagged {@code is_representative = true} with no row
 * in {@code representatives} is OFFERED by the drop-down and CANNOT BE RESOLVED on save — so
 * the recruiter is dropped and the capture reports success. That is A31's class and R5's exact
 * defect, re-created from the other side of the wire hours after 03.3 fixed it.
 *
 * <h2>Why this is a SOURCE scan and not a data test</h2>
 * A data test would need both a member-without-a-representative-row and a fixture that proves
 * the drop-down offered them — and would pass on any dataset where the two happen to agree,
 * which is most of them. The divergence is <b>structural</b>: it is a property of which
 * repository each side calls, and it is true whether or not today's rows expose it.
 *
 * <p>🔑 So this guard does not assert the two are the same. <b>It asserts that they are known to
 * differ</b>, and fails the day someone changes one without the other — in either direction. It
 * is a tripwire on a decision that has not been made yet, not a claim that the code is correct.
 *
 * <h2>What closing this looks like</h2>
 * Unit {@code 03.10}. It is a MODELLING decision — which table is the source of truth for "a
 * representative" — and picking one unilaterally is how the two concepts got out of step to
 * begin with. When it is settled, this guard is deleted along with the divergence.
 */
@DisplayName("Recruiter source agreement (03.10 — open)")
class RecruiterSourceAgreementTest {

    private static MainClassSource source(String name) {
        return MainSourceInventory.all().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        name + " not found. If it was renamed, this guard needs re-aiming rather "
                                + "than deleting: the divergence it tracks is still open."));
    }

    @Test
    @DisplayName("🔴 the two sides still read different tables — 03.10 is still open")
    void theDivergenceIsStillThere() {
        // Stated as the CURRENT truth. When 03.10 lands, this test fails and is deleted with
        // the divergence -- which is the correct way for a tripwire to end its life.
        MainClassSource representatives = source("RepresentativeServiceImpl");
        MainClassSource members = source("MemberServiceImpl");

        assertThat(representatives.contains("findByIsRepresentativeTrue"))
                .as("the drop-down's source: /api/representatives derives its list from the "
                        + "MEMBERS table (origin/main 4e41ed5)")
                .isTrue();

        assertThat(members.contains("representativeRepository.findByFullName"))
                .as("the column's source: resolveRecruiter looks the submitted name up in the "
                        + "REPRESENTATIVES table, which is what the FK points at")
                .isTrue();
    }

    @Test
    @DisplayName("the recruiter is still resolved by NAME, which is what makes the mismatch reachable")
    void theRecruiterIsResolvedByName() {
        // If the wire ever carried an ID instead of a name, the mismatch above stops being
        // reachable through the form and this guard would be describing a hazard that no
        // longer exists. Asserted so that change is noticed rather than assumed.
        assertThat(source("MemberServiceImpl").contains("member.getRecruitmentRep()"))
                .as("the capture form submits a recruiter NAME, and the server resolves it")
                .isTrue();
    }
}
