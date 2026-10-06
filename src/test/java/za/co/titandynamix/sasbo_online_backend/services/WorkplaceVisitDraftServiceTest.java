package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftRequest;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftSubmitRequest;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceLookupRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitDraftRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The visit forms' rules, the service alone (October build, 5 Oct Show &amp; Tell): a draft is its owner's and is held
 * to a visit's rules, but for its day; a save from an older version is refused, never written over; a submission
 * records the visit through the one path that records one, and a submitted visit is read-only; a submission sent
 * twice answers the first.
 */
@DisplayName("The visit drafts, the service")
class WorkplaceVisitDraftServiceTest {

    /** The middle of September 2026 in SASBO's zone -- the latest day a visit may be on. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T08:00:00Z"), AcbTime.ZONE);

    private static final WorkplaceLookupRepository.Place MAIN_STREET =
            new WorkplaceLookupRepository.Place(7, 900001, "INVENTED MAIN STREET", "A", "NORTH");

    private WorkplaceVisitDraftRepository drafts;
    private WorkplaceLookupRepository places;
    private WorkplaceVisitRepository visitRows;
    private WorkplaceSwitch workplaceSwitch;
    private WorkplaceVisitDraftService service;

    @BeforeEach
    void anOrganiserOfAreaA() {
        drafts = mock(WorkplaceVisitDraftRepository.class);
        places = mock(WorkplaceLookupRepository.class);
        visitRows = mock(WorkplaceVisitRepository.class);
        workplaceSwitch = mock(WorkplaceSwitch.class);
        when(workplaceSwitch.workplacesOnRecord()).thenReturn(true);
        when(places.byRaNumber(900001)).thenReturn(Optional.of(MAIN_STREET));
        when(places.byId(7L)).thenReturn(Optional.of(MAIN_STREET));
        when(visitRows.complementOf(7L)).thenReturn(Optional.of(new Integer[] {40}));
        when(visitRows.insert(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(99L);
        when(drafts.insert(anyLong(), any(), anyString(), anyString(), any())).thenReturn(5L);
        when(drafts.one(eq(5L), anyString())).thenReturn(Optional.of(draft("DRAFT", "2026-09-10", 0)));
        WorkplaceVisitService visits = new WorkplaceVisitService(visitRows, workplaceSwitch, CLOCK);
        service = new WorkplaceVisitDraftService(drafts, places, visits, workplaceSwitch, CLOCK);
        signedInAs("invented.organiser", CallerScope.areas(Set.of("A")));
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(String username, CallerScope scope) {
        ScopedPrincipal principal = new ScopedPrincipal() {
            @Override
            public String username() {
                return username;
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
                AuthorityUtils.createAuthorityList("VISITS_CAPTURE")));
    }

    private static VisitDraftDto draft(String status, String day, int version) {
        return new VisitDraftDto(5, status, 7, 900001, "INVENTED MAIN STREET", day, "09:00", "10:30",
                "INVENTED ORGANISER", 43, null, 12, 3, 2, 4, "system", "2026-09-14T08:00:00Z", "2026-09-14T08:00:00Z",
                null, null, null, version);
    }

    private static VisitDraftRequest request(Integer ra, String day, Integer version) {
        return new VisitDraftRequest(ra, day, "09:00", "10:30", "INVENTED ORGANISER", 43, 12, 3, 2, 4, version);
    }

    private static WorkplaceVisitDraftRepository.Head head(String status, int version) {
        return new WorkplaceVisitDraftRepository.Head(5, status, 7, version, "SUBMITTED".equals(status) ? 99L : null,
                Instant.parse("2026-09-14T08:00:00Z"), "SUBMITTED".equals(status)
                ? Instant.parse("2026-09-14T09:00:00Z") : null);
    }

    // ------------------------------------------------------------------------------------------- saving

    @Test
    @DisplayName("🔒 5 Oct: a draft saved is the signed-in user's -- named as its owner -- with what the form found, the day and times read")
    void aDraftIsItsOwners() {
        VisitDraftDto saved = service.create(request(900001, "2026-09-10", null));

        verify(drafts).insert(eq(7L), eq(new WorkplaceVisitDraftRepository.Fields(LocalDate.of(2026, 9, 10),
                LocalTime.of(9, 0), LocalTime.of(10, 30), "INVENTED ORGANISER", 43, 12, 3, 2, 4)),
                eq("invented.organiser"), eq("system"), eq(CLOCK.instant()));
        assertThat(saved.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("must-pass: a draft may be saved before its day is chosen -- the day alone is not needed until it is submitted")
    void aDraftWithoutItsDay() {
        service.create(new VisitDraftRequest(900001, null, null, null, null, null, null, null, null, null, null));

        verify(drafts).insert(eq(7L), eq(new WorkplaceVisitDraftRepository.Fields(null, null, null, null, null, null,
                null, null, null)), eq("invented.organiser"), eq("system"), any());
    }

    @Test
    @DisplayName("🔒 held to a visit's rules -- each refusal names its field, and nothing is written")
    void heldToAVisitsRules() {
        assertInvalid(() -> service.create(request(null, "2026-09-10", null)), "raNumber");
        assertInvalid(() -> service.create(request(0, "2026-09-10", null)), "raNumber");
        assertInvalid(() -> service.create(request(900001, "2026-09-16", null)), "visitedOn");
        assertInvalid(() -> service.create(request(900001, "10 September", null)), "visitedOn");
        assertInvalid(() -> service.create(new VisitDraftRequest(900001, "2026-09-10", "11:00", "10:00", null, null,
                null, null, null, null, null)), "timeOut");
        assertInvalid(() -> service.create(new VisitDraftRequest(900001, "2026-09-10", null, null, "X".repeat(121),
                null, null, null, null, null, null)), "organiser");
        assertInvalid(() -> service.create(new VisitDraftRequest(900001, "2026-09-10", null, null, null, -1, null,
                null, null, null, null)), "complementFound");
        assertInvalid(() -> service.create(new VisitDraftRequest(900001, "2026-09-10", null, null, null, null,
                100_000, null, null, null, null)), "membershipFound");

        verify(drafts, never()).insert(anyLong(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a number that is no workplace here is WORKPLACE_UNKNOWN at the R/A number; one outside the caller's area WORKPLACE_OUT_OF_SCOPE")
    void theWorkplaceNamed() {
        when(places.byRaNumber(900099)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(900099, "2026-09-10", null)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_UNKNOWN);
                    assertThat(refusal.details()).containsEntry("field", "raNumber");
                });

        signedInAs("invented.organiser", CallerScope.areas(Set.of("B")));
        assertCode(() -> service.create(request(900001, "2026-09-10", null)),
                MonthEndRefusalCode.WORKPLACE_OUT_OF_SCOPE);

        when(workplaceSwitch.workplacesOnRecord()).thenReturn(false);
        assertCode(() -> service.create(request(900001, "2026-09-10", null)), MonthEndRefusalCode.WORKPLACE_UNKNOWN);
        verify(drafts, never()).insert(anyLong(), any(), anyString(), anyString(), any());
    }

    // ------------------------------------------------------------------------------------------- saving again

    @Test
    @DisplayName("🔒 two tabs, one draft: a save from an older version is VISIT_DRAFT_STALE, and nothing is written over the newer")
    void aStaleSave() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("DRAFT", 3)));

        assertThatThrownBy(() -> service.update(5L, request(900001, "2026-09-10", 2)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.VISIT_DRAFT_STALE);
                    assertThat(refusal.details()).containsEntry("version", "3");
                });
        verify(drafts, never()).update(anyLong(), anyString(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("must-pass: a save from the version on record is written, at that version")
    void aSaveAtItsVersion() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("DRAFT", 3)));
        when(drafts.update(eq(5L), eq("invented.organiser"), eq(3), eq(7L), any(), any())).thenReturn(true);

        service.update(5L, request(900001, "2026-09-10", 3));

        verify(drafts).update(eq(5L), eq("invented.organiser"), eq(3), eq(7L), any(), eq(CLOCK.instant()));
    }

    @Test
    @DisplayName("🔒 a submitted visit is read-only: saving over it is VISIT_ALREADY_SUBMITTED, naming the visit")
    void aSubmittedVisitIsReadOnly() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("SUBMITTED", 1)));

        assertThatThrownBy(() -> service.update(5L, request(900001, "2026-09-10", 1)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.VISIT_ALREADY_SUBMITTED);
                    assertThat(refusal.details()).containsEntry("visitId", "99");
                });
        verify(drafts, never()).update(anyLong(), anyString(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("🔒 another person's draft is never found -- VISIT_DRAFT_NOT_FOUND, read or written -- and a save names its version")
    void anotherPersonsDraft() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.empty());
        when(drafts.one(6L, "invented.organiser")).thenReturn(Optional.empty());

        assertCode(() -> service.update(5L, request(900001, "2026-09-10", 0)), MonthEndRefusalCode.VISIT_DRAFT_NOT_FOUND);
        assertCode(() -> service.one(6L), MonthEndRefusalCode.VISIT_DRAFT_NOT_FOUND);
        assertCode(() -> service.update(5L, request(900001, "2026-09-10", null)), MonthEndRefusalCode.REQUEST_MALFORMED);
    }

    // ------------------------------------------------------------------------------------------- submitting

    @Test
    @DisplayName("🔒 5 Oct: submitting records the visit through the visit's own path -- its complement written through -- and marks the draft submitted with it")
    void submitting() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("DRAFT", 2)));
        when(drafts.one(5L, "invented.organiser")).thenReturn(Optional.of(draft("DRAFT", "2026-09-10", 2)));
        when(drafts.submitted(5L, "invented.organiser", 2, 99L, "system", CLOCK.instant())).thenReturn(true);

        service.submit(5L, new VisitDraftSubmitRequest(2));

        verify(visitRows).setComplement(7L, 43);
        verify(visitRows).insert(eq(7L), any(), eq(LocalDate.of(2026, 9, 10)), eq(LocalTime.of(9, 0)),
                eq(LocalTime.of(10, 30)), eq(40), any());
        verify(drafts).submitted(5L, "invented.organiser", 2, 99L, "system", CLOCK.instant());
    }

    @Test
    @DisplayName("🔒 a draft with no day is not submitted: VISIT_INVALID at the day, no visit recorded, the draft as it was")
    void noDayNoSubmission() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("DRAFT", 0)));
        when(drafts.one(5L, "invented.organiser")).thenReturn(Optional.of(draft("DRAFT", null, 0)));

        assertInvalid(() -> service.submit(5L, new VisitDraftSubmitRequest(0)), "visitedOn");

        verify(visitRows, never()).insert(anyLong(), any(), any(), any(), any(), any(), any());
        verify(drafts, never()).submitted(anyLong(), anyString(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("🔒 a submission sent twice answers the first -- no second visit, and no refusal")
    void submittedTwice() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("SUBMITTED", 3)));
        when(drafts.one(5L, "invented.organiser")).thenReturn(Optional.of(draft("SUBMITTED", "2026-09-10", 3)));

        assertThat(service.submit(5L, new VisitDraftSubmitRequest(2)).status()).isEqualTo("SUBMITTED");

        verify(visitRows, never()).insert(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("must-pass: a submission from an older version still is VISIT_ALREADY_SUBMITTED, and one of a draft saved since VISIT_DRAFT_STALE")
    void submittedFromElsewhere() {
        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("SUBMITTED", 3)));
        assertCode(() -> service.submit(5L, new VisitDraftSubmitRequest(0)), MonthEndRefusalCode.VISIT_ALREADY_SUBMITTED);

        when(drafts.lock(5L, "invented.organiser")).thenReturn(Optional.of(head("DRAFT", 3)));
        assertCode(() -> service.submit(5L, new VisitDraftSubmitRequest(2)), MonthEndRefusalCode.VISIT_DRAFT_STALE);
        verify(visitRows, never()).insert(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("my drafts: DRAFT where no status is named, SUBMITTED on asking; another word REQUEST_MALFORMED; a page past the last WORKPLACE_PAGE_INVALID")
    void myDrafts() {
        when(drafts.mine("invented.organiser", "DRAFT", 0, 20))
                .thenReturn(new WorkplaceVisitDraftRepository.Slice(List.of(draft("DRAFT", "2026-09-10", 0)), 1));
        when(drafts.mine("invented.organiser", "SUBMITTED", 0, 20))
                .thenReturn(new WorkplaceVisitDraftRepository.Slice(List.of(), 0));

        assertThat(service.mine(null, null, null).drafts()).hasSize(1);
        assertThat(service.mine("submitted", null, null).status()).isEqualTo("SUBMITTED");
        assertCode(() -> service.mine("ALL", null, null), MonthEndRefusalCode.REQUEST_MALFORMED);
        when(drafts.mine("invented.organiser", "DRAFT", 3, 20))
                .thenReturn(new WorkplaceVisitDraftRepository.Slice(List.of(), 0));
        assertCode(() -> service.mine(null, 3, null), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
        assertCode(() -> service.mine(null, 0, 201), MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
    }

    @Test
    @DisplayName("🔒 no anonymous draft: with nobody signed in, the service refuses to name an owner")
    void noAnonymousDraft() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.create(request(900001, "2026-09-10", null)))
                .isInstanceOf(IllegalStateException.class);
        verify(drafts, never()).insert(anyLong(), any(), anyString(), anyString(), any());
    }

    private static void assertInvalid(Runnable call, String field) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.VISIT_INVALID);
            assertThat(refusal.details()).containsEntry("field", field);
        });
    }

    private static void assertCode(Runnable call, MonthEndRefusalCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(code));
    }
}
