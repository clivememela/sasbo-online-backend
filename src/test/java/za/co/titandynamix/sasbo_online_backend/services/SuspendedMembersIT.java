package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SuspendedMembersRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.SuspensionLapse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The suspended members by name, and the lapsed, on PostgreSQL (Stephne, MEN-18: "We should also know who the suspended
 * members are name, mem number etc. The system should also remind us when the suspension period for a member have
 * lapsed, so we can delete. (suspensions is only for 3months)"): who is listed, in what order, what each row says, and
 * that the caller's scope and the filters are applied IN SQL. On the fixture's clock, Tuesday 15 September 2026.
 *
 * <pre>
 * member  where                    suspended    code                 lapses        on the clock
 * m7      W1 (area A, NORTH)       no change    --                   --            undated
 * m19     no workplace             1 Mar        11                   1 Jun         lapsed
 * m13     W3 (area B, SOUTH)       1 Apr        13                   1 Jul         lapsed
 * m1      W1                       6 May        11, Unpaid leave     6 Aug         lapsed
 * m2      W1                       15 Jun       11, a typed reason   15 Sep        lapsed, on the day
 * m3      W1                       16 Jun       11, a typed reason   16 Sep        not yet
 * m4      W1                       1 Jul        12, Maternity leave  1 Oct         not yet (suspended in January too)
 * m10     W2 (INVENTED INSURER)    1 Aug        A.C.B.'s, read as 13 1 Nov         not yet
 * m5      W1                       Feb, reinstated in March -- Active, listed nowhere
 * </pre>
 * Every row invented.
 */
@DisplayName("The suspended members by name, and the lapsed, on PostgreSQL")
class SuspendedMembersIT extends AbstractSwitchedOnIT {

    /** Everyone, oldest suspension first, the undated first of all. */
    private static final List<String> EVERYONE = List.of("M-800007", "M-800019", "M-800013", "M-800001", "M-800002",
            "M-800003", "M-800004", "M-800010");

    @Autowired
    private MemberRepository members;

    @Autowired
    private SuspendedMembers suspended;

    @Autowired
    private SuspendedMembersRepository repository;

    @BeforeEach
    void theFixturesSuspended() {
        SummaryFixture.insert(jdbc, members);
        jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'NOT ON PAYROLL'), (12, 'MATERNITY LEAVE'), "
                + "(13, 'DEBIT RETURNED')");
        suspend(19, "2026-03-01", 11, null);
        suspend(13, "2026-04-01", 13, null);
        suspend(1, "2026-05-06", 11, "Unpaid leave");
        suspend(2, "2026-06-15", 11, null);
        suspend(3, "2026-06-16", 11, null);
        change(4, "Active", "Suspended", "SUSPENSION_CODE", 12, "2026-01-10");
        change(4, "Suspended", "Active", "REINSTATED", null, "2026-02-01");
        suspend(4, "2026-07-01", 12, "Maternity leave");
        change(5, "Active", "Suspended", "SUSPENSION_CODE", 11, "2026-02-01");
        change(5, "Suspended", "Active", "REINSTATED", null, "2026-03-01");
        // A.C.B.'s suspension of an unpaid (03.5) carries no SASBO code on the change: read as 13 (MO23).
        jdbc.update("UPDATE members SET status = 'Suspended' WHERE membership_number = 'M-800010'");
        change(10, "Active", "Suspended", "UNPAID_SUSPENDED", null, "2026-08-01");
    }

    private void suspend(int m, String day, int code, String reason) {
        jdbc.update("UPDATE members SET status = 'Suspended' WHERE membership_number = ?", number(m));
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, suspension_code_id, "
                        + "no_deduction_reason_id, note, changed_by, changed_at) VALUES ((SELECT id FROM members WHERE "
                        + "membership_number = ?), 'Active', 'Suspended', 'SUSPENSION_CODE', (SELECT id FROM "
                        + "suspension_codes WHERE code = ?), (SELECT id FROM no_deduction_reasons WHERE name = ?), ?, "
                        + "'system', ?)", number(m), code, reason, reason == null ? "Invented: a reason typed" : null,
                at(day));
    }

    private void change(int m, String from, String to, String reason, Integer code, String day) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, suspension_code_id, "
                        + "changed_by, changed_at) VALUES ((SELECT id FROM members WHERE membership_number = ?), ?, ?, ?, "
                        + "(SELECT id FROM suspension_codes WHERE code = ?), 'system', ?)", number(m), from, to, reason,
                code, at(day));
    }

    private static String number(int m) {
        return String.format("M-8%05d", m);
    }

    /** Ten in the morning in Johannesburg, on {@code day}. */
    private static OffsetDateTime at(String day) {
        return OffsetDateTime.of(LocalDate.parse(day).atTime(8, 0), ZoneOffset.UTC);
    }

    private static List<String> numbers(SuspendedMembersPageDto page) {
        return page.items().stream().map(SuspendedMemberDto::membershipNumber).toList();
    }

    private static SuspendedMemberDto row(SuspendedMembersPageDto page, String number) {
        return page.items().stream().filter(item -> item.membershipNumber().equals(number)).findFirst().orElseThrow();
    }

    private static SignedInUser caller(CallerScope scope) {
        return SignInTestUsers.user("invented.reader", scope, Permission.MEMBERS_VIEW, Permission.MONTH_END_VIEW);
    }

    @Test
    @DisplayName("🔒 MEN-18: everyone suspended now, the oldest suspension first and the undated first of all -- a member reinstated since is listed nowhere")
    void everyoneSuspendedNow() {
        SuspendedMembersPageDto page = suspended.members(null, null, null, null);

        assertThat(numbers(page)).containsExactlyElementsOf(EVERYONE);
        assertThat(page.total()).isEqualTo(8);
        assertThat(page.lapsed()).isEqualTo(4);
        assertThat(page.undated()).isEqualTo(1);
        assertThat(page.asAt()).as("today in SASBO's zone, from the server's clock").isEqualTo("2026-09-15");
        assertThat(page.lapseMonths()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 MEN-18, \"suspensions is only for 3months\": three calendar months on, on the day -- 15 June has lapsed on 15 September, 16 June has not")
    void theLapseOnTheDay() {
        SuspendedMembersPageDto page = suspended.members(null, null, null, null);

        assertThat(row(page, "M-800002").lapsesOn()).isEqualTo("2026-09-15");
        assertThat(row(page, "M-800002").lapsed()).isTrue();
        assertThat(row(page, "M-800003").lapsesOn()).isEqualTo("2026-09-16");
        assertThat(row(page, "M-800003").lapsed()).isFalse();
        assertThat(row(page, "M-800004").suspendedOn()).as("the LATEST change into Suspended").isEqualTo("2026-07-01");
        assertThat(row(page, "M-800004").lapsed()).isFalse();
        assertThat(row(page, "M-800007")).as("suspended with no change on record: no date, never lapsed")
                .satisfies(undated -> {
                    assertThat(undated.suspendedOn()).isNull();
                    assertThat(undated.lapsesOn()).isNull();
                    assertThat(undated.lapsed()).isFalse();
                });
    }

    @Test
    @DisplayName("🔒 MEN-18, \"remind us when the suspension period for a member have lapsed, so we can delete\": the lapsed are their own list -- never the undated")
    void theLapsedAreTheirOwnList() {
        SuspendedMembersPageDto lapsed = suspended.lapsed(null, null, null, null);

        assertThat(numbers(lapsed)).containsExactly("M-800019", "M-800013", "M-800001", "M-800002");
        assertThat(lapsed.total()).isEqualTo(4);
        assertThat(lapsed.lapsed()).isEqualTo(4);
        assertThat(lapsed.undated()).isZero();
        assertThat(lapsed.items()).allSatisfy(item -> assertThat(item.lapsed()).isTrue());
    }

    @Test
    @DisplayName("MEN-18, \"name, mem number etc.\": a row names the member by initials and surname, their bank through the workplace, the code, the reason -- and no identity, account or telephone number")
    void whatARowSays() {
        SuspendedMembersPageDto page = suspended.members(null, null, null, null);

        SuspendedMemberDto unpaidLeave = row(page, "M-800001");
        assertThat(unpaidLeave.name()).isEqualTo("Invented1");
        assertThat(unpaidLeave.bank()).as("the employer through the workplace").isEqualTo("INVENTED BANK");
        assertThat(unpaidLeave.workplace()).isEqualTo("R/A 900001 INVENTED MAIN STREET");
        assertThat(unpaidLeave.area()).isEqualTo("A");
        assertThat(unpaidLeave.paymentType()).isEqualTo("Payroll");
        assertThat(unpaidLeave.suspensionCode()).isEqualTo(11);
        assertThat(unpaidLeave.suspensionName()).isEqualTo("NOT ON PAYROLL");
        assertThat(unpaidLeave.noDeductionReason()).isEqualTo("Unpaid leave");
        assertThat(unpaidLeave.suspendedOn()).isEqualTo("2026-05-06");
        assertThat(unpaidLeave.lapsesOn()).isEqualTo("2026-08-06");
        assertThat(row(page, "M-800004").noDeductionReason()).isEqualTo("Maternity leave");
        assertThat(row(page, "M-800002").noDeductionReason()).as("typed, not chosen: no reason from the list").isNull();
        assertThat(row(page, "M-800010")).as("A.C.B.'s suspension, read in SASBO's code 13 (MO23)")
                .satisfies(acb -> {
                    assertThat(acb.suspensionCode()).isEqualTo(13);
                    assertThat(acb.suspensionName()).isEqualTo("DEBIT RETURNED");
                    assertThat(acb.bank()).isEqualTo("INVENTED INSURER");
                });
        assertThat(row(page, "M-800019")).as("no workplace: no bank through it, no area").satisfies(nowhere -> {
            assertThat(nowhere.workplace()).isNull();
            assertThat(nowhere.area()).isNull();
        });
        assertThat(SuspendedMemberDto.class.getRecordComponents()).extracting(c -> c.getName().toLowerCase())
                .noneMatch(name -> name.contains("identity") || name.contains("account") || name.contains("phone")
                        || name.contains("tel"));
    }

    @Test
    @DisplayName("🔒 the caller's scope is applied in SQL -- a region reads its region, an organiser's area its area, and a member with no workplace is listed for everything only")
    void theCallersScope() throws Exception {
        SuspendedMembersPageDto south = SignInTestUsers.actingAs(caller(CallerScope.regions(Set.of("SOUTH"))),
                () -> suspended.members(null, null, null, null));
        SuspendedMembersPageDto areaA = SignInTestUsers.actingAs(caller(CallerScope.areas(Set.of("A"))),
                () -> suspended.members(null, null, null, null));
        SuspendedMembersPageDto lapsedInA = SignInTestUsers.actingAs(caller(CallerScope.areas(Set.of("A"))),
                () -> suspended.lapsed(null, null, null, null));

        assertThat(numbers(south)).containsExactly("M-800013");
        assertThat(south.total()).as("the counts are the scope's too").isEqualTo(1);
        assertThat(numbers(areaA)).containsExactly("M-800007", "M-800001", "M-800002", "M-800003", "M-800004",
                "M-800010");
        assertThat(areaA.undated()).isEqualTo(1);
        assertThat(numbers(lapsedInA)).containsExactly("M-800001", "M-800002");
    }

    @Test
    @DisplayName("the filters, in SQL: a bank through the workplace, a code -- A.C.B.'s read as 13 -- and both lists take both")
    void theFilters() {
        Long insurer = jdbc.queryForObject("SELECT id FROM employers WHERE name = 'INVENTED INSURER'", Long.class);

        assertThat(numbers(suspended.members(null, null, insurer, null))).containsExactly("M-800010");
        assertThat(numbers(suspended.members(null, null, null, 13))).containsExactly("M-800013", "M-800010");
        assertThat(numbers(suspended.members(null, null, null, 11))).containsExactly("M-800019", "M-800001",
                "M-800002", "M-800003");
        assertThat(numbers(suspended.lapsed(null, null, null, 11))).containsExactly("M-800019", "M-800001",
                "M-800002");
        assertThat(suspended.lapsed(null, null, insurer, null).total()).isZero();
    }

    @Test
    @DisplayName("a page at a time: the order holds across pages, and the counts are the whole of what the filters match")
    void aPageAtATime() {
        SuspendedMembersPageDto second = suspended.members(1, 3, null, null);

        assertThat(numbers(second)).containsExactlyElementsOf(EVERYONE.subList(3, 6));
        assertThat(second.total()).isEqualTo(8);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(numbers(suspended.members(3, 3, null, null))).as("past the last page: nothing").isEmpty();
    }

    @Test
    @DisplayName("🔒 the repository's counts and pages are the same ask: the lapsed boundary is SuspensionLapse's, to the instant")
    void theRepositoryAsksOnce() {
        Instant lapsedBefore = SuspensionLapse.lapsedIfBegunBefore(LocalDate.of(2026, 9, 15));
        SuspendedMembersRepository.Ask everyone = new SuspendedMembersRepository.Ask(CallerScope.all(), null, null,
                false, lapsedBefore);
        SuspendedMembersRepository.Ask lapsedOnly = new SuspendedMembersRepository.Ask(CallerScope.all(), null, null,
                true, lapsedBefore);

        assertThat(repository.counts(everyone)).isEqualTo(new SuspendedMembersRepository.Counts(8, 4, 1));
        assertThat(repository.counts(lapsedOnly)).isEqualTo(new SuspendedMembersRepository.Counts(4, 4, 0));
        assertThat(repository.page(everyone, 0, 100)).extracting(SuspendedMembersRepository.Row::membershipNumber)
                .containsExactlyElementsOf(EVERYONE);
        assertThat(repository.page(lapsedOnly, 0, 2)).extracting(SuspendedMembersRepository.Row::membershipNumber)
                .containsExactly("M-800019", "M-800013");
    }

    @Test
    @DisplayName("🔒 a page past the ceiling, or before the first, is SUSPENDED_PAGE_INVALID with the ceiling; a code not on the list SUSPENSION_CODE_UNKNOWN with the codes that are")
    void theRefusals() {
        for (int[] pageAndSize : new int[][] {{0, 101}, {-1, 50}, {0, 0}}) {
            assertThatThrownBy(() -> suspended.members(pageAndSize[0], pageAndSize[1], null, null))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUSPENDED_PAGE_INVALID);
                        assertThat(refusal.details()).containsEntry("ceiling", "100");
                    });
        }
        assertThatThrownBy(() -> suspended.lapsed(null, null, null, 99))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN);
                    assertThat(refusal.details()).containsEntry("suspensionCode", "99")
                            .containsEntry("known", "11,12,13");
                });
    }
}
