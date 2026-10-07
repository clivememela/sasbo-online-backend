package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbCodeService;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call each Debit Order route -- under the application's real security configuration, filter chain on and
 * method security enforced (the October build's permissions; Stephne, 6 October 2026).
 *
 * <h2>What each gated route is held to</h2>
 * For every route behind a permission: nobody signed in is refused and the services are never asked; a user signed
 * in without the permission is refused 403; an ORGANISER, holding every organiser permission there is, is refused
 * 403 on every one of them -- organisers never see unpaids (DOD-14); and the permission's holder is let through. A
 * 403 can only be the permission refusing, because the must-pass twins at the foot read open routes through the same
 * chain and get a 200.
 *
 * <p>The routes that refused everyone with {@code IDENTITY_REQUIRED} until the October build are the first twelve rows
 * of {@link #gated()} -- the suite's journeys on them must sign in (OB30). That code is retired (OB29).
 */
@WebMvcTest(ACBController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
@DisplayName("ACBController, with the application's real security configuration")
class ACBControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcbRunService runs;

    @MockitoBean
    private AcbReturnsService returns;

    @MockitoBean
    private AcbCalendarService calendar;

    @MockitoBean
    private AcbOpeningPositionService openingPosition;

    @MockitoBean
    private AcbRateService rates;

    @MockitoBean
    private AcbCodeService codes;

    @MockitoBean
    private AcbUnpaidCountService unpaidCounts;

    @MockitoBean
    private AcbDebitDayService debitDays;

    /** Every permission an organiser's default role holds (contracts/permissions.md). */
    static final String[] ORGANISER = {Permission.MEMBERS_VIEW.name(), Permission.MEMBERS_CAPTURE.name(),
            Permission.VISITS_CAPTURE.name(), Permission.REPORTS_PRINT.name()};

    /** One gated route: how it is called, and the permissions any one of which lets a caller through. */
    record Gated(String what, HttpMethod method, String path, String body, Set<String> permissions) {

        MockHttpServletRequestBuilder call() {
            MockHttpServletRequestBuilder call = request(method, path);
            return body == null ? call : call.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        @Override
        public String toString() {
            return what;
        }
    }

    private static Gated gated(String what, HttpMethod method, String path, String body, Permission... any) {
        return new Gated(what, method, path, body,
                Arrays.stream(any).map(Permission::name).collect(Collectors.toUnmodifiableSet()));
    }

    static Stream<Gated> gated() {
        String transition = "{\"version\": 3}";
        return Stream.of(
                // The twelve that refused every caller with IDENTITY_REQUIRED on main (OB30).
                gated("approve a run", HttpMethod.POST, "/api/acb/runs/41/approve", transition, Permission.DOD_PREPARE),
                gated("download the file for the bank", HttpMethod.GET, "/api/acb/runs/41/file", null, Permission.DOD_FILE),
                gated("record the submission", HttpMethod.POST, "/api/acb/runs/41/submitted", transition, Permission.DOD_FILE),
                gated("record a declined file", HttpMethod.POST, "/api/acb/runs/41/declined",
                        "{\"version\": 3, \"reason\": \"WRONG_DEBIT_DATE\"}", Permission.DOD_FILE),
                gated("send a held letter", HttpMethod.POST, "/api/acb/runs/41/letters/9/send", null, Permission.DOD_PREPARE),
                gated("confirm an ending", HttpMethod.POST, "/api/acb/termination-report/7/terminate", null,
                        Permission.DOD_PREPARE),
                gated("suspend an unpaid's member", HttpMethod.POST, "/api/acb/runs/41/lines/9/suspend-member",
                        "{\"suspensionCode\": 16}", Permission.DOD_PREPARE),
                gated("add a rate", HttpMethod.POST, "/api/acb/rates",
                        "{\"effectiveFrom\": \"2027-01-01\", \"amount\": 185.00, \"source\": \"AGM\"}",
                        Permission.DOD_SETTINGS, Permission.SUBSCRIPTIONS_MAINTAIN),
                gated("upload the opening position", HttpMethod.POST, "/api/acb/opening-position", null,
                        Permission.DOD_PREPARE),
                gated("add a non-working day", HttpMethod.POST, "/api/acb/holidays",
                        "{\"date\": \"2026-10-28\", \"name\": \"Declared\", \"kind\": \"BANK_CLOSED\"}",
                        Permission.DOD_SETTINGS),
                gated("remove a non-working day", HttpMethod.DELETE, "/api/acb/holidays/2026-10-28", null,
                        Permission.DOD_SETTINGS),
                gated("extend the calendar", HttpMethod.POST, "/api/acb/calendar/known-until",
                        "{\"knownUntil\": \"2029-12-31\"}", Permission.DOD_SETTINGS),
                // New in the October build.
                gated("the calendar's setting", HttpMethod.PUT, "/api/acb/calendar/settings",
                        "{\"bankClosuresCount\": false}", Permission.DOD_SETTINGS),
                gated("read the codes", HttpMethod.GET, "/api/acb/codes", null,
                        Permission.DOD_PREPARE, Permission.DOD_FILE, Permission.DOD_SETTINGS),
                gated("change a code", HttpMethod.PUT, "/api/acb/codes/6",
                        "{\"version\": 0, \"reDebit\": \"NO\", \"suspensionCode\": 13, \"wording\": \"Debit returned\", "
                                + "\"provisional\": false, \"note\": \"SASBO, 9 October\"}", Permission.DOD_SETTINGS),
                gated("add a code", HttpMethod.POST, "/api/acb/codes",
                        "{\"bankCode\": \"8\", \"reason\": \"OTHER\", \"wording\": \"Other\", \"reDebit\": \"UNKNOWN\", "
                                + "\"note\": \"the bank's list\"}", Permission.DOD_SETTINGS),
                gated("a run's debit days", HttpMethod.GET, "/api/acb/runs/41/debit-days", null,
                        Permission.DOD_PREPARE, Permission.DOD_FILE, Permission.DOD_SETTINGS),
                gated("record a debit day's returns", HttpMethod.POST, "/api/acb/runs/41/returns",
                        "{\"actionDate\": \"2026-10-27\", \"entries\": [{\"lineId\": 9, \"code\": \"2\"}]}",
                        Permission.DOD_PREPARE),
                gated("the returns reports", HttpMethod.GET, "/api/acb/runs/41/returns/batches", null,
                        Permission.DOD_PREPARE, Permission.DOD_FILE, Permission.DOD_SETTINGS),
                gated("read the unpaid counts", HttpMethod.GET, "/api/acb/unpaid-counts", null, Permission.DOD_PREPARE),
                gated("capture unpaid counts", HttpMethod.PUT, "/api/acb/unpaid-counts",
                        "{\"source\": \"Re-debit list\", \"entries\": [{\"membershipNumber\": \"M-095601\", "
                                + "\"unpaidCount\": 2}]}", Permission.DOD_PREPARE),
                gated("reactivate a member", HttpMethod.POST, "/api/acb/members/7/reactivate", null,
                        Permission.MEMBERS_EDIT),
                gated("a member rejoins", HttpMethod.POST, "/api/acb/members/7/rejoin", null, Permission.MEMBERS_EDIT),
                gated("the banks", HttpMethod.GET, "/api/acb/banks", null, Permission.DOD_SETTINGS),
                gated("preview a debit day move", HttpMethod.GET, "/api/acb/banks/3/debit-day-move?day=27", null,
                        Permission.DOD_SETTINGS),
                gated("move a bank's members", HttpMethod.POST, "/api/acb/banks/3/debit-day-move",
                        "{\"newDay\": 27, \"expectedCount\": 42}", Permission.DOD_SETTINGS));
    }

    private void verifyNoServiceWasAsked() {
        verifyNoInteractions(runs, returns, calendar, openingPosition, rates, codes, unpaidCounts, debitDays);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gated")
    @DisplayName("nobody signed in is refused, and no service is asked")
    void anonymousIsRefused(Gated route) throws Exception {
        MvcResult result = mockMvc.perform(route.call().with(anonymous())).andReturn();
        assertThat(result.getResponse().getStatus()).as("not 2xx, until sign-in's entry point says 401").isBetween(400, 499);
        verifyNoServiceWasAsked();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gated")
    @DisplayName("a user without the permission is refused 403, and no service is asked")
    void theWrongPermissionIsRefused(Gated route) throws Exception {
        String other = Arrays.stream(Permission.values()).map(Permission::name)
                .filter(name -> !route.permissions().contains(name)).findFirst().orElseThrow();
        mockMvc.perform(route.call().with(user("someone").authorities(() -> other)))
                .andExpect(status().isForbidden());
        verifyNoServiceWasAsked();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gated")
    @DisplayName("🔒 DOD-14: an organiser, with every organiser permission, is refused 403")
    void organisersAreRefused(Gated route) throws Exception {
        mockMvc.perform(route.call().with(user("organiser").authorities(
                        Arrays.stream(ORGANISER).map(name -> (org.springframework.security.core.GrantedAuthority) () -> name)
                                .toList())))
                .andExpect(status().isForbidden());
        verifyNoServiceWasAsked();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gated")
    @DisplayName("the permission's holder is let through -- each permission that opens it")
    void theHolderIsLetThrough(Gated route) throws Exception {
        when(runs.download(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AcbRunService.Download(new za.co.titandynamix.sasbo_online_backend.services.AcbFileService
                        .RenderedFile("sasbosub.oct", "a".repeat(64), new byte[] {1}, 4, 1,
                        new java.math.BigDecimal("174.00")), 1));
        for (String permission : route.permissions()) {
            MvcResult result = mockMvc.perform(route.call().with(user("holder").authorities(() -> permission)))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as("%s with %s", route, permission).isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("🔒 OB29: IDENTITY_REQUIRED is retired -- no Debit Order refusal code says it")
    void identityRequiredIsRetired() {
        assertThat(Arrays.stream(za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode.values())
                .map(Enum::name)).doesNotContain("IDENTITY_REQUIRED");
    }

    // ------------------------------------------------------------------------------------------
    // The must-pass twins: open routes through the same chain
    // ------------------------------------------------------------------------------------------

    @Test
    @WithAnonymousUser
    @DisplayName("the must-pass twin: the calendar READS openly -- public holidays are public")
    void theCalendarReadsOpenly() throws Exception {
        when(this.calendar.calendar(null)).thenReturn(new AcbCalendarDto("za-statutory", "2028-12-31", false, 2026, List.of()));
        this.mockMvc.perform(get("/api/acb/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("za-statutory"))
                .andExpect(jsonPath("$.bankClosuresCount").value(true));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("the must-pass twin: the runs list still answers an anonymous caller while sign-in is open -- so the 403s above are the permissions'")
    void theChainPermitsTheOpenRoutes() throws Exception {
        when(this.runs.list(0, 50)).thenReturn(new AcbPage<>(List.of(), 0, 50, 0, 0));
        this.mockMvc.perform(get("/api/acb/runs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "DOD_FILE")
    @DisplayName("the must-pass twin: the CFO's permission alone downloads the file -- the holder is answered with its bytes")
    void theFileServesItsHolder() throws Exception {
        when(this.runs.download(41L, "system")).thenReturn(new AcbRunService.Download(
                new za.co.titandynamix.sasbo_online_backend.services.AcbFileService.RenderedFile("sasbosub.oct",
                        "a".repeat(64), new byte[] {1, 2, 3}, 4, 1, new java.math.BigDecimal("174.00")), 2));
        this.mockMvc.perform(get("/api/acb/runs/41/file"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"sasbosub.oct\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-File-Downloads", "2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
    }
}
