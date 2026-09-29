package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SASBO's lists and workplaces through a whole start, with the switch at {@code all} (month end 01.1, 01.2). A
 * context of its own, on a database of its own: the statement count it pins is taken at the connection, and nothing
 * else may be writing while it is taken.
 *
 * <p>No count typed here is a count of SASBO's: every expected size is read from the resource itself, which states
 * how many rows each list holds (MV-37).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import({SeedStartupTest.MigrationRowsAndAFrozenClock.class, MonthEndSeedTest.CountedAtTheConnection.class})
@DisplayName("Month end's seed, through a whole start with the switch at all")
class MonthEndSeedTest {

    /**
     * THE PINNED SECOND START (MR-PERF-19, MV-63): what a start issues when every list and every workplace is already
     * on record. SASBO's lists: one read of what {@code reference_lists} holds, then one read of the keys on record a
     * list -- ten, the deletion codes being two, SASBO's numbered codes and the reasons for resigning under code 2.
     * The workplaces: one read of their record, one each of the areas, table codes and committees they refer to,
     * and one of the workplaces on record. Nothing is written. A question asked a row would multiply this by
     * thousands; a write would add one.
     */
    static final long SECOND_START_STATEMENTS = 11 + 5;

    @TestConfiguration
    static class CountedAtTheConnection {
        @Bean
        static BeanPostProcessor countStatementsAtTheConnection() {
            return ConnectionStatements.register();
        }
    }

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @Autowired
    private DemonstrationMemberSeeder invented;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.WorkplaceService workplaceRoute;

    @AfterEach
    void releaseTheHooks() {
        lists.beforeWrite(null);
        workplaces.beforeWrite(null);
    }

