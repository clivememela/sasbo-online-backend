package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Filter;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The member register on PostgreSQL, as production runs it (register row A10): through the real service -- the count
 * and the page read in one read-only {@code REPEATABLE READ} transaction -- with the migration chain's own indexes and
 * constraints in force (the partial unique index on identity numbers among them), the caller's scope through real
 * workplaces, areas and regions, and the figures. The plan at scale is {@code MemberRegisterPlanIT}'s; every fragment on
 * H2 is {@code MemberRegisterRepositoryTest}'s.
 *
 * <p>Writes rows, so it resets the shared database before and after, under its lock (AbstractWritingIT). Every member
 * is invented; identity numbers come from the fixture's generator.
 */
@DisplayName("The member register on PostgreSQL (A10)")
class MemberRegisterIT extends AbstractWritingIT {

    @Autowired
    private MemberRegisterService service;

    @Autowired
    private MemberRegisterRepository register;

    private static final LocalDateTime SAVED = LocalDateTime.of(2026, 9, 1, 8, 0);

    @BeforeEach
    void anInventedRegister() {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('IRN', 'INVENTED NORTH'), ('IRS', 'INVENTED SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'X1', id FROM sasbo_regions WHERE code = 'IRN'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'X2', id FROM sasbo_regions WHERE code = 'IRS'");
        workplace(920001, "X1");
        workplace(920002, "X2");
        member("M-970001", "abelinvented", "Ann", "Active", "INVENTED BANK ONE", "INVENTED BRANCH ONE", 1, 920001);
        member("M-970002", "Bakerinvented", "Ben", "Active", "INVENTED BANK ONE", "INVENTED BRANCH TWO", 2, 920001);
        member("M-970003", "BAKERINVENTED", "Abe", "Suspended", "INVENTED BANK TWO", "INVENTED BRANCH TWO", 3, 920002);
        member("M-970004", "carterinvented", "Cas", "Pending", "INVENTED BANK TWO", "INVENTED BRANCH ONE", 4, 920002);
        member("M-970005", "Daleinvented", "Dee", "Resigned", "INVENTED BANK ONE", null, 5, null);
        member("M-970006", "Ellisinvented", "Eve", "Active", "INVENTED BANK ONE", "INVENTED BRANCH ONE", 6, 920001);
        member("M-970007", "fox_invented", "Fay", "Active", null, null, 7, 920002);
    }

    private void workplace(int ra, String area) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, "
                + "staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', (SELECT id FROM sasbo_areas WHERE "
                + "letter = ?), 'Q01', 10, '00000000')", ra, "INVENTED WORKPLACE " + ra, area);
    }

    private void member(String number, String surname, String firstNames, String status, String bank, String branch,
                        int identity, Integer ra) {
        jdbc.update("INSERT INTO members (membership_number, surname, first_names, status, bank_name, branch, "
                        + "identity_type, identity_number, workplace_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, "
                        + "'I.D. Number', ?, (SELECT id FROM workplaces WHERE ra_number = ?), ?, ?)",
                number, surname, firstNames, status, bank, branch, MemberFixture.syntheticIdFor(identity), ra, SAVED,
                SAVED);
    }

    private static MemberRegisterQuery asked(String q, String status, Integer page, Integer size, String sort) {
        return new MemberRegisterQuery(q, status, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, page, size, sort);
    }

    private static List<String> numbers(MemberRegisterPageDto page) {
        return page.content().stream().map(MemberRegisterPageDto.Row::membershipNumber).toList();
    }

    @Test
    @DisplayName("a page through the service: the count and the rows of one snapshot, and a page past the last is the last")
    void aPageThroughTheService() {
        MemberRegisterPageDto third = service.page(asked(null, null, 2, 3, null), CallerScope.all());
        assertThat(third.totalElements()).isEqualTo(7);
        assertThat(third.totalPages()).isEqualTo(3);
        assertThat(third.page()).isEqualTo(2);
        assertThat(numbers(third)).containsExactly("M-970007");

        MemberRegisterPageDto pastTheLast = service.page(asked(null, null, 9, 3, null), CallerScope.all());
        assertThat(pastTheLast.page()).as("answered as the last page").isEqualTo(2);
        assertThat(numbers(pastTheLast)).isEqualTo(numbers(third));
    }

    @Test
    @DisplayName("by surname in any case, then first names, then the id -- and backwards")
    void bySurname() {
        assertThat(numbers(service.page(asked(null, null, 0, 50, "surname,asc"), CallerScope.all())))
                .containsExactly("M-970001", "M-970003", "M-970002", "M-970004", "M-970005", "M-970006", "M-970007");
        assertThat(numbers(service.page(asked(null, null, 0, 50, "surname,desc"), CallerScope.all())))
                .containsExactly("M-970007", "M-970006", "M-970005", "M-970004", "M-970002", "M-970003", "M-970001");
    }

    @Test
    @DisplayName("the search box: the start of a name in any case, literal wildcards, an identity number whole or by its last four")
    void theSearchBox() {
        assertThat(numbers(service.page(asked("BAKER", null, 0, 50, null), CallerScope.all())))
                .containsExactly("M-970003", "M-970002");
        assertThat(numbers(service.page(asked("fox_", null, 0, 50, null), CallerScope.all())))
                .containsExactly("M-970007");
        assertThat(numbers(service.page(asked("fo%", null, 0, 50, null), CallerScope.all()))).isEmpty();
        String carter = MemberFixture.syntheticIdFor(4);
        assertThat(numbers(service.page(asked(carter, null, 0, 50, null), CallerScope.all())))
                .containsExactly("M-970004");
        assertThat(numbers(service.page(asked(carter.substring(9), null, 0, 50, null), CallerScope.all())))
                .contains("M-970004");
        assertThat(numbers(service.page(asked("970006", null, 0, 50, null), CallerScope.all())))
                .as("a membership number without its M-").containsExactly("M-970006");
    }

    @Test
    @DisplayName("a status filter, in the status's own order")
    void aStatusFilter() {
        MemberRegisterPageDto active = service.page(asked(null, "Active", 0, 50, "status,asc"), CallerScope.all());
        assertThat(numbers(active)).containsExactly("M-970001", "M-970002", "M-970006", "M-970007");
        assertThat(active.totalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("🔒 the caller's scope, through real workplaces: an area, a region -- and nobody without a workplace")
    void theScope() {
        assertThat(numbers(service.page(asked(null, null, 0, 50, "membershipNumber"), CallerScope.areas(Set.of("x1")))))
                .containsExactly("M-970001", "M-970002", "M-970006");
        assertThat(numbers(service.page(asked(null, null, 0, 50, "membershipNumber"), CallerScope.regions(Set.of("IRS")))))
                .containsExactly("M-970003", "M-970004", "M-970007");
        assertThat(numbers(service.page(asked(null, null, 0, 50, "membershipNumber"),
                CallerScope.regions(Set.of("IRN", "IRS"))))).doesNotContain("M-970005");
        assertThat(service.page(asked(null, null, 0, 50, null), CallerScope.areas(Set.of("X2"))).totalElements())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("the figures through the service: by status for the caller's register, and its branches")
    void theFigures() {
        MemberRegisterOverviewDto all = service.overview(CallerScope.all());
        assertThat(all.total()).isEqualTo(7);
        assertThat(all.active()).isEqualTo(4);
        assertThat(all.pending()).isEqualTo(1);
        assertThat(all.suspended()).isEqualTo(1);
        assertThat(all.branches()).containsExactly("INVENTED BRANCH ONE", "INVENTED BRANCH TWO");
        assertThat(LocalDate.parse(all.asOf())).isNotNull();

        MemberRegisterOverviewDto north = service.overview(CallerScope.regions(Set.of("IRN")));
        assertThat(north.total()).isEqualTo(3);
        assertThat(north.suspended()).isZero();
        assertThat(north.branches()).containsExactly("INVENTED BRANCH ONE", "INVENTED BRANCH TWO");
    }

    @Test
    @DisplayName("the month's joiners and the day's saves, in their windows, on PostgreSQL's dates and timestamps")
    void theWindows() {
        jdbc.update("UPDATE members SET join_date = DATE '2026-09-14', payment_type = 'Debit Order' "
                + "WHERE membership_number IN ('M-970001', 'M-970003')");
        jdbc.update("UPDATE members SET join_date = DATE '2026-10-01' WHERE membership_number = 'M-970002'");
        jdbc.update("UPDATE members SET updated_at = TIMESTAMP '2026-10-06 23:59:59' WHERE membership_number = 'M-970004'");

        assertThat(register.joined(CallerScope.all(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
                "Debit Order")).containsExactly(2, 2);
        assertThat(register.joined(CallerScope.areas(Set.of("X1")), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1), "Debit Order")).containsExactly(1, 1);
        assertThat(register.updated(CallerScope.all(), LocalDateTime.of(2026, 10, 6, 0, 0),
                LocalDateTime.of(2026, 10, 7, 0, 0))).isEqualTo(1);
        assertThat(register.count(Filter.NONE, CallerScope.all())).isEqualTo(7);
    }
}
