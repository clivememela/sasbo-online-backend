package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberChange;
import za.co.titandynamix.sasbo_online_backend.entity.workplace.Workplace;
import za.co.titandynamix.sasbo_online_backend.repository.MemberChangeRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * What a save writes to the member's history (month end 03.5): one row a changed field, one id a save, masked
 * numbers, the injected clock's instant -- and nothing for a save that changed nothing.
 */
@DisplayName("MemberTrail")
class MemberTrailTest {

    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    private final MemberChangeRepository changes = mock(MemberChangeRepository.class);
    private final MemberTrail trail = new MemberTrail(changes, Clock.fixed(NOW, ZoneOffset.UTC));

    private static Member member() {
        Member member = new Member();
        member.setId(7);
        member.setMembershipNumber("M-900001");
        member.setSurname("Invented");
        member.setIdentityNumber("8001015009087");
        member.setBankAccountNumber("99991234567");
        member.setCellTel("+27 60 555 0001");
        return member;
    }

    @SuppressWarnings("unchecked")
    private List<MemberChange> saved() {
        ArgumentCaptor<List<MemberChange>> rows = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(rows.capture());
        return rows.getValue();
    }

    @Test
    @DisplayName("🔒 identity, passport and account numbers are their last four -- never whole, in a snapshot or a row")
    void numbersAreMasked() {
        Map<String, String> snapshot = trail.snapshot(member());

        assertThat(snapshot.get("identityNumber")).isEqualTo("****9087");
        assertThat(snapshot.get("bankAccountNumber")).isEqualTo("****4567");
        assertThat(snapshot.values()).noneMatch(v -> v != null && (v.contains("8001015009087") || v.contains("99991234567")));
    }

    @Test
    @DisplayName("ME-22: two fields changed by one save are two rows under one save's id, dated by the injected clock")
    void oneSaveOneId() {
        Member member = member();
        Map<String, String> before = trail.snapshot(member);
        member.setCellTel("+27 60 555 0002");
        member.setBankAccountNumber("99997654321");

        trail.changed(7, before, trail.snapshot(member));

        List<MemberChange> rows = saved();
        assertThat(rows).extracting(MemberChange::getField).containsExactlyInAnyOrder("cellTel", "bankAccountNumber");
        assertThat(rows).extracting(MemberChange::getSaveId).containsOnly(rows.get(0).getSaveId());
        assertThat(rows).extracting(MemberChange::getChangedAt).containsOnly(NOW);
        assertThat(rows).extracting(MemberChange::getChangedBy).containsOnly(MemberTrail.ACTOR);
        assertThat(rows).filteredOn(r -> r.getField().equals("bankAccountNumber"))
                .extracting(MemberChange::getOldValue, MemberChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("****4567", "****4321"));
    }

    @Test
    @DisplayName("a save that changed nothing writes nothing")
    void nothingChangedNothingWritten() {
        Member member = member();
        Map<String, String> before = trail.snapshot(member);

        trail.changed(7, before, trail.snapshot(member));

        verify(changes, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("V55: a seat taken is in the history -- made a shop steward on a day, the committee by its code")
    void aSeatTakenIsARow() {
        Member member = member();
        Map<String, String> before = trail.snapshot(member);
        member.setIsShopSteward(true);
        member.setShopStewardElectionDate(java.time.LocalDate.of(2026, 9, 1));
        member.setIsBec(true);
        za.co.titandynamix.sasbo_online_backend.entity.reference.Committee committee =
                new za.co.titandynamix.sasbo_online_backend.entity.reference.Committee();
        committee.setCode("Q01");
        member.setBecCommittee(committee);

        trail.changed(7, before, trail.snapshot(member));

        assertThat(saved()).extracting(MemberChange::getField, MemberChange::getOldValue, MemberChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("isShopSteward", null, "true"),
                        org.assertj.core.groups.Tuple.tuple("shopStewardElectionDate", null, "2026-09-01"),
                        org.assertj.core.groups.Tuple.tuple("isBec", null, "true"),
                        org.assertj.core.groups.Tuple.tuple("becCommitteeCode", null, "Q01"));
    }

    @Test
    @DisplayName("V55: a No that clears its follow-ups writes each field it cleared -- the entity is compared, not the request")
    void aNoThatClearsItsFollowUps() {
        Member member = member();
        za.co.titandynamix.sasbo_online_backend.entity.reference.Committee committee =
                new za.co.titandynamix.sasbo_online_backend.entity.reference.Committee();
        committee.setCode("Q01");
        member.setIsBec(true);
        member.setBecCommittee(committee);
        member.setIsBecRecruiter(true);
        Map<String, String> before = trail.snapshot(member);
        member.setIsBec(false);
        member.setBecCommittee(null);
        member.setIsBecRecruiter(null);

        trail.changed(7, before, trail.snapshot(member));

        assertThat(saved()).extracting(MemberChange::getField, MemberChange::getOldValue, MemberChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("isBec", "true", "false"),
                        org.assertj.core.groups.Tuple.tuple("becCommitteeCode", "Q01", null),
                        org.assertj.core.groups.Tuple.tuple("isBecRecruiter", "true", null));
    }

    @Test
    @DisplayName("rule T: a change of workplace is a row like any other")
    void aChangeOfWorkplaceIsARow() {
        Member member = member();
        Map<String, String> before = trail.snapshot(member);
        Workplace workplace = new Workplace();
        workplace.setRaNumber(900001);
        workplace.setName("INVENTED MAIN STREET");
        member.setWorkplace(workplace);

        trail.changed(7, before, trail.snapshot(member));

        assertThat(saved()).extracting(MemberChange::getField, MemberChange::getOldValue, MemberChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("workplace", null, "900001 INVENTED MAIN STREET"));
    }

    @Test
    @DisplayName("a capture is ONE row, captured -- not a row a field")
    void aCaptureIsOneRow() {
        trail.captured(member());

        ArgumentCaptor<MemberChange> row = ArgumentCaptor.forClass(MemberChange.class);
        verify(changes).save(row.capture());
        assertThat(row.getValue().getField()).isEqualTo(MemberTrail.CAPTURED);
        assertThat(row.getValue().getNewValue()).isEqualTo("M-900001");
        assertThat(row.getValue().getMemberId()).isEqualTo(7);
    }
}
