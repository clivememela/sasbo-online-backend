package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.RecruiterService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/recruiters} — the endpoint 03.10 added.
 *
 * <h2>Why it exists at all</h2>
 * {@code recruiters} has been seeded with <b>33 real union names</b> since before
 * this arc, referenced by no foreign key and <b>served by no controller</b>. The
 * capture form's Recruiter drop-down called {@code /api/representatives} instead,
 * which upstream had pointed at members flagged {@code is_representative} — a flag
 * absent from the member seed entirely — so it answered {@code []} and the field was
 * empty for every user.
 */
@WebMvcTest(RecruiterController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RecruiterController")
class RecruiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecruiterService recruiterService;

    private static Recruiter recruiter(Integer id, String name) {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(id);
        recruiter.setName(name);
        recruiter.setMembersRecruited(12);
        return recruiter;
    }

    @Test
    @DisplayName("serves the recruiters the drop-down needs, with the name the form submits back")
    void servesTheRecruiters() throws Exception {
        when(this.recruiterService.findAll())
                .thenReturn(List.of(recruiter(1, "Phindile Moloti"), recruiter(2, "SYDNEY BIYELA")));

        this.mockMvc.perform(get("/api/recruiters"))
                .andExpect(status().isOk())
                // `name` is the contract: it is what the browser renders in the list AND
                // what it sends back, and `resolveRecruiter` looks that string up. A
                // response shaped differently would render an empty drop-down without
                // failing anything.
                .andExpect(jsonPath("$[0].name").value("Phindile Moloti"))
                .andExpect(jsonPath("$[1].name").value("SYDNEY BIYELA"));
    }

    @Test
    @DisplayName("passes the service's order through rather than imposing its own")
    void doesNotReorder() {
        // The ordering rule lives in RecruiterServiceImpl, with its reason. Asserted
        // here as an ABSENCE so that a second sort added in the controller -- the
        // obvious "helpful" change -- shows up as two places deciding one thing.
        when(this.recruiterService.findAll())
                .thenReturn(List.of(recruiter(1, "Zulu"), recruiter(2, "Adams")));

        org.assertj.core.api.Assertions.assertThatCode(() ->
                this.mockMvc.perform(get("/api/recruiters"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].name").value("Zulu"))
                        .andExpect(jsonPath("$[1].name").value("Adams")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("🔒 read-only — a recruiter cannot be created from here")
    void isReadOnly() throws Exception {
        // Deliberate, and worth an assertion rather than a comment: letting the
        // capture screen create a recruiter would put a typo in everybody's
        // drop-down permanently, which is the failure 02.5's `verified` flag exists
        // to prevent for job grades.
        int status = this.mockMvc.perform(post("/api/recruiters"))
                .andReturn().getResponse().getStatus();

        // Asserted as "not a success" rather than as one exact code: whether Spring
        // answers 405 or the handler turns it into something else is a framework
        // detail, and pinning it would make this test about Spring. What must hold
        // is that nothing was created.
        org.assertj.core.api.Assertions.assertThat(status)
                .as("a POST to /api/recruiters must be refused, not accepted")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("an empty list is served as an empty list, not as an error")
    void servesAnEmptyList() throws Exception {
        // The state this endpoint was BUILT to end, so it had better be reported
        // honestly if it ever returns: the browser distinguishes "no recruiters have
        // been set up yet" from "could not be loaded", and it can only do that if an
        // empty table is a 200.
        when(this.recruiterService.findAll()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/recruiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
