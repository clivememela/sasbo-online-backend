package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.workplace.Workplace;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceEntityRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A member's workplace (month end 01.3): what a save names, resolved or refused before anything is written; and what
 * an answer says of it, as the deployment's switch allows (MD62). Every workplace here is invented (MD57).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberWorkplaces")
class MemberWorkplacesTest {

    @Mock
    private WorkplaceEntityRepository entities;

    @Mock
    private WorkplaceService lineages;

    private MemberWorkplaces at(String mode) {
        return new MemberWorkplaces(entities, lineages, new WorkplaceSwitch(mode));
    }

    private static Workplace workplace(long id) {
        Workplace workplace = new Workplace();
        workplace.setId(id);
        workplace.setRaNumber(900000 + (int) id);
        workplace.setName("INVENTED BRANCH " + id);
        return workplace;
    }

    private static Member standingAt(Workplace workplace) {
        Member member = new Member();
        member.setWorkplace(workplace);
        return member;
    }

    private static MemberDto answerAt(long workplaceId) {
        MemberDto answer = new MemberDto();
        answer.setWorkplaceId(workplaceId);
        answer.setWorkplaceName("INVENTED BRANCH " + workplaceId);
        answer.setWorkplaceRaNumber(900000 + (int) workplaceId);
        answer.setWorkplace(lineage(workplaceId));
        return answer;
    }

    private static WorkplaceDto lineage(long id) {
        return new WorkplaceDto(id, 900000 + (int) id, "INVENTED BRANCH " + id, "INVENTED BANK", 7L, "INVENTED BANK",
                "INVENTED GROUP", 902, "NAME", "A", "NORTH", "Q01", "INVENTED COMMITTEE", null, null);
    }

    @Test
    @DisplayName("MD58: a save that names no workplace leaves the member's as it was, and asks nothing")
    void noneNamedLeavesItAsItWas() {
        Workplace before = workplace(1);
        Member member = standingAt(before);

        at("all").resolve(null, member);

        assertThat(member.getWorkplace()).isSameAs(before);
        verifyNoInteractions(entities);
    }

    @Test
    @DisplayName("a workplace on record is the member's")
    void aWorkplaceOnRecord() {
        Workplace named = workplace(5);
        when(entities.findById(5L)).thenReturn(Optional.of(named));
        Member member = standingAt(workplace(1));

        at("all").resolve(5L, member);

        assertThat(member.getWorkplace()).isSameAs(named);
    }

    @Test
    @DisplayName("ME-12: a workplace not on record is WORKPLACE_UNKNOWN, naming the id -- and the member is untouched")
    void aWorkplaceNotOnRecord() {
        when(entities.findById(9L)).thenReturn(Optional.empty());
        Workplace before = workplace(1);
        Member member = standingAt(before);

        assertThatThrownBy(() -> at("names").resolve(9L, member))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_UNKNOWN);
                    assertThat(refusal.details()).isEqualTo(Map.of("workplaceId", "9"));
                });
        assertThat(member.getWorkplace()).isSameAs(before);
    }

    @Test
    @DisplayName("MD62: where the switch serves none, any workplace is WORKPLACE_UNKNOWN -- and the table is not asked")
    void servedNoneRefusesAny() {
        Member member = new Member();

        assertThatThrownBy(() -> at("").resolve(5L, member))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_UNKNOWN));
        assertThat(member.getWorkplace()).isNull();
        verifyNoInteractions(entities);
    }

    @Test
    @DisplayName("MD62: in a list, the three scalars where the switch serves workplaces, none where it does not -- and never a lineage")
    void inAListAsTheSwitchAllows() {
        MemberDto named = answerAt(5);
        at("names").inAList(named);
        assertThat(named.getWorkplaceId()).isEqualTo(5L);
        assertThat(named.getWorkplaceName()).isEqualTo("INVENTED BRANCH 5");
        assertThat(named.getWorkplaceRaNumber()).isEqualTo(900005);
        assertThat(named.getWorkplace()).as("a list carries three scalars and no more").isNull();

        MemberDto none = answerAt(5);
        at("").inAList(none);
        assertThat(none.getWorkplaceId()).isNull();
        assertThat(none.getWorkplaceName()).isNull();
        assertThat(none.getWorkplaceRaNumber()).isNull();
        assertThat(none.getWorkplace()).isNull();
        verifyNoInteractions(lineages);
    }

    @Test
    @DisplayName("the answer for one member carries the workplace's lineage; where the switch serves none, nothing is asked or said")
    void forOneCarriesTheLineage() {
        when(lineages.one(5L)).thenReturn(Optional.of(lineage(5)));
        MemberDto one = answerAt(5);
        one.setWorkplace(null);

        at("all").forOne(one);

        assertThat(one.getWorkplace()).isEqualTo(lineage(5));

        MemberDto none = answerAt(5);
        at("").forOne(none);
        assertThat(none.getWorkplaceId()).isNull();
        assertThat(none.getWorkplace()).isNull();
    }

    @Test
    @DisplayName("a member with no workplace is answered with none, and no lineage is asked for")
    void noWorkplaceNoLineage() {
        MemberDto answer = new MemberDto();

        at("all").forOne(answer);

        assertThat(answer.getWorkplaceId()).isNull();
        assertThat(answer.getWorkplace()).isNull();
        verifyNoInteractions(lineages);
    }
}
