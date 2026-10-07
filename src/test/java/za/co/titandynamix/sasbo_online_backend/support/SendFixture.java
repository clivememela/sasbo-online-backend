package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ONE invented population for the files that leave SASBO (October build) -- put on record the same way on H2 and on
 * PostgreSQL. Every row is invented: the regions, the employers, the workplaces, the people.
 *
 * <pre>
 * Region  Area  Workplace (R/A, table code)            Members in August 2026, the month taken
 * NORTH   A     990001 INVENTED MAIN STREET (901)      a1 joined 5 Aug (payroll) · a2 on record (payroll) ·
 *                                                      a3 suspended 15 Aug, code 11 · a4 deleted 20 Aug, code 2
 * NORTH   B     990002 INVENTED HARBOUR (902)          b1 joined 10 Aug (debit order) · b2 deleted 25 Aug, code 1
 *                                                      (payroll) · b3 on record (payroll)
 * SOUTH   C     990003 INVENTED SOUTH POINT (901)      c1 joined 12 Aug (payroll) · c2 suspended 18 Aug by a
 *                                                      returned debit (read as code 13) · c3 on record (payroll)
 * --      --    none                                   n1 joined 20 Aug (payroll): in no region's pack
 * </pre>
 * Recipients: NORTH1 (area A), NORTH2 (area B), SOUTH (the whole region). Banks: INVENTED_BANK (901, the full list, in
 * Absa's layout), INVENTED_INSURER (902, new and deleted, a workbook). Providers: OM_FUNERAL (Old Mutual's layout),
 * LAW_CALL (provisional, by SFTP), CRITICARE (switched off).
 */
public final class SendFixture {

    /** The clock the tests read: 15 September 2026, so August is the last month and September open. */
    public static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");
    public static final YearMonth TAKEN = YearMonth.of(2026, 8);

    /** Each member's surname after "Invented" -- letters only, as Absa's record prints a surname. */
    public static final Map<String, String> SURNAMES = Map.ofEntries(Map.entry("a1", "alpha"), Map.entry("a2", "bravo"),
            Map.entry("a3", "charlie"), Map.entry("a4", "delta"), Map.entry("b1", "echo"), Map.entry("b2", "foxtrot"),
            Map.entry("b3", "golf"), Map.entry("c1", "hotel"), Map.entry("c2", "india"), Map.entry("c3", "juliet"),
            Map.entry("n1", "kilo"));

    private SendFixture() {
    }

    /** The membership number of a member of the fixture: M-7 and the employee number. */
    public static String number(String key) {
        return "M-7" + EMPLOYEE.get(key);
    }

    /** The surname a member of the fixture is printed with, in capitals. */
    public static String surname(String key) {
        return ("Invented" + SURNAMES.get(key)).toUpperCase(java.util.Locale.ROOT);
    }

    /** Each member's employee number. */
    public static final Map<String, String> EMPLOYEE = Map.ofEntries(Map.entry("a1", "10000001"),
            Map.entry("a2", "10000002"), Map.entry("a3", "10000003"), Map.entry("a4", "10000004"),
            Map.entry("b1", "20000001"), Map.entry("b2", "20000002"), Map.entry("b3", "20000003"),
            Map.entry("c1", "30000001"), Map.entry("c2", "30000002"), Map.entry("c3", "30000003"),
            Map.entry("n1", "40000001"));

