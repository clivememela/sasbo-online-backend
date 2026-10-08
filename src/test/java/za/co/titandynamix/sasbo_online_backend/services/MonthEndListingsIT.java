package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SignInSeeder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A month's listings on PostgreSQL (month end 04.3, 04.7, 04.8; switched on with sign-in): who each lists, and that the
 * caller's scope is applied IN SQL -- a region's secretary reads their region, an organiser their area, and a member
 * with no workplace is listed for everything only (contracts/permissions.md). The fixture's September, invented.
 */
@DisplayName("Month end's listings, on PostgreSQL")
class MonthEndListingsIT extends AbstractSwitchedOnIT {

    /** A hash of BCrypt's shape for the one real user, made once; the strength does not matter to the test. */
    private static final String HASH = new BCryptPasswordEncoder(4).encode("an invented phrase");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndListingRepository listingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignInSeeder seeder;

    /**
     * The staff user behind every caller here. The record of who did what names the user's id, a foreign key to
     * {@code staff_users} (users are deactivated, never deleted), so a caller who reads a listing must be one on record.
     */
    private long holder;

    private SignedInUser headOffice;
    private SignedInUser south;
    private SignedInUser areaA;

    @BeforeEach
    void theFixturesSeptember() {
        SummaryFixture.insert(jdbc, members);
        // One more new member in September with no workplace on record -- listed for everything only.
        jdbc.update("UPDATE members SET join_date = DATE '2026-09-03' WHERE membership_number = 'M-800019'");
        // A recruiter for one of September's new members, and two shop stewards: one on record, one who left.
        jdbc.update("UPDATE members SET recruiter_id = (SELECT MIN(id) FROM recruiters) WHERE membership_number = "
                + "'M-800008'");
        jdbc.update("UPDATE members SET is_shop_steward = TRUE WHERE membership_number IN ('M-800001', 'M-800015')");
        jdbc.update("UPDATE members SET is_bec = TRUE WHERE membership_number = 'M-800001'");
        seeder.seedPositionsAndRoles();
        holder = jdbc.queryForObject("INSERT INTO staff_users (username, display_name, password_hash, position_id, "
                + "role_id, scope_type, must_change_password, created_at, created_by, updated_at, updated_by) "
                + "SELECT 'invented.reader', 'Invented Reader', ?, p.id, p.default_role_id, 'ALL', FALSE, now(), 'test', "
                + "now(), 'test' FROM staff_positions p WHERE p.code = 'HEAD_OFFICE_ADMINISTRATOR' RETURNING id",
                Long.class, HASH);
        headOffice = caller("invented.headoffice", CallerScope.all());
        south = caller("invented.regional", CallerScope.regions(Set.of("SOUTH")));
        areaA = caller("invented.organiser", CallerScope.areas(Set.of("A")));
    }

    /** A caller holding MEMBERS_VIEW and seeing {@code scope}, on record as the one real user. */
    private SignedInUser caller(String username, CallerScope scope) {
        return new SignedInUser(holder, 1, username, "Invented " + username, scope, Set.of(Permission.MEMBERS_VIEW),
                false);
    }

    private JsonNode listing(SignedInUser caller, String listing, String query) throws Exception {
        String body = mockMvc.perform(get("/api/month-end/2026-09/listings/" + listing + query)
                        .with(SignInTestUsers.signedIn(caller)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<String> numbers(JsonNode answer) {
        List<String> numbers = new ArrayList<>();
        answer.path("content").forEach(row -> numbers.add(row.path("membershipNumber").asText()));
        return numbers;
    }

    @Test
    @DisplayName("the month's new members: everyone's for everything, the member with no workplace among them")
    void newMembersForEverything() throws Exception {
        JsonNode answer = listing(headOffice, "new-members", "");
        assertThat(numbers(answer)).containsExactly("M-800017", "M-800019", "M-800008");
        assertThat(answer.path("scope").asText()).isEqualTo("ALL");
        assertThat(answer.path("state").asText()).isEqualTo("OPEN");
        assertThat(answer.path("content").get(0).path("date").asText()).isEqualTo("2026-09-02");
    }

    @Test
    @DisplayName("🔒 a region reads its region only -- never another's, never a member with no workplace")
    void aRegionReadsItsRegion() throws Exception {
        assertThat(numbers(listing(south, "new-members", ""))).containsExactly("M-800017");
        JsonNode deleted = listing(south, "deleted-members", "");
        assertThat(numbers(deleted)).containsExactly("M-800015", "M-800017");
        assertThat(deleted.path("content").get(0).path("code").asInt()).isEqualTo(2);
        assertThat(deleted.path("content").get(1).path("code").asInt()).isEqualTo(5);
        assertThat(deleted.path("content").get(1).path("date").asText()).isEqualTo("2026-09-12");
    }

    @Test
    @DisplayName("🔒 an area reads its area only; a region or area asked beyond it reads nothing")
    void anAreaReadsItsArea() throws Exception {
        assertThat(numbers(listing(areaA, "new-members", ""))).containsExactly("M-800008");
        assertThat(numbers(listing(areaA, "deleted-members", ""))).isEmpty();
        assertThat(numbers(listing(areaA, "new-members", "?area=B"))).isEmpty();
        assertThat(numbers(listing(headOffice, "new-members", "?region=SOUTH"))).containsExactly("M-800017");
    }

    @Test
    @DisplayName("the recruited, with their recruiter; the representatives on record, the leaver among them left out")
    void recruitedAndRepresentatives() throws Exception {
        JsonNode recruited = listing(headOffice, "recruited", "");
        assertThat(numbers(recruited)).containsExactly("M-800008");
        assertThat(recruited.path("content").get(0).path("recruiter").asText()).isNotBlank();
        JsonNode representatives = listing(headOffice, "representatives", "");
        assertThat(numbers(representatives)).containsExactly("M-800001");
        assertThat(representatives.path("content").get(0).path("role").asText())
                .isEqualTo("Shop steward and BEC member");
        assertThat(numbers(listing(south, "representatives", ""))).isEmpty();
    }

    @Test
    @DisplayName("a page at a time, counted; and each page read is a line in the record of who did what")
    void pagedAndRecorded() throws Exception {
        JsonNode first = listing(headOffice, "new-members", "?page=0&size=2");
        assertThat(numbers(first)).containsExactly("M-800017", "M-800019");
        assertThat(first.path("totalElements").asLong()).isEqualTo(3);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(numbers(listing(headOffice, "new-members", "?page=1&size=2"))).containsExactly("M-800008");
        assertThat(jdbc.queryForList("SELECT username FROM audit_events WHERE action = 'MONTH_END_LISTING_READ'",
                String.class)).containsOnly("invented.headoffice").hasSize(2);
    }

    @Test
    @DisplayName("the repository counts what it pages, scope applied the same way")
    void theCountIsThePagesCount() {
        MonthRange september = MonthRange.of(SummaryFixture.MONTH);
        assertThat(listingRepository.count(MonthEndListingRepository.Kind.NEW_MEMBERS, september,
                CallerScope.areas(Set.of("A")), null, null)).isEqualTo(1);
        assertThat(listingRepository.page(MonthEndListingRepository.Kind.NEW_MEMBERS, september,
                CallerScope.regions(Set.of("NORTH")), null, null, 0, 10)).extracting(r -> r.membershipNumber())
                .containsExactly("M-800008");
    }
}
