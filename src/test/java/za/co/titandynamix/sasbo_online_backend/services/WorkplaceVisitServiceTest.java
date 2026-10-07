package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitRequest;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceLookupRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch visits, the service alone (month end A.1): a visit that finds another complement writes it and keeps what it
 * was, one that finds the same writes nothing of the workplace; every refusal before anything is written; and the
 * page rules the history's route keeps too.
 */
@DisplayName("Branch visits, the service")
class WorkplaceVisitServiceTest {

    /** The middle of September 2026 in SASBO's zone -- the day a visit may be on, at the latest. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T08:00:00Z"), AcbTime.ZONE);

    private WorkplaceVisitRepository visits;
    private WorkplaceLookupRepository places;
    private WorkplaceSwitch workplaceSwitch;
    private WorkplaceVisitService service;

    @BeforeEach
    void aWorkplaceOfTwenty() {
        visits = mock(WorkplaceVisitRepository.class);
        workplaceSwitch = mock(WorkplaceSwitch.class);
        when(workplaceSwitch.workplacesOnRecord()).thenReturn(true);
        when(visits.complementOf(7L)).thenReturn(Optional.of(new Integer[] {20}));
        when(visits.lockComplementOf(7L)).thenReturn(Optional.of(new Integer[] {20}));
        when(visits.insert(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(99L);
        places = mock(WorkplaceLookupRepository.class);
        when(places.byId(7L)).thenReturn(Optional.of(new WorkplaceLookupRepository.Place(7, 900001,
                "INVENTED MAIN STREET", "L", "DBN")));
        service = new WorkplaceVisitService(visits, places, workplaceSwitch, CLOCK);
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInOfArea(String area) {
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
                return CallerScope.areas(Set.of(area));
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                AuthorityUtils.createAuthorityList("VISITS_CAPTURE")));
    }

    @Test
    @DisplayName("🔒 review: an organiser of area W cannot record a visit to (and write the complement of) a workplace that is not in W -- by any route")
    void anOrganiserOfAnotherAreaIsRefused() {
        signedInOfArea("W");

        assertCode(() -> service.record(7L, visit(LocalDate.of(2026, 9, 10), 55)),
                MonthEndRefusalCode.WORKPLACE_OUT_OF_SCOPE);
        verify(visits, never()).setComplement(anyLong(), anyInt());
        verify(visits, never()).insert(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("must-pass: the workplace's own area records it -- read FOR UPDATE, so two visits at once are written one after the other")
    void theWorkplacesOwnAreaRecordsIt() {
        signedInOfArea("L");

        assertThat(service.record(7L, visit(LocalDate.of(2026, 9, 10), 55)).complementBefore()).isEqualTo(20);
        verify(visits).lockComplementOf(7L);
        verify(visits).setComplement(7L, 55);
    }

    private static WorkplaceVisitRequest visit(LocalDate on, Integer complement) {
        return new WorkplaceVisitRequest(on.toString(), null, null, "INVENTED ORGANISER", complement, 12, null, 1, null);
    }

    @Test
    @DisplayName("🔒 A.1: a visit that finds another complement writes it onto the workplace, and keeps what it was")
    void anotherComplementIsWritten() {
        WorkplaceVisitPageDto.Visit recorded = service.record(7L, visit(LocalDate.of(2026, 9, 10), 23));

        verify(visits).setComplement(7L, 23);
        verify(visits).insert(eq(7L), any(), eq(LocalDate.of(2026, 9, 10)), isNull(), isNull(), eq(20), any());
        assertThat(recorded.complementFound()).isEqualTo(23);
        assertThat(recorded.complementBefore()).isEqualTo(20);
        assertThat(recorded.dayKnown()).isTrue();
        assertThat(recorded.seeded()).isFalse();
    }

    @Test
    @DisplayName("the must-pass: one that finds the same complement -- or none -- writes nothing of the workplace")
    void theSameComplementWritesNothing() {
        assertThat(service.record(7L, visit(LocalDate.of(2026, 9, 10), 20)).complementBefore()).isNull();
        assertThat(service.record(7L, visit(LocalDate.of(2026, 9, 10), null)).complementBefore()).isNull();

        verify(visits, never()).setComplement(anyLong(), anyInt());
        verify(visits, org.mockito.Mockito.times(2)).insert(eq(7L), any(), any(), any(), any(), isNull(), any());
    }

    @Test
    @DisplayName("🔒 A.1: every refusal is said before anything is written -- the field named")
    void theRefusals() {
        assertInvalid(new WorkplaceVisitRequest(null, null, null, null, 23, null, null, null, null), "visitedOn");
        assertInvalid(visit(LocalDate.of(2026, 9, 16), 23), "visitedOn");
        assertInvalid(new WorkplaceVisitRequest("2026-09-10", null, null, null, 23, -1, null, null, null),
                "membershipFound");
        assertInvalid(new WorkplaceVisitRequest("2026-09-10", "11:00", "10:00", null, 23, null, null, null, null),
                "timeOut");
        assertInvalid(new WorkplaceVisitRequest("2026-09-10", null, null, "X".repeat(121), 23, null, null, null, null),
                "organiser");
        assertInvalid(new WorkplaceVisitRequest("10 September", null, null, null, 23, null, null, null, null),
                "visitedOn");
        assertInvalid(new WorkplaceVisitRequest("2026-09-10", "nine", null, null, 23, null, null, null, null),
                "timeIn");
        // October build: a count has a ceiling -- a staff of a hundred thousand is a typing slip.
        assertInvalid(new WorkplaceVisitRequest("2026-09-10", null, null, null, 100_000, null, null, null, null),
                "complementFound");

        verify(visits, never()).setComplement(anyLong(), anyInt());
        verify(visits, never()).insert(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the must-pass: today itself, SASBO's day, is a day a visit may be on")
    void todayIsADay() {
        assertThat(service.record(7L, visit(LocalDate.of(2026, 9, 15), 23)).visitedOn()).isEqualTo("2026-09-15");
    }

    @Test
    @DisplayName("a workplace not on record, or none served here, is WORKPLACE_UNKNOWN -- for the read and the write")
    void aWorkplaceNotOnRecord() {
        when(visits.complementOf(8L)).thenReturn(Optional.empty());
        assertCode(() -> service.record(8L, visit(LocalDate.of(2026, 9, 10), 23)), MonthEndRefusalCode.WORKPLACE_UNKNOWN);
        assertCode(() -> service.page(8L, null, null), MonthEndRefusalCode.WORKPLACE_UNKNOWN);

        when(workplaceSwitch.workplacesOnRecord()).thenReturn(false);
        assertCode(() -> service.record(7L, visit(LocalDate.of(2026, 9, 10), 23)), MonthEndRefusalCode.WORKPLACE_UNKNOWN);
        assertCode(() -> service.page(7L, null, null), MonthEndRefusalCode.WORKPLACE_UNKNOWN);
    }

    @Test
    @DisplayName("a page that does not exist is WORKPLACE_PAGE_INVALID; the first, even empty, is answered -- and names what it withholds")
    void thePages() {
        when(visits.count(7L)).thenReturn(0L);
        when(visits.page(7L, 0, 20)).thenReturn(List.of());
        WorkplaceVisitPageDto first = service.page(7L, null, null);
        assertThat(first.visits()).isEmpty();
        assertThat(first.withheld()).containsExactly("organiser", "membershipFound", "nonMembers", "recruited",
                "nonMembersInterviewed");

        assertCode(() -> service.page(7L, -1, null), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
        assertCode(() -> service.page(7L, 0, 201), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
        when(visits.page(7L, 3, 20)).thenReturn(List.of());
        assertCode(() -> service.page(7L, 3, null), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
    }

    private void assertInvalid(WorkplaceVisitRequest request, String field) {
        assertThatThrownBy(() -> service.record(7L, request))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.VISIT_INVALID);
                    assertThat(refusal.details()).containsEntry("field", field);
                });
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, MonthEndRefusalCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(code));
    }
}
