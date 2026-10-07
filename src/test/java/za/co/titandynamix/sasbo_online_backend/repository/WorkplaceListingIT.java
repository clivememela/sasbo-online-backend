package za.co.titandynamix.sasbo_online_backend.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceListingsDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMemberPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMembersDto;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceListingService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The Location Listing on PostgreSQL (October build, 5 Oct Show &amp; Tell): the members at a branch -- the members on
 * record there and no leaver, masked in the statement, in a listing's order -- for a caller whose area or region
 * covers it, and filtered in SQL as well as refused; and the organisers' batch print in two statements.
 */
@DisplayName("The Location Listing, on PostgreSQL")
class WorkplaceListingIT extends AbstractSwitchedOnIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @Autowired
    private WorkplaceMemberRepository members;

    @Autowired
    private WorkplaceLookupRepository places;

    @Autowired
    private WorkplaceService workplacePages;

    @Autowired
    private WorkplaceSwitch workplaceSwitch;

    @Autowired
    private DataSource dataSource;

    private long branch;
    private String region;

    /** A signed-in caller of the sign-in seed's shape. */
    record Caller(String username, String displayName, CallerScope scope) implements ScopedPrincipal {
    }

    @BeforeEach
    void inventedMembersAtABranchOfAreaL() {
        lists.seed();
        workplaces.seed();
        branch = jdbc.queryForObject("SELECT w.id FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id "
                + "WHERE a.letter = 'L' ORDER BY w.ra_number LIMIT 1", Long.class);
        region = jdbc.queryForObject("SELECT r.code FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id "
                + "JOIN sasbo_regions r ON r.id = a.region_id WHERE w.id = ?", String.class, branch);
        member("M-090003", "INVENTED-C", "Active", "INV000003", branch);
        member("M-090001", "INVENTED-A", "Active", "INV000001", branch);
        member("M-090002", "INVENTED-B", "Suspended", "INV000002", branch);
        // A leaver is not at the branch any more, and a member elsewhere is not at this one.
        member("M-090004", "INVENTED-D", "Resigned", "INV000004", branch);
        Long elsewhere = jdbc.queryForObject("SELECT w.id FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id "
                + "WHERE a.letter = 'W' ORDER BY w.ra_number LIMIT 1", Long.class);
        member("M-090005", "INVENTED-E", "Active", "INV000005", elsewhere);
    }

    private void member(String number, String surname, String status, String employeeNumber, Long workplace) {
        jdbc.update("INSERT INTO members (membership_number, surname, initials, status, join_date, employee_number, "
                + "cell_tel, workplace_id, created_at, updated_at) VALUES (?, ?, 'A', ?, DATE '2024-03-01', ?, "
                + "'+27 60 555 0002', ?, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00')",
                number, surname, status, employeeNumber, workplace);
    }

    private MockHttpServletResponse membersAs(CallerScope scope) throws Exception {
        return mockMvc.perform(get("/api/workplaces/{id}/members", branch).with(authentication(
                new UsernamePasswordAuthenticationToken(new Caller("invented.caller", "Invented Caller", scope), null,
                        AuthorityUtils.createAuthorityList("MEMBERS_VIEW"))))).andReturn().getResponse();
    }

    @Test
    @DisplayName("🔒 5 Oct: the members on record at a branch -- the suspended beside the paying, no leaver, nobody from elsewhere -- in a listing's order, masked in the statement")
    void theMembersAtABranch() throws Exception {
        MockHttpServletResponse response = membersAs(CallerScope.all());

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode page = objectMapper.readTree(response.getContentAsString());
        assertThat(page.get("totalElements").asLong()).isEqualTo(3L);
        assertThat(page.get("members").findValuesAsText("surname"))
                .containsExactly("INVENTED-A", "INVENTED-B", "INVENTED-C");
        assertThat(page.get("members").findValuesAsText("employeeNumber"))
                .containsExactly("****0001", "****0002", "****0003");
        assertThat(page.get("members").get(1).get("suspended").asBoolean()).isTrue();
        assertThat(page.get("members").get(0).get("cell").asBoolean()).isTrue();
        assertThat(page.get("members").get(0).get("joinedOn").asText()).isEqualTo("2024-03-01");
        assertThat(response.getContentAsString()).as("no whole employee number leaves the database")
                .doesNotContain("INV000001").doesNotContain("+27 60");
    }

    @Test
    @DisplayName("🔒 the contract's scope: the branch's area and its region are answered; another area or region is WORKPLACE_OUT_OF_SCOPE")
    void scoped() throws Exception {
        assertThat(membersAs(CallerScope.areas(Set.of("L"))).getStatus()).isEqualTo(200);
        assertThat(membersAs(CallerScope.regions(Set.of(region))).getStatus()).isEqualTo(200);

        MockHttpServletResponse area = membersAs(CallerScope.areas(Set.of("W")));
        assertThat(area.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(area.getContentAsString()).get("code").asText())
                .isEqualTo("WORKPLACE_OUT_OF_SCOPE");
        String otherRegion = jdbc.queryForObject("SELECT code FROM sasbo_regions WHERE code <> ? ORDER BY code LIMIT 1",
                String.class, region);
        assertThat(membersAs(CallerScope.regions(Set.of(otherRegion))).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("🔒 the scope is in the statement too: asked past the service's check, a caller of another area is handed no row")
    void theStatementIsScoped() {
        assertThat(members.page(branch, CallerScope.areas(Set.of("W")), 0, 50).rows()).isEmpty();
        assertThat(members.page(branch, CallerScope.areas(Set.of("L")), 0, 50).rows()).hasSize(3);
        assertThat(members.page(branch, CallerScope.all(), 1, 2).rows()).as("a page past the first").hasSize(1);
    }

    @Test
    @DisplayName("🔒 review: the members of a batch's branches in ONE read -- two statements for every branch, scoped in SQL, capped a branch with the branch's whole count beside")
    void theBatchsMembersInTwoStatements() {
        Long elsewhere = jdbc.queryForObject("SELECT w.id FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id "
                + "WHERE a.letter = 'W' ORDER BY w.ra_number LIMIT 1", Long.class);
        DataSource counted = ConnectionStatements.wrap(dataSource);
        NamedParameterJdbcTemplate countedJdbc = new NamedParameterJdbcTemplate(counted);
        WorkplaceListingService batch = new WorkplaceListingService(new WorkplaceRepository(countedJdbc),
                new WorkplaceVisitRepository(counted), new WorkplaceLookupRepository(countedJdbc),
                new WorkplaceMemberRepository(countedJdbc), workplacePages, workplaceSwitch);

        WorkplaceMembersDto[] answer = new WorkplaceMembersDto[1];
        long statements = ConnectionStatements.during(() -> answer[0] = batch.membersOf(
                List.of(String.valueOf(branch), String.valueOf(elsewhere))));

        assertThat(statements).as("the branches once, their members once -- never a statement a branch").isEqualTo(2);
        assertThat(answer[0].branches()).extracting(WorkplaceMembersDto.Branch::workplaceId)
                .containsExactly(branch, elsewhere);
        assertThat(answer[0].branches().get(0).members()).extracting(WorkplaceMemberPageDto.Member::surname)
                .containsExactly("INVENTED-A", "INVENTED-B", "INVENTED-C");
        assertThat(answer[0].branches().get(1).totalElements()).isEqualTo(1L);

        assertThat(members.forWorkplaces(List.of(branch, elsewhere), CallerScope.areas(Set.of("L")), 500))
                .as("the statement itself keeps a caller of area L to area L's branch")
                .allSatisfy(row -> assertThat(row.workplaceId()).isEqualTo(branch)).hasSize(3);
        assertThat(members.forWorkplaces(List.of(branch), CallerScope.all(), 2))
                .as("capped a branch, the branch's whole count beside each row")
                .allSatisfy(row -> assertThat(row.branchTotal()).isEqualTo(3L)).hasSize(2);
    }

    @Test
    @DisplayName("🔒 MR-PERF: a batch print of SASBO's numbers is two statements -- the workplaces, then their visits -- in the order typed, the unknown said")
    void theBatchIsTwoStatements() {
        List<Integer> numbers = jdbc.queryForList("SELECT w.ra_number FROM workplaces w JOIN sasbo_areas a "
                + "ON a.id = w.area_id WHERE a.letter = 'L' ORDER BY w.ra_number DESC LIMIT 3", Integer.class);
        Integer none = jdbc.queryForObject("SELECT max(ra_number) + 1 FROM workplaces", Integer.class);
        DataSource counted = ConnectionStatements.wrap(dataSource);
        WorkplaceListingService batch = new WorkplaceListingService(
                new WorkplaceRepository(new NamedParameterJdbcTemplate(counted)), new WorkplaceVisitRepository(counted),
                places, members, workplacePages, workplaceSwitch);
        List<String> typed = List.of(String.valueOf(numbers.get(0)), String.valueOf(none), String.valueOf(numbers.get(1)),
                String.valueOf(numbers.get(2)), String.valueOf(numbers.get(0)));

        WorkplaceListingsDto[] answer = new WorkplaceListingsDto[1];
        long statements = ConnectionStatements.during(() -> answer[0] = batch.listings(typed));

        assertThat(statements).isEqualTo(2);
        assertThat(answer[0].listings()).extracting(listing -> listing.workplace().raNumber())
                .containsExactly(numbers.get(0), numbers.get(1), numbers.get(2));
        assertThat(answer[0].unknown()).containsExactly(none);
        assertThat(answer[0].listings().get(0).workplace().staffComplement()).isNotNull();
    }
}
