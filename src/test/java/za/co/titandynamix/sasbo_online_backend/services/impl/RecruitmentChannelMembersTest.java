package za.co.titandynamix.sasbo_online_backend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.MemberCandidatesDto;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelMembersService;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.RecruitmentChannelSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RC15, the server half: the Recruiters screen's figures and lists, worked out in SQL from each member's own
 * recruitment channel -- on H2, from the fixture the client's test reads too ({@code recruitment-channel-fixture.json},
 * the same file in both repositories), so the server's numbers and the screen's are held to one {@code expected}.
 *
 * <p>Invented people only. The class has its database to itself, so its rows are the only ones counted; methods run
 * one at a time in random order, and the two that change a member put them back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
        "spring.datasource.url=jdbc:h2:mem:recruitment-channels;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Import(RecruitmentChannelMembersTest.CountedAtTheConnection.class)
@DisplayName("RC15 -- the Recruiters screen's figures and lists, worked out by the server")
class RecruitmentChannelMembersTest {

    @TestConfiguration
    static class CountedAtTheConnection {
        @Bean
        static BeanPostProcessor countStatementsAtTheConnection() {
            return ConnectionStatements.register();
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecruitmentChannelMembersService service;

    @Autowired
    private RecruitmentChannelSeeder seeder;

    @Autowired
    private SeedMemberNumbers seedNumbers;

    private static JsonNode fixture;

    @BeforeAll
    static void readTheFixture() throws Exception {
        try (InputStream in = RecruitmentChannelMembersTest.class
                .getResourceAsStream("/fixtures/recruitment-channel-fixture.json")) {
            assertThat(in).as("the shared fixture is on the classpath").isNotNull();
            fixture = new ObjectMapper().readTree(in);
        }
    }

    @BeforeEach
    void thePopulation() {
        Integer present = jdbc.queryForObject("SELECT count(*) FROM members WHERE membership_number = 'T-0001'",
                Integer.class);
        if (present == null || present == 0) {
            insert();
        }
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("🔴 RC15: every channel's members counted by the member's own channel -- the shared fixture's figures, "
            + "in ONE statement")
    void theFiguresAreTheFixtures() {
        AtomicReference<List<ChannelSummaryDto>> answer = new AtomicReference<>();
        long statements = ConnectionStatements.during(() -> answer.set(service.summary()));

        assertThat(figures(answer.get())).isEqualTo(expected("all"));
        assertThat(statements).as("one grouped statement, however many members there are").isEqualTo(1);
    }

    @Test
    @DisplayName("a caller limited to an area is counted that area's members; a member with no workplace is counted "
            + "only for a caller who sees everything")
    void theFiguresKeepToTheCallersArea() {
        signedInWith(CallerScope.areas(Set.of("A")));
        assertThat(figures(service.summary())).isEqualTo(expected("areaA"));

        signedInWith(CallerScope.regions(Set.of("south")));
        assertThat(figures(service.summary())).isEqualTo(expected("regionSouth"));
    }

    @Test
    @DisplayName("a channel's members, a page at a time in the order of their surnames, narrowed by each criterion")
    void aChannelsMembers() {
        long bec = channelId("BEC");

        ChannelMembersPageDto all = service.members(bec, null, null, null, null, null, 0, 20);
        assertThat(all.content()).extracting(ChannelMemberDto::surname)
                .containsExactly("Example", "Sample", "Testperson");
        assertThat(all.totalElements()).isEqualTo(3);

        ChannelMembersPageDto first = service.members(bec, null, null, null, null, null, 0, 2);
        assertThat(first.content()).hasSize(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(service.members(bec, null, null, null, null, null, 1, 2).content())
                .extracting(ChannelMemberDto::surname).containsExactly("Testperson");

        // Each as the screen's own filters read it: "contains", ignoring case -- the name as initials and surname.
        assertThat(service.members(bec, null, "b sam", null, null, null, 0, 20).content())
                .extracting(ChannelMemberDto::membershipNumber).containsExactly("T-0002");
        assertThat(service.members(bec, null, null, null, null, "SUSP", 0, 20).content())
                .extracting(ChannelMemberDto::membershipNumber).containsExactly("T-0003");
        assertThat(service.members(bec, "0001", null, "synthetic", "main", null, 0, 20).content())
                .extracting(ChannelMemberDto::membershipNumber).containsExactly("T-0001");
        // A typed % is only itself.
        assertThat(service.members(bec, null, "%", null, null, null, 0, 20).content()).isEmpty();

        signedInWith(CallerScope.areas(Set.of("A")));
        assertThat(service.members(bec, null, null, null, null, null, 0, 20).content())
                .extracting(ChannelMemberDto::membershipNumber).containsExactly("T-0001");
    }

    @Test
    @DisplayName("a list the routes do not take is refused in words: a page past the limit, a long search, no channel")
    void whatTheListRefuses() {
        long bec = channelId("BEC");

        assertRefused(() -> service.members(bec, null, null, null, null, null, 0, 101),
                ReferenceRefusalCode.REQUEST_INVALID, "A page holds 1 to 100 members.");
        assertRefused(() -> service.members(bec, null, null, null, null, null, -1, 20),
                ReferenceRefusalCode.REQUEST_INVALID, "A page number is 0 or more.");
        assertRefused(() -> service.members(bec, "x".repeat(101), null, null, null, null, 0, 20),
                ReferenceRefusalCode.REQUEST_INVALID, "A search is at most 100 characters.");
        assertRefused(() -> service.members(999_999L, null, null, null, null, null, 0, 20),
                ReferenceRefusalCode.CHANNEL_NOT_FOUND,
                "That recruitment channel is no longer on record. Reload the list, then try again.");
    }

    @Test
    @DisplayName("a picker offers at most fifty members and says there were more, leaving out the channel's own")
    void thePickers() {
        long bec = channelId("BEC");

        assertThat(service.candidates("sample", bec).rows()).extracting(ChannelMemberDto::membershipNumber)
                .as("T-0002 is the BEC's already").containsExactly("T-0008");
        assertThat(service.candidates("sample", null).rows()).extracting(ChannelMemberDto::membershipNumber)
                .containsExactly("T-0002", "T-0008");
        assertThat(service.candidates("invented bank", null).rows()).extracting(ChannelMemberDto::membershipNumber)
                .containsExactlyInAnyOrder("T-0003", "T-0004");
        assertThat(service.candidates("charlie", null).rows()).extracting(ChannelMemberDto::membershipNumber)
                .containsExactly("T-0003");

        MemberCandidatesDto many = service.candidates("bulkperson", null);
        assertThat(many.rows()).hasSize(RecruitmentChannelMembersService.PICKER_ROWS);
        assertThat(many.more()).isTrue();
        assertThat(service.candidates("sample", null).more()).isFalse();

        signedInWith(CallerScope.areas(Set.of("A")));
        assertThat(service.candidates("sample", null).rows()).isEmpty();
    }

    @Test
    @DisplayName("🔴 RC15: Add Member records the member's channel, and Remove takes it off -- each a history row; the "
            + "same again changes nothing")
    void addAndRemoveAMember() {
        long mso = channelId("MSO");
        int member = memberId("T-0099");
        try {
            assertThat(service.assign(mso, member).membershipNumber()).isEqualTo("T-0099");
            assertThat(channelOf(member)).isEqualTo("MSO");
            assertThat(figures(service.summary())).filteredOn(f -> f.get("name").equals("MSO"))
                    .extracting(f -> f.get("members")).containsExactly(1L);
            service.assign(mso, member);

            service.unassign(mso, member);
            assertThat(channelOf(member)).isNull();
            service.unassign(mso, member);

            assertThat(jdbc.query("SELECT old_value, new_value FROM member_changes WHERE member_id = ?"
                            + " AND field = 'recruitedHow' ORDER BY id",
                    (rs, n) -> Arrays.asList(rs.getString(1), rs.getString(2)), member))
                    .as("one row each way -- the repeats wrote nothing")
                    .containsExactly(Arrays.asList(null, "MSO"), Arrays.asList("MSO", null));
        } finally {
            jdbc.update("UPDATE members SET recruitment_channel_id = NULL WHERE id = ?", member);
            jdbc.update("DELETE FROM member_changes WHERE member_id = ?", member);
        }
    }

    @Test
    @DisplayName("Remove on a stale view -- the member is on another channel now -- is refused, and changes nothing")
    void removeOnAStaleView() {
        int member = memberId("T-0004");

        assertRefused(() -> service.unassign(channelId("BEC"), member), ReferenceRefusalCode.MEMBER_NOT_IN_CHANNEL,
                "That member is recorded as recruited through Website, not BEC. Reload the list to see where they "
                        + "are now.");
        assertThat(channelOf(member)).isEqualTo("Website");
    }

    @Test
    @DisplayName("a caller limited to an area cannot reach a member outside it -- told as of a member not on record")
    void aMemberOutsideTheCallersArea() {
        signedInWith(CallerScope.areas(Set.of("B")));

        assertRefused(() -> service.assign(channelId("MSO"), memberId("T-0099")), ReferenceRefusalCode.MEMBER_NOT_FOUND,
                "That member is not on record. Reload the list, then try again.");
        assertRefused(() -> service.assign(channelId("MSO"), memberId("T-0003")), ReferenceRefusalCode.MEMBER_NOT_FOUND,
                "That member is not on record. Reload the list, then try again.");
        assertThat(channelOf(memberId("T-0099"))).isNull();
    }

    @Test
    @DisplayName("the seed gives a seeded member with no channel the one their number names, and never replaces one "
            + "already chosen")
    void theSeedFillsOnlyWhatIsAbsent() {
        List<String> numbers = seedNumbers.all().stream().limit(3).toList();
        try {
            insertMember(numbers.get(0), "Mx", "S", "Seed", "Seedone", "Synthetic Bank", "Hill", "Active", null, "A");
            insertMember(numbers.get(1), "Mx", "S", "Seed", "Seedtwo", "Synthetic Bank", "Hill", "Active", null, "A");
            insertMember(numbers.get(2), "Mx", "S", "Seed", "Seedthree", "Synthetic Bank", "Hill", "Active", "Youth",
                    "A");

            int given = seeder.seed();

            assertThat(given).as("the two with none, and only them").isEqualTo(2);
            assertThat(channelOf(memberId(numbers.get(0)))).isEqualTo(RecruitmentChannelSeeder.channelFor(numbers.get(0)));
            assertThat(channelOf(memberId(numbers.get(1)))).isEqualTo(RecruitmentChannelSeeder.channelFor(numbers.get(1)));
            assertThat(channelOf(memberId(numbers.get(2)))).as("chosen already, so kept").isEqualTo("Youth");
            assertThat(seeder.seed()).as("a second start gives nobody anything").isZero();
        } finally {
            jdbc.update("DELETE FROM members WHERE membership_number IN (?, ?, ?)", numbers.toArray());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** The figures without their ids -- the part the fixture states. */
    private static List<Map<String, Object>> figures(List<ChannelSummaryDto> summary) {
        return summary.stream().map(c -> Map.<String, Object>of("name", c.name(), "members", c.members(),
                "banks", c.banks(), "branches", c.branches())).toList();
    }

    private static List<Map<String, Object>> expected(String which) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode c : fixture.path("expected").path(which)) {
            rows.add(Map.of("name", c.path("name").asText(), "members", c.path("members").asLong(),
                    "banks", strings(c.path("banks")), "branches", strings(c.path("branches"))));
        }
        return rows;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asText()));
        return values;
    }

    private static void assertRefused(Runnable call, ReferenceRefusalCode code, String message) {
        assertThatThrownBy(call::run).isInstanceOf(ReferenceRefusal.class).hasMessage(message)
                .satisfies(thrown -> assertThat(((ReferenceRefusal) thrown).code()).isEqualTo(code));
    }

    private static void signedInWith(CallerScope scope) {
        ScopedPrincipal principal = new ScopedPrincipal() {
            @Override
            public String username() {
                return "test.user";
            }

            @Override
            public String displayName() {
                return "Test User";
            }

            @Override
            public CallerScope scope() {
                return scope;
            }
        };
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private long channelId(String name) {
        return jdbc.queryForObject("SELECT id FROM recruitment_channel WHERE name = ?", Long.class, name);
    }

    private int memberId(String number) {
        return jdbc.queryForObject("SELECT id FROM members WHERE membership_number = ?", Integer.class, number);
    }

    private String channelOf(int member) {
        return jdbc.queryForObject("SELECT c.name FROM members m LEFT JOIN recruitment_channel c"
                + " ON c.id = m.recruitment_channel_id WHERE m.id = ?", String.class, member);
    }

    private void insert() {
        fixture.path("channels").forEach(c ->
                jdbc.update("INSERT INTO recruitment_channel (name, is_enabled) VALUES (?, TRUE)", c.asText()));
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'INVENTED NORTH'), "
                + "('SOUTH', 'INVENTED SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'B', id FROM sasbo_regions WHERE code = 'SOUTH'");
        for (String letter : List.of("A", "B")) {
            jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code,"
                            + " staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER',"
                            + " (SELECT id FROM sasbo_areas WHERE letter = ?), 'Q01', 10, 'CL0')",
                    letter.equals("A") ? 910_001 : 910_002, "INVENTED WORKPLACE " + letter, letter);
        }
        for (JsonNode m : fixture.path("members")) {
            insertMember(m.path("membershipNumber").asText(), m.path("title").asText(), m.path("initials").asText(),
                    m.path("firstNames").asText(), m.path("surname").asText(), text(m.path("bankName")),
                    text(m.path("branch")), m.path("status").asText(), text(m.path("channel")), text(m.path("area")));
        }
        for (int i = 1; i <= 55; i++) {
            insertMember(String.format("B-%04d", i), "Mx", "Z", "Zulu", "Bulkperson", "Synthetic Bank", "Hill",
                    "Active", null, "A");
        }
        insertMember("T-0099", "Mx", "Y", "Yankee", "Mutable", "Synthetic Bank", "Hill", "Active", null, "A");
    }

    private static String text(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    private void insertMember(String number, String title, String initials, String firstNames, String surname,
                              String bank, String branch, String status, String channel, String area) {
        jdbc.update("INSERT INTO members (membership_number, title, initials, first_names, surname, bank_name, branch,"
                        + " status, recruitment_channel_id, workplace_id, created_at, updated_at) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, (SELECT id FROM recruitment_channel WHERE name = ?),"
                        + " (SELECT w.id FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id WHERE a.letter = ?),"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                number, title, initials, firstNames, surname, bank, branch, status, channel, area);
    }
}