    /** The lists, the workplaces, the members and their August -- then August taken by the real take. */
    public static Map<String, Integer> insert(JdbcTemplate jdbc, MemberRepository members) {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'INVENTED NORTH'), ('SOUTH', 'INVENTED SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'B', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'C', id FROM sasbo_regions WHERE code = 'SOUTH'");
        jdbc.update("INSERT INTO employer_groups (name, core, ordinal) VALUES ('INVENTED GROUP ONE', TRUE, 1)");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 1, 'INVENTED BANK', id FROM employer_groups");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 2, 'INVENTED INSURER', id FROM employer_groups");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 901, 'INVENTED BANK', id, NULL "
                + "FROM employers WHERE ordinal = 1");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 902, 'INVENTED INSURER', id, NULL "
                + "FROM employers WHERE ordinal = 2");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) VALUES (903, 'INVENTED LENDER', NULL, NULL)");
        jdbc.update("INSERT INTO committee_provinces (letter, name) VALUES ('Q', NULL)");
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'Q01', id, "
                + "'INVENTED COMMITTEE', NULL FROM committee_provinces WHERE letter = 'Q'");
        jdbc.update("INSERT INTO deletion_codes (sasbo_code, name) VALUES (1, 'Left service'), (2, 'Resigned Society'), "
                + "(5, 'Subscription in Arrears')");
        jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'NOT ON PAYROLL'), (13, 'DEBIT RETURNED')");
        workplace(jdbc, 990001, "INVENTED MAIN STREET", 901, "A", 50);
        workplace(jdbc, 990002, "INVENTED HARBOUR", 902, "B", 40);
        workplace(jdbc, 990003, "INVENTED SOUTH POINT", 901, "C", 30);

        LocalDate longAgo = LocalDate.of(2019, 1, 1);
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("a1", member(members, "a1", "Active", LocalDate.of(2026, 8, 5), "Payroll Deduction", "10000001"));
        ids.put("a2", member(members, "a2", "Active", longAgo, "Payroll Deduction", "10000002"));
        ids.put("a3", member(members, "a3", "Suspended", longAgo, "Payroll Deduction", "10000003"));
        ids.put("a4", member(members, "a4", "Resigned", longAgo, "Payroll Deduction", "10000004"));
        ids.put("b1", member(members, "b1", "Active", LocalDate.of(2026, 8, 10), "Debit Order", "20000001"));
        ids.put("b2", member(members, "b2", "Retired", longAgo, "Payroll Deduction", "20000002"));
        ids.put("b3", member(members, "b3", "Active", longAgo, "Payroll Deduction", "20000003"));
        ids.put("c1", member(members, "c1", "Active", LocalDate.of(2026, 8, 12), "Payroll Deduction", "30000001"));
        ids.put("c2", member(members, "c2", "Suspended", longAgo, "Payroll Deduction", "30000002"));
        ids.put("c3", member(members, "c3", "Active", longAgo, "Payroll Deduction", "30000003"));
        ids.put("n1", member(members, "n1", "Active", LocalDate.of(2026, 8, 20), "Payroll Deduction", "40000001"));

        standAt(jdbc, 990001, ids, "a1", "a2", "a3", "a4");
        standAt(jdbc, 990002, ids, "b1", "b2", "b3");
        standAt(jdbc, 990003, ids, "c1", "c2", "c3");

        change(jdbc, ids.get("a3"), "Active", "Suspended", "SUSPENSION_CODE", null, 11, "2026-08-15T08:00:00Z");
        change(jdbc, ids.get("a4"), "Active", "Resigned", "DELETION_CODE", 2, null, "2026-08-20T08:00:00Z");
        change(jdbc, ids.get("b2"), "Active", "Retired", "DELETION_CODE", 1, null, "2026-08-25T08:00:00Z");
        change(jdbc, ids.get("c2"), "Active", "Suspended", "UNPAID_SUSPENDED", null, null, "2026-08-18T08:00:00Z");
        return ids;
    }

    /** The recipients, the banks and the providers. */
    public static void settings(JdbcTemplate jdbc) {
        String at = "TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00'";
        jdbc.update("INSERT INTO month_end_pack_sets (code, label, region_code, area_letters, position, email, active, "
                + "version, updated_by, updated_at) VALUES "
                + "('NORTH1', 'Invented North (first set)', 'NORTH', 'A', 'Regional Secretary', "
                + "'regional.secretary.north1@example.org', TRUE, 0, 'seed', " + at + "), "
                + "('NORTH2', 'Invented North (second set)', 'NORTH', 'B', 'Regional Secretary', "
                + "'regional.secretary.north2@example.org', TRUE, 0, 'seed', " + at + "), "
                + "('SOUTH', 'Invented South', 'SOUTH', NULL, 'Regional Secretary', NULL, TRUE, 0, 'seed', " + at + ")");
        jdbc.update("INSERT INTO month_end_banks (code, name, full_list, layout, active, version, updated_by, updated_at) "
                + "VALUES ('INVENTED_BANK', 'Invented Bank', TRUE, 'ABSA_FIXED_WIDTH', TRUE, 0, 'seed', " + at + "), "
                + "('INVENTED_INSURER', 'Invented Insurer', FALSE, 'SHEET', TRUE, 0, 'seed', " + at + ")");
        jdbc.update("INSERT INTO month_end_bank_table_codes (bank_id, table_code) SELECT id, 901 FROM month_end_banks "
                + "WHERE code = 'INVENTED_BANK'");
        jdbc.update("INSERT INTO month_end_bank_table_codes (bank_id, table_code) SELECT id, 902 FROM month_end_banks "
                + "WHERE code = 'INVENTED_INSURER'");
        jdbc.update("INSERT INTO month_end_providers (code, name, layout, delivery, active, version, updated_by, "
                + "updated_at) VALUES ('OM_FUNERAL', 'Invented funeral benefit', 'OLD_MUTUAL_FUNERAL', 'DOWNLOAD', TRUE, "
                + "0, 'seed', " + at + "), ('LAW_CALL', 'Invented legal cover', 'PROVISIONAL_MEMBER_LIST', 'SFTP', "
                + "TRUE, 0, 'seed', " + at + "), ('CRITICARE', 'Invented suspended benefit', 'PROVISIONAL_MEMBER_LIST', "
                + "'DOWNLOAD', FALSE, 0, 'seed', " + at + ")");
    }

    /** Empties what these tests write, the parents last -- the fast lane's databases are not reset between tests. */
    public static void clear(JdbcTemplate jdbc) {
        for (String table : List.of("month_end_deliveries", "month_end_region_packs", "month_end_files",
                "month_end_bank_table_codes", "month_end_banks", "month_end_pack_sets", "month_end_providers",
                "month_end_packs", "month_end_counts", "month_end_workplaces", "month_end_months",
                "member_status_changes", "member_changes", "members", "workplaces", "committees",
                "committee_provinces", "table_codes", "employers", "employer_groups", "sasbo_areas", "sasbo_regions",
                "deletion_codes", "suspension_codes")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    /** {@code body} run as a signed-in caller of {@code scope}, holding MONTH_END_SEND -- then nobody again. */
    public static <T> T as(CallerScope scope, Supplier<T> body) {
        ScopedPrincipal principal = new ScopedPrincipal() {
            @Override
            public String username() {
                return "invented.user";
            }

            @Override
            public String displayName() {
                return "Invented User";
            }

            @Override
            public CallerScope scope() {
                return scope;
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("MONTH_END_SEND"))));
        try {
            return body.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void workplace(JdbcTemplate jdbc, int ra, String name, int tableCode, String area, int complement) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                        + "committee_code, committee_id, staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', "
                        + "(SELECT id FROM table_codes WHERE code = ?), 'NAME', (SELECT id FROM sasbo_areas WHERE letter = ?), "
                        + "'Q01', (SELECT id FROM committees WHERE code = 'Q01'), ?, '00000000')",
                ra, name, tableCode, area, complement);
    }

    private static Integer member(MemberRepository members, String key, String status, LocalDate joined, String pays,
                                  String employee) {
        Member member = MemberFixture.withSurname("Invented" + SURNAMES.get(key));
        member.setFirstNames("Madeup");
        member.setInitials("M");
        member.setTitle("Mr");
        member.setMembershipNumber("M-7" + employee);
        member.setEmployeeNumber(employee);
        member.setStatus(status);
        member.setJoinDate(joined);
        member.setPaymentType(pays);
        member.setMonthlySubscription(new java.math.BigDecimal("174.00"));
        return members.save(member).getId();
    }

    private static void standAt(JdbcTemplate jdbc, int ra, Map<String, Integer> ids, String... keys) {
        for (String key : keys) {
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) WHERE id = ?",
                    ra, ids.get(key));
        }
    }

    private static void change(JdbcTemplate jdbc, Integer member, String from, String to, String reason,
                               Integer deletionCode, Integer suspensionCode, String at) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                        + "changed_at, deletion_code_id, suspension_code_id) VALUES (?, ?, ?, ?, 'system', ?, "
                        + "(SELECT id FROM deletion_codes WHERE sasbo_code = ?), (SELECT id FROM suspension_codes "
                        + "WHERE code = ?))",
                member, from, to, reason, OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC), deletionCode,
                suspensionCode);
    }
}
