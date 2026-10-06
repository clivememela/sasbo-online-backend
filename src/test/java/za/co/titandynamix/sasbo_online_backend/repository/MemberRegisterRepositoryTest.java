package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Filter;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Order;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every statement of the member register on H2, in the fast lane (register row A10): each criterion, each order either
 * way, the pages, the caller's scope through the member's workplace, the figures -- and the statements a page costs,
 * counted at the connection, the same for five members as for forty. Both databases accept every statement (BR65); what
 * only PostgreSQL can show -- en_ZA's order, and the plan at scale -- is {@code MemberRegisterIT} and
 * {@code MemberRegisterPlanIT}.
 *
 * <p>The schema is the entities' (the fast lane's), in H2's PostgreSQL mode, as the application's own tests run it.
 * Every member is invented; identity numbers come from the fixture's own generator, with a valid check digit.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:member-register;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemberRegisterRepository.class, MemberRegisterRepositoryTest.Wiring.class})
@DisplayName("The member register's statements, on H2 (A10)")
class MemberRegisterRepositoryTest {

    @TestConfiguration
    static class Wiring {

        @Bean
        static BeanPostProcessor countStatementsAtTheConnection() {
            return ConnectionStatements.register();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    private static final LocalDateTime SAVED = LocalDateTime.of(2026, 9, 1, 8, 0);

    private static final String ANA = "M-990001";
    private static final String BO = "M-990002";
    private static final String DE = "M-990003";
    private static final String INVENTOR = "M-990004";
    private static final String UNDERSCORE = "M-990005";
    private static final String NOT_UNDERSCORE = "M-990006";
    private static final String UNPLACED = "M-990007";
    private static final String PASSPORT = "M-990008";
    private static final String ANA_AGAIN = "M-990009";
    private static final String SOUTHERN = "M-990010";
    private static final String NO_REGION = "M-990011";

    @Autowired
    private MemberRegisterRepository register;

    @Autowired
    private JdbcTemplate jdbc;

    private NamedParameterJdbcTemplate named;

    @BeforeEach
    void aSmallInventedRegister() {
        named = new NamedParameterJdbcTemplate(jdbc);
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'INVENTED NORTH'), "
                + "('SOUTH', 'INVENTED SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'B', id FROM sasbo_regions WHERE code = 'SOUTH'");
        // An area no region claims, and one held in lower case: a scope's codes are compared in either case.
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) VALUES ('Y', NULL)");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'c', id FROM sasbo_regions WHERE code = 'SOUTH'");
        workplace(910001, "A");
        workplace(910002, "B");
        workplace(910003, "Y");
        workplace(910004, "c");

        member(ANA, m -> with(m, "surname", "Fictaria", "first_names", "Ana Synthetic", "title", "Ms",
                "initials", "AS", "employee_number", "E-1001", "identity_number", MemberFixture.syntheticIdFor(11),
                "bank_name", "INVENTED BANK ONE", "branch", "INVENTED BRANCH NORTH",
                "bank_account_number", "62000001001", "phone", "+27 60 555 1001",
                "personal_email", "Ana.Fictaria@personal.invented", "work_email", "afictaria@work.invented",
                "race", "African", "join_date", LocalDate.of(2026, 9, 3), "payment_type", "Debit Order",
                "ra", 910001));
        member(BO, m -> with(m, "surname", "fictaria", "first_names", "Bo Invented", "title", "Mr", "initials", "BI",
                "employee_number", "E-2002", "identity_number", MemberFixture.syntheticIdFor(12),
                "bank_name", "INVENTED BANK TWO", "branch", "INVENTED BRANCH SOUTH", "status", "Suspended",
                "race", "Indian", "join_date", LocalDate.of(2026, 9, 20), "payment_type", "Payroll Deduction",
                "ra", 910002));
        member(DE, m -> with(m, "surname", "de Fictus", "first_names", "Cee", "employee_number", "E-3003",
                "identity_number", MemberFixture.syntheticIdFor(13), "bank_name", "INVENTED BANK ONE",
                "branch", "INVENTED BRANCH NORTH", "status", "Pending", "ra", 910001));
        member(INVENTOR, m -> with(m, "surname", "Inventor", "first_names", "Fictaria Dee",
                "identity_number", MemberFixture.syntheticIdFor(14), "bank_name", "INVENTED BANK TWO",
                "branch", "INVENTED BRANCH SOUTH", "status", "Resigned", "ra", 910001));
        member(UNDERSCORE, m -> with(m, "surname", "Zed_Sample", "first_names", "Ee",
                "identity_number", MemberFixture.syntheticIdFor(15), "ra", 910001));
        member(NOT_UNDERSCORE, m -> with(m, "surname", "ZedXSample", "first_names", "Eff",
                "identity_number", MemberFixture.syntheticIdFor(16), "ra", 910001));
        member(UNPLACED, m -> with(m, "surname", "Unplaced", "first_names", "Gee",
                "identity_number", MemberFixture.syntheticIdFor(17), "branch", "", "status", null));
        member(PASSPORT, m -> with(m, "surname", "Passportholder", "first_names", "Aitch", "identity_type", "Passport",
                "identity_number", "QZ9876543", "ra", 910002));
        // The same surname and first names as ANA: the id decides between them, so two pages never share one.
        member(ANA_AGAIN, m -> with(m, "surname", "Fictaria", "first_names", "Ana Synthetic",
                "identity_number", MemberFixture.syntheticIdFor(19), "bank_name", "INVENTED BANK THREE",
                "ra", 910001));
        member(SOUTHERN, m -> with(m, "surname", "Southerner", "first_names", "Jay",
                "identity_number", MemberFixture.syntheticIdFor(20), "ra", 910004));
        member(NO_REGION, m -> with(m, "surname", "Outposter", "first_names", "Kay",
                "identity_number", MemberFixture.syntheticIdFor(21), "ra", 910003));
    }

    private void workplace(int ra, String area) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, "
                + "staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', (SELECT id FROM sasbo_areas WHERE "
                + "letter = ?), 'Q01', 10, '00000000')", ra, "INVENTED WORKPLACE " + ra, area);
    }

    private static Map<String, Object> with(Map<String, Object> member, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            member.put((String) pairs[i], pairs[i + 1]);
        }
        return member;
    }

    /** An invented member: Active, an I.D. Number, saved on the first of September -- unless told otherwise. */
    private void member(String number, UnaryOperator<Map<String, Object>> told) {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("membership_number", number);
        columns.put("status", "Active");
        columns.put("identity_type", "I.D. Number");
        columns.put("created_at", SAVED);
        columns.put("updated_at", SAVED);
        told.apply(columns);
        Object ra = columns.remove("ra");
        if (ra != null) {
            columns.put("workplace_id", jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = ?",
                    Long.class, ra));
        }
        named.update("INSERT INTO members (" + String.join(", ", columns.keySet()) + ") VALUES ("
                + columns.keySet().stream().map(c -> ":" + c).collect(Collectors.joining(", ")) + ")", columns);
    }

    private static Filter q(String q) {
        return new Filter(q, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** The membership numbers of the whole answer, in order, read a page at a time. */
    private List<String> numbers(Filter filter, CallerScope scope, Order order, boolean descending) {
        return register.page(filter, scope, order, descending, 0, 100).stream()
                .map(MemberRegisterPageDto.Row::membershipNumber).toList();
    }

    private List<String> found(Filter filter) {
        List<String> found = numbers(filter, CallerScope.all(), Order.MEMBERSHIP_NUMBER, false);
        assertThat(register.count(filter, CallerScope.all())).as("the count is the rows'").isEqualTo(found.size());
        return found;
    }

    // ── the search box ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the start of a surname or a first name finds its members, in any case")
    void theStartOfAName() {
        assertThat(found(q("fict"))).containsExactly(ANA, BO, INVENTOR, ANA_AGAIN);
        assertThat(found(q("FICT"))).containsExactly(ANA, BO, INVENTOR, ANA_AGAIN);
        assertThat(found(q("de f"))).as("a surname with a space in it").containsExactly(DE);
        assertThat(found(q("ana"))).as("a first name").containsExactly(ANA, ANA_AGAIN);
        assertThat(found(q("taria"))).as("the start, never the middle").isEmpty();
    }

    @Test
    @DisplayName("a membership number by its start, with or without its M-; an employee number by its start")
    void theStartOfANumber() {
        assertThat(found(q("m-99001"))).containsExactly(SOUTHERN, NO_REGION);
        assertThat(found(q("990003"))).as("typed without its M-").containsExactly(DE);
        assertThat(found(q("e-200"))).containsExactly(BO);
    }

    @Test
    @DisplayName("🔒 a % or an _ typed is only itself -- never a wildcard that answers more")
    void wildcardsAreEscaped() {
        assertThat(found(q("zed_"))).containsExactly(UNDERSCORE);
        assertThat(found(q("zed%"))).isEmpty();
        assertThat(found(q("%"))).isEmpty();
        assertThat(found(q("!"))).as("the escape character itself is only itself").isEmpty();
    }

    @Test
    @DisplayName("four digits find the identity numbers that end in them; a whole identity or passport number its member")
    void anIdentityNumberWholeOrByItsLastFour() {
        String ana = MemberFixture.syntheticIdFor(11);
        assertThat(found(q(ana.substring(9)))).contains(ANA);
        assertThat(found(q(ana))).containsExactly(ANA);
        assertThat(found(q("qz9876543"))).as("a passport number, in any case").containsExactly(PASSPORT);
        assertThat(found(q(ana.substring(3)))).as("part of one finds nobody").isEmpty();
    }

    @Test
    @DisplayName("a search that matches nobody answers no rows and a count of nought")
    void nobody() {
        assertThat(found(q("nobody-invented"))).isEmpty();
    }

    // ── the criteria ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("each exact criterion -- status, bank, branch, title, identity type, race")
    void theExactCriteria() {
        assertThat(found(new Filter(null, "Suspended", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null))).containsExactly(BO);
        assertThat(found(new Filter(null, null, "INVENTED BANK ONE", null, null, null, null, null, null, null, null,
                null, null, null, null, null))).containsExactly(ANA, DE);
        assertThat(found(new Filter(null, null, null, "INVENTED BRANCH SOUTH", null, null, null, null, null, null,
                null, null, null, null, null, null))).containsExactly(BO, INVENTOR);
        assertThat(found(new Filter(null, null, null, null, "Ms", null, null, null, null, null, null, null, null,
                null, null, null))).containsExactly(ANA);
        assertThat(found(new Filter(null, null, null, null, null, null, null, null, null, "Passport", null, null,
                null, null, null, null))).containsExactly(PASSPORT);
        assertThat(found(new Filter(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, "Indian"))).containsExactly(BO);
    }

    @Test
    @DisplayName("each criterion matched by its start, in any case -- initials, surname, numbers, e-mail addresses")
    void theStartingCriteria() {
        assertThat(found(new Filter(null, null, null, null, null, "a", null, null, null, null, null, null, null,
                null, null, null))).containsExactly(ANA);
        assertThat(found(new Filter(null, null, null, null, null, null, "FICTA", null, null, null, null, null, null,
                null, null, null))).containsExactly(ANA, BO, ANA_AGAIN);
        assertThat(found(new Filter(null, null, null, null, null, null, null, "e-3", null, null, null, null, null,
                null, null, null))).containsExactly(DE);
        assertThat(found(new Filter(null, null, null, null, null, null, null, null, "990002", null, null, null, null,
                null, null, null))).as("typed without its M-").containsExactly(BO);
        assertThat(found(new Filter(null, null, null, null, null, null, null, null, null, null, null, null, null,
                "ana.fict", null, null))).containsExactly(ANA);
        assertThat(found(new Filter(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, "AFICTARIA@", null))).containsExactly(ANA);
    }

    @Test
    @DisplayName("an identity, account or cellphone number, whole or by its last four -- never by a part")
    void theNumbersWholeOrByTheirLastFour() {
        String ana = MemberFixture.syntheticIdFor(11);
        assertThat(found(identity(ana))).containsExactly(ANA);
        assertThat(found(identity(ana.substring(9)))).contains(ANA);
        assertThat(found(identity(ana.substring(5)))).isEmpty();
        assertThat(found(account("62000001001"))).containsExactly(ANA);
        assertThat(found(account("1001"))).containsExactly(ANA);
        assertThat(found(account("2000001001"))).isEmpty();
        assertThat(found(phone("+27 60 555 1001"))).containsExactly(ANA);
        assertThat(found(phone("1001"))).containsExactly(ANA);
        assertThat(found(phone("555 1001"))).isEmpty();
    }

    private static Filter identity(String value) {
        return new Filter(null, null, null, null, null, null, null, null, null, null, value, null, null, null, null,
                null);
    }

    private static Filter account(String value) {
        return new Filter(null, null, null, null, null, null, null, null, null, null, null, value, null, null, null,
                null);
    }

    private static Filter phone(String value) {
        return new Filter(null, null, null, null, null, null, null, null, null, null, null, null, value, null, null,
                null);
    }

    @Test
    @DisplayName("criteria together narrow together")
    void criteriaTogether() {
        assertThat(found(new Filter("fict", "Active", "INVENTED BANK ONE", null, null, null, null, null, null, null,
                null, null, null, null, null, null))).containsExactly(ANA);
    }

    // ── the orders ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("by surname, then first names, then the id -- in any case -- and backwards")
    void bySurname() {
        List<String> ascending = numbers(Filter.NONE, CallerScope.all(), Order.SURNAME, false);
        assertThat(ascending).containsExactly(DE, ANA, ANA_AGAIN, BO, INVENTOR, NO_REGION, PASSPORT, SOUTHERN,
                UNPLACED, UNDERSCORE, NOT_UNDERSCORE);
        List<String> descending = new ArrayList<>(ascending);
        java.util.Collections.reverse(descending);
        assertThat(numbers(Filter.NONE, CallerScope.all(), Order.SURNAME, true)).containsExactlyElementsOf(descending);
    }

    @Test
    @DisplayName("by bank, then surname: the members of a bank together, in surname order")
    void byBank() {
        assertThat(numbers(new Filter("fict", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null), CallerScope.all(), Order.BANK, false)).containsExactly(ANA, ANA_AGAIN, BO, INVENTOR);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Order.class)
    @DisplayName("every order reads the whole register, every member once, and backwards is the reverse")
    void everyOrderIsATotalOrder(Order order) {
        List<String> ascending = numbers(Filter.NONE, CallerScope.all(), order, false);
        List<String> descending = new ArrayList<>(numbers(Filter.NONE, CallerScope.all(), order, true));
        java.util.Collections.reverse(descending);

        assertThat(ascending).hasSize(11).doesNotHaveDuplicates();
        assertThat(descending).isEqualTo(ascending);
    }

    // ── the pages ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pages of two follow one another: no member twice, none skipped, the last one short")
    void pagesFollowOneAnother() {
        List<String> whole = numbers(Filter.NONE, CallerScope.all(), Order.SURNAME, false);
        List<String> paged = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            List<MemberRegisterPageDto.Row> rows = register.page(Filter.NONE, CallerScope.all(), Order.SURNAME, false,
                    page, 2);
            assertThat(rows).hasSize(page < 5 ? 2 : 1);
            rows.forEach(row -> paged.add(row.membershipNumber()));
        }
        assertThat(paged).isEqualTo(whole);
        assertThat(register.page(Filter.NONE, CallerScope.all(), Order.SURNAME, false, 6, 2)).isEmpty();
    }

    // ── the caller's scope ───────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔒 a caller limited to an area reads its members alone; to a region, the members of its areas")
    void theScope() {
        assertThat(numbers(Filter.NONE, CallerScope.areas(Set.of("A")), Order.MEMBERSHIP_NUMBER, false))
                .containsExactly(ANA, DE, INVENTOR, UNDERSCORE, NOT_UNDERSCORE, ANA_AGAIN);
        assertThat(numbers(Filter.NONE, CallerScope.regions(Set.of("SOUTH")), Order.MEMBERSHIP_NUMBER, false))
                .as("area B and area c are the south's").containsExactly(BO, PASSPORT, SOUTHERN);
        assertThat(numbers(Filter.NONE, CallerScope.areas(Set.of("c")), Order.MEMBERSHIP_NUMBER, false))
                .as("in either case").containsExactly(SOUTHERN);
        assertThat(numbers(Filter.NONE, CallerScope.regions(Set.of("NORTH", "SOUTH")), Order.MEMBERSHIP_NUMBER, false))
                .as("an area no region claims is in no region's scope").doesNotContain(NO_REGION);
        assertThat(register.count(Filter.NONE, CallerScope.regions(Set.of("SOUTH")))).isEqualTo(3);
    }

    @Test
    @DisplayName("🔒 a member with no workplace is read only by a caller who sees everything")
    void anUnplacedMemberIsReadOnlyByAll() {
        assertThat(numbers(Filter.NONE, CallerScope.all(), Order.MEMBERSHIP_NUMBER, false)).contains(UNPLACED);
        for (CallerScope limited : List.of(CallerScope.areas(Set.of("A", "B", "Y", "C")),
                CallerScope.regions(Set.of("NORTH", "SOUTH")))) {
            assertThat(numbers(Filter.NONE, limited, Order.MEMBERSHIP_NUMBER, false)).doesNotContain(UNPLACED);
        }
    }

    @Test
    @DisplayName("the scope narrows a search too")
    void theScopeNarrowsASearch() {
        assertThat(numbers(q("fict"), CallerScope.areas(Set.of("B")), Order.MEMBERSHIP_NUMBER, false))
                .containsExactly(BO);
    }

    // ── the statements ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔒 no N+1: a page is two statements -- the count and the rows -- for eleven members as for forty")
    void aPageIsTwoStatements() {
        long small = ConnectionStatements.during(this::aPage);
        for (int n = 0; n < 29; n++) {
            member("M-98" + String.format("%04d", n), m -> with(m, "surname", "Bulkinvented", "first_names", "Lee",
                    "bank_name", "INVENTED BANK ONE"));
        }
        long large = ConnectionStatements.during(this::aPage);

        assertThat(small).isEqualTo(2);
        assertThat(large).as("the same at forty members").isEqualTo(small);
    }

    private void aPage() {
        register.count(Filter.NONE, CallerScope.all());
        register.page(Filter.NONE, CallerScope.all(), Order.SURNAME, false, 0, 50);
    }

    // ── the figures ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the members counted by status, a status none and all included; and in scope")
    void countsByStatus() {
        Map<String, Long> all = register.countsByStatus(CallerScope.all());
        assertThat(all).containsEntry("Active", 7L).containsEntry("Suspended", 1L).containsEntry("Pending", 1L)
                .containsEntry("Resigned", 1L).containsEntry(null, 1L);
        assertThat(all.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(11);
        assertThat(register.countsByStatus(CallerScope.regions(Set.of("SOUTH"))))
                .containsOnly(Map.entry("Active", 2L), Map.entry("Suspended", 1L));
    }

    @Test
    @DisplayName("the month's joiners, and how many of them pay by debit order")
    void theMonthsJoiners() {
        assertThat(register.joined(CallerScope.all(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
                "Debit Order")).containsExactly(2, 1);
        assertThat(register.joined(CallerScope.all(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
                "Debit Order")).as("a month nobody joined in").containsExactly(0, 0);
        assertThat(register.joined(CallerScope.areas(Set.of("B")), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1), "Debit Order")).containsExactly(1, 0);
    }

    @Test
    @DisplayName("the records saved in a day's window -- the window's start in, its end out")
    void theDaysSaves() {
        jdbc.update("UPDATE members SET updated_at = ? WHERE membership_number = ?",
                LocalDateTime.of(2026, 10, 6, 0, 0), ANA);
        jdbc.update("UPDATE members SET updated_at = ? WHERE membership_number = ?",
                LocalDateTime.of(2026, 10, 7, 0, 0), BO);

        assertThat(register.updated(CallerScope.all(), LocalDateTime.of(2026, 10, 6, 0, 0),
                LocalDateTime.of(2026, 10, 7, 0, 0))).isEqualTo(1);
    }

    @Test
    @DisplayName("every branch the members in scope are at, once, in order, at most as many as asked -- never a blank")
    void theBranches() {
        assertThat(register.branches(CallerScope.all(), 500))
                .containsExactly("INVENTED BRANCH NORTH", "INVENTED BRANCH SOUTH");
        assertThat(register.branches(CallerScope.all(), 1)).containsExactly("INVENTED BRANCH NORTH");
        assertThat(register.branches(CallerScope.regions(Set.of("SOUTH")), 500))
                .containsExactly("INVENTED BRANCH SOUTH");
    }
}
