package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.repository.RecruiterRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The one piece of logic behind {@code GET /api/recruiters}: the order.
 *
 * <p>It is here rather than in the controller because a controller that decides an
 * ordering puts that decision where no service test can see it — which is exactly
 * what {@code LayeringTest} refused when this endpoint was first written.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecruiterServiceImpl")
class RecruiterServiceImplTest {

    @Mock
    private RecruiterRepository recruiterRepository;

    private RecruiterServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new RecruiterServiceImpl(this.recruiterRepository);
    }

    private static Recruiter named(String name) {
        Recruiter recruiter = new Recruiter();
        recruiter.setName(name);
        return recruiter;
    }

    @Test
    @DisplayName("🔴 sorts case-insensitively — the seed SHOUTS some names and not others")
    void sortsCaseInsensitively() {
        // recruiters.json really is mixed: "SYDNEY BIYELA" and "ADMINISTRATION" beside
        // "Alice Smith". A byte-order sort files every capitalised name ahead of every
        // other one, so the list reads as two lists and a user scanning for a name
        // gives up halfway.
        when(this.recruiterRepository.findAll())
                .thenReturn(List.of(named("alice smith"), named("SYDNEY BIYELA"), named("Bob Jones")));

        assertThat(this.service.findAll())
                .extracting(Recruiter::getName)
                .containsExactly("alice smith", "Bob Jones", "SYDNEY BIYELA");
    }

    @Test
    @DisplayName("a nameless row sorts last instead of throwing")
    void toleratesANullName() {
        // `name` is nullable in the schema. A comparator that dereferences it turns
        // one bad row into a 500 on a reference endpoint the capture screen calls on
        // mount -- which would take the whole form down, not just the drop-down.
        when(this.recruiterRepository.findAll())
                .thenReturn(List.of(named(null), named("Alice Smith")));

        assertThat(this.service.findAll())
                .extracting(Recruiter::getName)
                .containsExactly("Alice Smith", null);
    }

    @Test
    @DisplayName("an empty table is an empty list, not a null and not an error")
    void servesAnEmptyTable() {
        // The state this whole unit exists to end. It has to be reportable, because
        // the browser tells "nothing set up yet" apart from "could not load" and can
        // only do that if an empty table comes back as an ordinary empty list.
        when(this.recruiterRepository.findAll()).thenReturn(List.of());

        assertThat(this.service.findAll()).isEmpty();
    }
}
