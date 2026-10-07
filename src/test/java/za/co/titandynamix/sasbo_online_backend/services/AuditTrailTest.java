package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.entity.signin.AuditEvent;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AuditEventRepository;
import za.co.titandynamix.sasbo_online_backend.security.BearerAuthentication;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The record of who did what (V67): who is written, what never is, and how a page of it is asked for. */
@DisplayName("AuditTrail -- the record of who did what")
class AuditTrailTest {

    private static final Instant NOW = Instant.parse("2026-10-07T06:00:00Z");

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditTrail audit = new AuditTrail(events, new MovingClock(NOW), mock(PlatformTransactionManager.class));

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(event.capture());
        return event.getValue();
    }

    @Test
    @DisplayName("an action by a signed-in user names them as they are now, at the clock's instant")
    void theSignedInUserIsNamed() {
        SecurityContextHolder.getContext().setAuthentication(new BearerAuthentication(new SignedInUser(12, 34,
                "invented.useradmin", "Invented Useradmin", CallerScope.all(), Set.of(), false)));
        audit.record(AuditTrail.USER_CREATED, AuditTrail.USER, "99", "Created with position Organiser.");
        AuditEvent event = saved();
        assertThat(event.getUserId()).isEqualTo(12);
        assertThat(event.getUsername()).isEqualTo("invented.useradmin");
        assertThat(event.getDisplayName()).isEqualTo("Invented Useradmin");
        assertThat(event.getAt()).isEqualTo(NOW);
        assertThat(event.getAction()).isEqualTo("USER_CREATED");
        assertThat(event.getEntityId()).isEqualTo("99");
    }

    @Test
    @DisplayName("nobody signed in is system -- the seed, a job, the start-up")
    void nobodyIsSystem() {
        audit.record("MONTH_END_TAKEN", "MONTH_END", "2026-09", "Taken.");
        AuditEvent event = saved();
        assertThat(event.getUsername()).isEqualTo(AuditTrail.SYSTEM);
        assertThat(event.getUserId()).isNull();
    }

    @Test
    @DisplayName("🔒 a failed sign-in for a username nobody holds records no name at all")
    void anUnknownUsernameIsNotRecorded() {
        audit.recordFor(null, false, AuditTrail.SIGN_IN_FAILED, "Sign-in failed: a username nobody holds.");
        AuditEvent event = saved();
        assertThat(event.getUsername()).isNull();
        assertThat(event.getDisplayName()).isNull();
        assertThat(event.getEntityId()).isNull();
    }

    @Test
    @DisplayName("an action is capitals and underscores; a summary is cut to its 500 characters")
    void actionsAreShapedAndSummariesCut() {
        assertThatThrownBy(() -> audit.record("signed in", null, null, "x")).isInstanceOf(IllegalArgumentException.class);
        audit.record("SIGN_IN", null, null, "y".repeat(600));
        assertThat(saved().getSummary()).hasSize(500);
    }

    @Test
    @DisplayName("a page below nought is PAGE_INVALID; a size past a hundred is a hundred")
    void pagesAreBounded() {
        assertThat(catchThrowableOfType(SignInRefusal.class, () -> audit.search(null, null, null, null, null, -1, 10))
                .code()).isEqualTo(SignInRefusalCode.PAGE_INVALID);
        when(events.findAll(org.mockito.ArgumentMatchers.<Specification<AuditEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        audit.search(null, null, null, null, null, 0, 1000);
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findAll(org.mockito.ArgumentMatchers.<Specification<AuditEvent>>any(), asked.capture());
        assertThat(asked.getValue().getPageSize()).isEqualTo(100);
        assertThat(asked.getValue().getSort().toString()).isEqualTo("at: DESC,id: DESC");
    }

    @Test
    @DisplayName("AUDIT_FILTER_INVALID: a day that cannot be read, a range that ends before it begins, a filter past its column")
    void filtersAreRead() {
        SignInRefusal unreadable = catchThrowableOfType(SignInRefusal.class,
                () -> audit.search(null, null, null, "yesterday", null, 0, 10));
        assertThat(unreadable.code()).isEqualTo(SignInRefusalCode.AUDIT_FILTER_INVALID);
        assertThat(unreadable.details()).containsEntry("field", "from");
        assertThat(catchThrowableOfType(SignInRefusal.class,
                () -> audit.search(null, null, null, "2026-10-07", "2026-10-01", 0, 10)).details())
                .containsEntry("field", "to");
        assertThat(catchThrowableOfType(SignInRefusal.class,
                () -> audit.search(null, null, "u".repeat(61), null, null, 0, 10)).details())
                .containsEntry("field", "username");
    }
}