    private static JsonNode resource(String path) throws IOException {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new ObjectMapper().readTree(stream);
        }
    }

    /** Each list's size, as the resource states it. */
    private static Map<String, Long> statedSizes() throws IOException {
        Map<String, Long> sizes = new LinkedHashMap<>();
        for (JsonNode list : resource(ReferenceListSeeder.RESOURCE).path("lists")) {
            sizes.put(list.path("list").asText(), list.path("count").asLong());
        }
        return sizes;
    }

    private long rows(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("every list is on a fresh demonstration database, as many rows as the resource states, each marked as printed and unconfirmed (MD28)")
    void everyListIsThere() throws IOException {
        Map<String, Long> stated = statedSizes();
        assertThat(stated.keySet()).containsExactlyElementsOf(ReferenceListSeeder.ORDER);
        assertThat(rows("SELECT count(*) FROM sasbo_regions")).isEqualTo(stated.get("regions"));
        assertThat(rows("SELECT count(*) FROM sasbo_areas")).isEqualTo(stated.get("areas"));
        assertThat(rows("SELECT count(*) FROM employer_groups")).isEqualTo(stated.get("employerGroups"));
        assertThat(rows("SELECT count(*) FROM employers")).isEqualTo(stated.get("employers"));
        assertThat(rows("SELECT count(*) FROM table_codes")).isEqualTo(stated.get("tableCodes"));
        assertThat(rows("SELECT count(*) FROM committee_provinces")).isEqualTo(stated.get("committeeProvinces"));
        assertThat(rows("SELECT count(*) FROM committees")).isEqualTo(stated.get("committees"));
        assertThat(rows("SELECT count(*) FROM suspension_codes")).isEqualTo(stated.get("suspensionCodes"));
        assertThat(rows("SELECT count(*) FROM deletion_codes WHERE sasbo_code IS NOT NULL OR parent_id IS NOT NULL"))
                .isEqualTo(stated.get("deletionCodes"));
        assertThat(jdbc.queryForList("SELECT list_key FROM reference_lists WHERE outcome = 'SEEDED' "
                + "AND printed_on = DATE '2026-08-31' AND confirmed_on IS NULL", String.class))
                .containsAll(ReferenceListSeeder.ORDER)
                .contains(WorkplaceSeeder.LIST);
    }

    @Test
    @DisplayName("RC7: the deletion codes carry SASBO's numbers, and the reasons for resigning stand under code 2")
    void theDeletionCodesCarrySasbosNumbers() {
        assertThat(jdbc.queryForList("SELECT sasbo_code FROM deletion_codes WHERE sasbo_code IS NOT NULL "
                + "ORDER BY sasbo_code", Integer.class)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(rows("SELECT count(*) FROM deletion_codes c JOIN deletion_codes p ON p.id = c.parent_id "
                + "WHERE p.sasbo_code = 2 AND c.sasbo_code IS NULL")).isEqualTo(5);
    }

    @Test
    @DisplayName("MD42: the workplaces, keyed by R/A number, carry the table code the files prove -- by name or by sums -- or none")
    void theWorkplacesAreThere() throws IOException {
        JsonNode resource = resource(WorkplaceSeeder.RESOURCE);
        long byName = 0;
        long bySums = 0;
        long none = 0;
        for (JsonNode w : resource.path("workplaces")) {
            String by = w.path("tableCodeBy").asText("");
            if (by.equals("NAME")) {
                byName++;
            } else if (by.equals("SUMS")) {
                bySums++;
            } else {
                none++;
            }
        }
        assertThat(rows("SELECT count(*) FROM workplaces")).isEqualTo(resource.path("count").asLong());
        assertThat(rows("SELECT count(DISTINCT ra_number) FROM workplaces")).isEqualTo(resource.path("count").asLong());
        assertThat(rows("SELECT count(*) FROM workplaces WHERE table_code_by = 'NAME'")).isEqualTo(byName);
        assertThat(rows("SELECT count(*) FROM workplaces WHERE table_code_by = 'SUMS'")).isEqualTo(bySums);
        assertThat(rows("SELECT count(*) FROM workplaces WHERE table_code_id IS NULL")).isEqualTo(none);
        assertThat(rows("SELECT count(*) FROM workplaces WHERE committee_id IS NULL"))
                .as("the workplaces whose printed code has no committee keep the code, and no committee")
                .isEqualTo(rows("SELECT count(*) FROM workplaces w WHERE NOT EXISTS "
                        + "(SELECT 1 FROM committees c WHERE c.code = w.committee_code)"));
    }

    /** Every member's membership number, and the R/A number of the workplace they stand at. */
    private Map<String, Integer> standing() {
        Map<String, Integer> at = new HashMap<>();
        jdbc.query("SELECT m.membership_number, w.ra_number FROM members m JOIN workplaces w ON w.id = m.workplace_id",
                rs -> {
                    at.put(rs.getString(1), rs.getInt(2));
                });
        return at;
    }

    @Test
    @DisplayName("MD53, MV-45: the switch at all puts the invented members on record -- the 160, the 150 and the file's own, and nobody else")
    void theSwitchPutsTheInventedMembersOnRecord() throws IOException {
        long original;
        try (InputStream stream = new ClassPathResource("data/members.json").getInputStream()) {
            original = DataSeeder.readSeedMembers(stream).size();
        }
        assertThat(members.count()).isEqualTo(original + DebitOrderMemberSeeder.readSeedFile().members().size()
                + DemonstrationMemberSeeder.readSeedFile().count());
    }

    @Test
    @DisplayName("MD35, MR-ROB-32: every member of the seed stands where the file says -- the invented, and the 310 by their placements")
    void everyMemberStandsWhereTheFileSays() throws IOException {
        DemonstrationMember.SeedFile file = DemonstrationMemberSeeder.readSeedFile();
        Map<String, Integer> expected = new HashMap<>();
        file.members().forEach(m -> expected.put(m.membershipNumber(), m.workplaceRaNumber()));
        file.placements().forEach(p -> expected.put(p.membershipNumber(), p.workplaceRaNumber()));

        assertThat(standing()).isEqualTo(expected);
        assertThat(rows("SELECT count(*) FROM members WHERE workplace_id IS NULL"))
                .as("on a fresh demonstration database with the switch at all, no member of the seed is without one")
                .isZero();
    }

    @Test
    @DisplayName("🔒 MR-PERF-19: a second start of the invented members and the placements is two statements, and writes nothing")
    void theInventedMembersSecondStartIsPinned() {
        Map<String, Integer> before = standing();
        long statements = ConnectionStatements.during(() -> {
            try {
                assertThat(invented.seed()).isZero();
                assertThat(invented.place()).isZero();
            } catch (IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
        });
        assertThat(statements).as("one read of the file's numbers on record, one of the seed's members without a workplace")
                .isEqualTo(2);
        assertThat(standing()).isEqualTo(before);
    }

    @Test
    @DisplayName("MV-46: the step fills only what is empty -- a workplace a person chose stays, one never given is filled")
    void thePlacementsFillOnlyWhatIsEmpty() throws IOException {
        List<DemonstrationMember.Placement> placements = DemonstrationMemberSeeder.readSeedFile().placements();
        DemonstrationMember.Placement chosen = placements.get(0);
        DemonstrationMember.Placement empty = placements.get(1);
        Integer another = jdbc.queryForObject("SELECT min(ra_number) FROM workplaces WHERE ra_number <> ?", Integer.class,
                chosen.workplaceRaNumber());
        jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) "
                + "WHERE membership_number = ?", another, chosen.membershipNumber());
        jdbc.update("UPDATE members SET workplace_id = NULL WHERE membership_number = ?", empty.membershipNumber());
        try {
            assertThat(invented.place()).isEqualTo(1);

            assertThat(standing()).containsEntry(chosen.membershipNumber(), another)
                    .containsEntry(empty.membershipNumber(), empty.workplaceRaNumber());
        } finally {
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) "
                    + "WHERE membership_number = ?", chosen.workplaceRaNumber(), chosen.membershipNumber());
        }
    }

    @Test
    @DisplayName("🔒 BR36: the A.C.B. history, prepared with the invented members on record, lists none of them -- no line, no listed exclusion")
    void acbListsNoInventedMember() throws IOException {
        List<String> numbers = DemonstrationMemberSeeder.readSeedFile().members().stream()
                .map(DemonstrationMember::membershipNumber).toList();
        List<String> lined = jdbc.queryForList("SELECT DISTINCT m.membership_number FROM acb_run_lines l "
                + "JOIN members m ON m.id = l.member_id", String.class);
        List<String> listed = jdbc.queryForList("SELECT DISTINCT m.membership_number FROM acb_run_exclusions x "
                + "JOIN members m ON m.id = x.member_id", String.class);

        assertThat(lined).as("the history was prepared, or this proves nothing").isNotEmpty();
        assertThat(lined).as("an invented member on a debit line").doesNotContainAnyElementsOf(numbers);
        assertThat(listed).as("an invented member on the 'left out, and why' sheet").doesNotContainAnyElementsOf(numbers);
    }

    @Test
    @DisplayName("🔒 MR-PERF-19: a second start issues the pinned number of statements, counted at the connection, and writes nothing")
    void aSecondStartIsPinned() {
        Timestamp before = jdbc.queryForObject("SELECT max(recorded_at) FROM reference_lists", Timestamp.class);

        long statements = ConnectionStatements.during(() -> {
            lists.seed();
            workplaces.seed();
        });

        assertThat(statements)
                .as("statements a second start issued -- %d was pinned; see SECOND_START_STATEMENTS",
                        SECOND_START_STATEMENTS)
                .isEqualTo(SECOND_START_STATEMENTS);
        assertThat(jdbc.queryForObject("SELECT max(recorded_at) FROM reference_lists", Timestamp.class))
                .as("a second start records nothing, because it wrote nothing")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("MR-PERF-12: a page of the workplaces is one statement -- its rows, its lineage and its total together")
    void aPageIsOneStatement() {
        assertThat(ConnectionStatements.during(() -> workplaceRoute.page(3, 50, null, null, null, null, null)))
                .isEqualTo(1);
        assertThat(ConnectionStatements.during(() -> workplaceRoute.page(0, 50, null, null, "A", null, "ba")))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("canary: the count at the connection sees a statement JdbcTemplate issues, which QueryCount cannot")
    void theCountCanSeeAJdbcStatement() {
        assertThat(ConnectionStatements.during(() -> jdbc.queryForObject("SELECT 1", Integer.class))).isEqualTo(1);
    }

    @Test
    @DisplayName("the per-row lesson of JobGradeSeeder: a row the database lacks reaches it, and the rest are left as they are")
    void aMissingRowReachesTheDatabase() {
        jdbc.update("DELETE FROM suspension_codes WHERE code = 19");

        lists.seed();

        assertThat(jdbc.queryForList("SELECT code FROM suspension_codes ORDER BY code", Integer.class))
                .containsExactly(11, 13, 19);
    }

    @Test
    @DisplayName("MR-ROB-20: until SASBO confirms a list the seeder owns it -- a row changed by hand is brought back to the resource's")
    void anUnconfirmedListIsBroughtUpToDate() {
        jdbc.update("UPDATE suspension_codes SET name = 'CHANGED BY HAND' WHERE code = 11");

        lists.seed();

        assertThat(jdbc.queryForObject("SELECT name FROM suspension_codes WHERE code = 11", String.class))
                .isEqualTo("NOT ON PAYROLL");
    }

    @Test
    @DisplayName("🔒 MR-ROB-20: from the day SASBO confirms a list the database owns it, and a changed resource does not touch it")
    void aConfirmedListIsLeftAlone() {
        jdbc.update("UPDATE reference_lists SET confirmed_on = DATE '2026-10-01' WHERE list_key = 'suspensionCodes'");
        jdbc.update("UPDATE suspension_codes SET name = 'CORRECTED BY SASBO' WHERE code = 13");
        try {
            lists.seed();

            assertThat(jdbc.queryForObject("SELECT name FROM suspension_codes WHERE code = 13", String.class))
                    .isEqualTo("CORRECTED BY SASBO");
        } finally {
            jdbc.update("UPDATE reference_lists SET confirmed_on = NULL WHERE list_key = 'suspensionCodes'");
            lists.seed();
        }
    }

    @Test
    @DisplayName("🔒 ME-18: a list is seeded whole or not at all -- a row the database refuses leaves none of the list, and the status says REFUSED")
    void aListIsWholeOrNotThere() throws IOException {
        ObjectNode broken = (ObjectNode) resource(ReferenceListSeeder.RESOURCE);
        for (JsonNode list : broken.path("lists")) {
            if (list.path("list").asText().equals("suspensionCodes")) {
                ArrayNode rows = (ArrayNode) list.path("rows");
                rows.add(rows.get(0).deepCopy());
            }
        }
        jdbc.update("DELETE FROM suspension_codes");
        try {
            lists.seed(broken);

            assertThat(rows("SELECT count(*) FROM suspension_codes"))
                    .as("the list's first row was written before the second was refused, and was rolled back with it")
                    .isZero();
            assertThat(jdbc.queryForMap("SELECT outcome, failure FROM reference_lists WHERE list_key = 'suspensionCodes'"))
                    .containsEntry("OUTCOME", "FAILED")
                    .containsEntry("FAILURE", "REFUSED");
        } finally {
            lists.seed();
        }
        assertThat(rows("SELECT count(*) FROM suspension_codes")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT outcome FROM reference_lists WHERE list_key = 'suspensionCodes'",
                String.class)).isEqualTo("SEEDED");
    }

    @Test
    @DisplayName("🔒 MR-SEC-21: a row that is not a row's shape fails its list as UNREADABLE -- a word, never the row")
    void anUnreadableRowFailsItsList() throws IOException {
        ObjectNode broken = (ObjectNode) resource(ReferenceListSeeder.RESOURCE);
        for (JsonNode list : broken.path("lists")) {
            if (list.path("list").asText().equals("suspensionCodes")) {
                ((ObjectNode) list.path("rows").get(0)).remove("name");
            }
        }
        try {
            lists.seed(broken);

            assertThat(jdbc.queryForMap("SELECT outcome, failure FROM reference_lists WHERE list_key = 'suspensionCodes'"))
                    .containsEntry("OUTCOME", "FAILED")
                    .containsEntry("FAILURE", "UNREADABLE");
            assertThat(rows("SELECT count(*) FROM suspension_codes")).as("nothing of it was removed").isEqualTo(3);
        } finally {
            lists.seed();
        }
    }

    @Test
    @DisplayName("🔒 MR-ROB-23: two starts at once leave each row once, and the one that lost says so rather than failing")
    void twoStartsAtOnce() throws Exception {
        jdbc.update("DELETE FROM suspension_codes");
        CyclicBarrier together = new CyclicBarrier(2);
        lists.beforeWrite(() -> {
            try {
                together.await(10, TimeUnit.SECONDS);
            } catch (Exception releasedAlone) {
                together.reset();
            }
        });
        Thread first = new Thread(lists::seed, "month-end-first-start");
        Thread second = new Thread(lists::seed, "month-end-second-start");
        first.start();
        second.start();
        first.join(60_000);
        second.join(60_000);
        lists.beforeWrite(null);

        assertThat(jdbc.queryForList("SELECT code FROM suspension_codes ORDER BY code", Integer.class))
                .containsExactly(11, 13, 19);
        assertThat(jdbc.queryForObject("SELECT outcome FROM reference_lists WHERE list_key = 'suspensionCodes'",
                String.class)).isEqualTo("SEEDED");
    }

    @Test
    @DisplayName("MR-ROB-24: a workplace the list no longer prints is closed from the day that list was printed -- never deleted")
    void aWorkplaceIsClosedNeverDeleted() {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, "
                + "staff_complement, cl_code) SELECT 999999, 'INVENTED CLOSED BRANCH', 'INVENTED BANK', id, 'Q99', 1, "
                + "'00000000' FROM sasbo_areas WHERE letter = 'A'");
        try {
            workplaces.seed();

            assertThat(jdbc.queryForObject("SELECT closed_on FROM workplaces WHERE ra_number = 999999",
                    java.sql.Date.class)).hasToString("2026-08-31");
        } finally {
            jdbc.update("DELETE FROM workplaces WHERE ra_number = 999999");
        }
    }
}
