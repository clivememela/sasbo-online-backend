package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The job-grade outcome reaches the WIRE, not just the entity.
 *
 * <h2>🔴 The defect this exists for, which was mine</h2>
 * 02.5 computed {@code MATCHED} / {@code CAPTURED} / {@code CLEARED} correctly, and
 * {@code JobGradeResolutionTest} asserted it correctly — <b>on the entity</b>. But
 * every member endpoint returns {@code memberMapper.toDto(...)}, {@code MemberDto}
 * had no such field, and {@code MemberMapper} is
 * {@code unmappedTargetPolicy = IGNORE}. So the outcome was computed, asserted, and
 * then dropped in silence at the mapper. The browser would have received a member
 * with no outcome, {@code jobGradeNoticeFor} would have returned null every time,
 * and the user would have been told nothing.
 *
 * <p>🔑 <b>That is the exact defect 02.5 was written to remove, reintroduced one
 * layer up.</b> The unit replaced a silent {@code .orElse(null)} with an outcome
 * that says what happened — and then lost the outcome to a silent
 * {@code unmappedTargetPolicy}. Both failures are the same shape: a value discarded
 * by a default nobody had to write down.
 *
 * <h2>Why a separate test, and why it asserts the WIRE</h2>
 * The service test could not have caught this: it reads the entity the service
 * returns, which is exactly where the value was correct. Nothing that inspects the
 * calculation can see a mapping that drops it. So this asserts the boundary — every
 * outcome the enum can produce survives {@code toDto}.
 *
 * <p>It is written over {@link JobGradeOutcome#values()} rather than over a list of
 * names, so a fourth outcome added later is covered the moment it exists rather than
 * silently untested.
 */
@DisplayName("Job-grade outcome: entity -> DTO")
class MemberJobGradeOutcomeWireTest {

    private final MemberMapper mapper = new MemberMapperImpl();

    @ParameterizedTest
    @EnumSource(JobGradeOutcome.class)
    @DisplayName("every outcome the server can report survives the mapper")
    void everyOutcomeReachesTheDto(JobGradeOutcome outcome) {
        Member member = new Member();
        member.setJobGradeOutcome(outcome);

        MemberDto dto = this.mapper.toDto(member);

        assertThat(dto.getJobGradeOutcome())
                .as("the outcome is the ONLY thing telling the user their typed grade was "
                        + "captured, or could not be saved. Dropped here, the save reports "
                        + "success and says nothing -- which is the defect 02.5 removed")
                .isEqualTo(outcome.name());
    }

    @Test
    @DisplayName("no outcome maps to no outcome, not to a default")
    void absentOutcomeStaysAbsent() {
        // "Nothing was submitted" must not arrive as CLEARED: that would tell the user
        // a field they never touched had been wiped.
        assertThat(this.mapper.toDto(new Member()).getJobGradeOutcome()).isNull();
    }
}
