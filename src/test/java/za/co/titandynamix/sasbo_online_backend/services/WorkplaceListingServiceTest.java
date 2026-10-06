package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceListingsDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMemberPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceLookupRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceMemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Location Listing's rules, the service alone (October build, 5 Oct Show &amp; Tell): the organisers' batch print
 * reads up to fifty R/A numbers once each, in the order typed, says which are none and still answers the rest, from
 * two statements; and the members at a branch are listed only within the caller's region or area, masked.
 */
@DisplayName("The Location Listing, the service")
class WorkplaceListingServiceTest {

    private WorkplaceRepository workplaces;
    private WorkplaceVisitRepository visits;
    private WorkplaceLookupRepository places;
    private WorkplaceMemberRepository members;
    private WorkplaceService pages;
    private WorkplaceSwitch workplaceSwitch;
    private WorkplaceListingService service;

    @BeforeEach
    void everythingServed() {
        workplaces = mock(WorkplaceRepository.class);
        visits = mock(WorkplaceVisitRepository.class);
        places = mock(WorkplaceLookupRepository.class);
        members = mock(WorkplaceMemberRepository.class);
        pages = mock(WorkplaceService.class);
        workplaceSwitch = mock(WorkplaceSwitch.class);
        when(workplaceSwitch.workplacesOnRecord()).thenReturn(true);
        when(workplaceSwitch.staffNumbersServed()).thenReturn(true);
        when(workplaceSwitch.mode()).thenReturn(WorkplaceSwitch.Mode.ALL);
        service = new WorkplaceListingService(workplaces, visits, places, members, pages, workplaceSwitch);
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static WorkplaceDto workplace(long id, int ra, String employer, String area) {
        return new WorkplaceDto(id, ra, "INVENTED BRANCH " + ra, employer, 7L, employer, "INVENTED GROUP", 901, "NAME",
                area, "NORTH", "Q01", "INVENTED COMMITTEE", 40, null);
    }

    private static void signedInAs(CallerScope scope) {
        ScopedPrincipal principal = new ScopedPrincipal() {
            @Override
            public String username() {
                return "invented.organiser";
            }

            @Override
            public String displayName() {
                return "Invented Organiser";
            }

            @Override
            public CallerScope scope() {
                return scope;
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                AuthorityUtils.createAuthorityList("MEMBERS_VIEW")));
    }

    // ------------------------------------------------------------------------------------------- the numbers

    @Test
    @DisplayName("5 Oct: the numbers typed are read once each, in the order typed -- a repeat counted once")
    void readOnceEachInOrder() {
        assertThat(WorkplaceListingService.read(List.of(" 900003", "900001", "900003 ", "", "900002")))
                .containsExactly(900003, 900001, 900002);
    }

    @Test
    @DisplayName("🔒 the ceiling: fifty numbers print, the fifty-first is refused RA_NUMBERS_INVALID, TOO_MANY -- nothing looked up")
    void fiftyAtMost() {
        List<String> fifty = IntStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList();
        assertThat(WorkplaceListingService.read(fifty)).hasSize(50);

        List<String> fiftyOne = new ArrayList<>(fifty);
        fiftyOne.add("51");
        assertRefused(() -> service.listings(fiftyOne), "TOO_MANY");
        verifyNoInteractions(workplaces, visits);
    }

    @Test
    @DisplayName("none, or a number that is not one -- a letter, nought, ten digits -- is RA_NUMBERS_INVALID, saying which")
    void unreadableNumbers() {
        assertRefused(() -> service.listings(null), "NONE");
        assertRefused(() -> service.listings(List.of(" ", "")), "NONE");
        assertRefused(() -> service.listings(List.of("900001", "12A")), "NOT_A_NUMBER");
        assertRefused(() -> service.listings(List.of("0")), "NOT_A_NUMBER");
        assertRefused(() -> service.listings(List.of("1234567890")), "NOT_A_NUMBER");
        assertRefused(() -> service.listings(List.of("-5")), "NOT_A_NUMBER");
        verifyNoInteractions(workplaces, visits);
    }

    // ------------------------------------------------------------------------------------------- the batch

    @Test
    @DisplayName("🔒 5 Oct: the workplaces in the order asked, the numbers that are none said -- and the rest still answered")
    void inTheOrderAskedWithTheUnknown() {
        when(workplaces.byRaNumbers(List.of(900003, 900099, 900001), true)).thenReturn(List.of(
                workplace(1, 900001, "INVENTED BANK", "A"), workplace(3, 900003, "INVENTED INSURER", "B")));
        when(visits.newest(any(), eq(10))).thenReturn(List.of(
                new WorkplaceVisitRepository.Newest(1, LocalDate.of(2026, 8, 20), true),
                new WorkplaceVisitRepository.Newest(1, LocalDate.of(2026, 7, 1), false)));

        WorkplaceListingsDto answer = service.listings(List.of("900003", "900099", "900001"));

        assertThat(answer.asked()).containsExactly(900003, 900099, 900001);
        assertThat(answer.listings()).extracting(listing -> listing.workplace().raNumber())
                .containsExactly(900003, 900001);
        assertThat(answer.unknown()).containsExactly(900099);
        assertThat(answer.listings().get(1).lastVisited()).containsExactly(
                new WorkplaceListingsDto.LastVisit("2026-08-20", true),
                new WorkplaceListingsDto.LastVisit("2026-07-01", false));
        assertThat(answer.listings().get(0).lastVisited()).isEmpty();
        assertThat(answer.workplacesServed()).isEqualTo("ALL");
        assertThat(answer.reason()).isNull();
    }

    @Test
    @DisplayName("🔒 MR-PERF: two statements for the whole batch -- the workplaces once, their visits once -- never one a number")
    void twoStatementsForTheBatch() {
        List<String> twenty = IntStream.rangeClosed(900001, 900020).mapToObj(String::valueOf).toList();
        List<WorkplaceDto> found = IntStream.rangeClosed(1, 20)
                .mapToObj(n -> workplace(n, 900000 + n, "INVENTED BANK", "A")).toList();
        when(workplaces.byRaNumbers(any(), anyBoolean())).thenReturn(found);

        assertThat(service.listings(twenty).listings()).hasSize(20);

        verify(workplaces, org.mockito.Mockito.times(1)).byRaNumbers(any(), eq(true));
        verify(visits, org.mockito.Mockito.times(1)).newest(any(), eq(10));
    }

    @Test
    @DisplayName("🔒 MD62: below all, nothing made from a staff number -- no complement asked for, and no visit read")
    void namesOnly() {
        when(workplaceSwitch.staffNumbersServed()).thenReturn(false);
        when(workplaceSwitch.mode()).thenReturn(WorkplaceSwitch.Mode.NAMES);
        when(workplaces.byRaNumbers(List.of(900001), false)).thenReturn(List.of(workplace(1, 900001, "INVENTED BANK",
                "A")));

        WorkplaceListingsDto answer = service.listings(List.of("900001"));

        assertThat(answer.workplacesServed()).isEqualTo("NAMES");
        assertThat(answer.listings().get(0).lastVisited()).isEmpty();
        verify(workplaces).byRaNumbers(List.of(900001), false);
        verifyNoInteractions(visits);
    }

    @Test
    @DisplayName("MD62, ME-19: at none, nothing is read -- the numbers are kept and the reason says the workplaces are not loaded")
    void noneServed() {
        when(workplaceSwitch.workplacesOnRecord()).thenReturn(false);
        when(workplaceSwitch.mode()).thenReturn(WorkplaceSwitch.Mode.NONE);

        WorkplaceListingsDto answer = service.listings(List.of("900001", "900002"));

        assertThat(answer.asked()).containsExactly(900001, 900002);
        assertThat(answer.listings()).isEmpty();
        assertThat(answer.unknown()).isEmpty();
        assertThat(answer.reason()).isEqualTo("NOT_LOADED");
        verifyNoInteractions(workplaces, visits);
    }

    @Test
    @DisplayName("ME-19: where not one is found, the workplaces' own reason says why none could be")
    void notOneFound() {
        when(workplaces.byRaNumbers(any(), anyBoolean())).thenReturn(List.of());
        when(pages.page(0, 1, null, null, null, null, null))
                .thenReturn(new WorkplacePageDto(List.of(), 0, 1, 0, 0, "FAILED"));

        WorkplaceListingsDto answer = service.listings(List.of("900001"));

        assertThat(answer.unknown()).containsExactly(900001);
        assertThat(answer.reason()).isEqualTo("FAILED");
        verifyNoInteractions(visits);
    }

    // ------------------------------------------------------------------------------------------- the members

    private static WorkplaceMemberRepository.Row member(int id, String status, String tail) {
        return new WorkplaceMemberRepository.Row(id, "M-09990" + id, "MS", "A", "INVENTED", status,
                Date.valueOf(LocalDate.of(2024, 3, 1)), tail, new BigDecimal("174.00"), true, false, true);
    }

    @Test
    @DisplayName("🔒 5 Oct: the members at a branch, masked -- the employee number's last four, a short one all stars, none none")
    void theMembersMasked() {
        when(places.byId(1L)).thenReturn(Optional.of(new WorkplaceLookupRepository.Place(1, 900001, "INVENTED BRANCH",
                "A", "NORTH")));
        when(members.page(eq(1L), any(), eq(0), eq(50))).thenReturn(new WorkplaceMemberRepository.Slice(List.of(
                member(1, "Active", "0042"), member(2, "Suspended", ""), member(3, "Pending", null)), 3));

        WorkplaceMemberPageDto page = service.members(1L, null, null);

        assertThat(page.members()).extracting(WorkplaceMemberPageDto.Member::employeeNumber)
                .containsExactly("****0042", "****", null);
        assertThat(page.members()).extracting(WorkplaceMemberPageDto.Member::suspended)
                .containsExactly(false, true, false);
        assertThat(page.members().get(0).joinedOn()).isEqualTo("2024-03-01");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 the contract's scope: a branch outside the caller's area is WORKPLACE_OUT_OF_SCOPE -- and nothing is read")
    void outsideTheCallersArea() {
        when(places.byId(1L)).thenReturn(Optional.of(new WorkplaceLookupRepository.Place(1, 900001, "INVENTED BRANCH",
                "A", "NORTH")));
        signedInAs(CallerScope.areas(Set.of("B")));

        assertCode(() -> service.members(1L, null, null), MonthEndRefusalCode.WORKPLACE_OUT_OF_SCOPE);
        verify(members, never()).page(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("must-pass: the caller's own area, and their area's region, are listed -- with their scope handed to the statement")
    void insideTheCallersAreaOrRegion() {
        when(places.byId(1L)).thenReturn(Optional.of(new WorkplaceLookupRepository.Place(1, 900001, "INVENTED BRANCH",
                "A", "NORTH")));
        when(members.page(anyLong(), any(), anyInt(), anyInt())).thenReturn(new WorkplaceMemberRepository.Slice(
                List.of(member(1, "Active", "0042")), 1));

        CallerScope area = CallerScope.areas(Set.of("A"));
        signedInAs(area);
        assertThat(service.members(1L, null, null).members()).hasSize(1);
        verify(members).page(1L, area, 0, 50);

        CallerScope region = CallerScope.regions(Set.of("NORTH"));
        signedInAs(region);
        assertThat(service.members(1L, null, null).members()).hasSize(1);
        verify(members).page(1L, region, 0, 50);
    }

    @Test
    @DisplayName("a workplace not on record -- or none served here -- is WORKPLACE_UNKNOWN; a page that does not exist WORKPLACE_PAGE_INVALID")
    void unknownAndPages() {
        when(places.byId(8L)).thenReturn(Optional.empty());
        assertCode(() -> service.members(8L, null, null), MonthEndRefusalCode.WORKPLACE_UNKNOWN);

        when(places.byId(1L)).thenReturn(Optional.of(new WorkplaceLookupRepository.Place(1, 900001, "INVENTED BRANCH",
                "A", "NORTH")));
        when(workplaceSwitch.workplacesOnRecord()).thenReturn(false);
        assertCode(() -> service.members(1L, null, null), MonthEndRefusalCode.WORKPLACE_UNKNOWN);

        when(workplaceSwitch.workplacesOnRecord()).thenReturn(true);
        assertCode(() -> service.members(1L, -1, null), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
        assertCode(() -> service.members(1L, 0, 201), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
        when(members.page(1L, CallerScope.all(), 4, 50)).thenReturn(new WorkplaceMemberRepository.Slice(
                Collections.emptyList(), 0));
        assertCode(() -> service.members(1L, 4, null), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
    }

    private static void assertRefused(Runnable call, String reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.RA_NUMBERS_INVALID);
            assertThat(refusal.details()).containsEntry("reason", reason).containsEntry("max", "50");
        });
    }

    private static void assertCode(Runnable call, MonthEndRefusalCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(code));
    }
}
