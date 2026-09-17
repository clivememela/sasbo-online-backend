package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code ACBController} declares {@code @RestController} and
 * {@code @RequestMapping("/api/acb")} and then has <b>no methods at all</b>.
 *
 * <h2>Why this file exists rather than being skipped</h2>
 * The instinct is that an empty class needs no test, and that instinct is what
 * leaves it exactly as it is for another year. A registered controller with no
 * endpoints is either a placeholder somebody meant to come back to or a feature
 * lost in a merge, and both are worth someone noticing.
 *
 * <p>So this asserts what is true today: nothing under {@code /api/acb}
 * succeeds. The day an endpoint is added, this reddens and whoever added it
 * writes the real tests here. A missing test file would have been a silent gap;
 * this one answers back.
 *
 * <h2>What it found on its first run, and what became of it</h2>
 * The obvious assertion -- that an unhandled path is a <b>404</b> -- failed,
 * because it answered <b>500</b>: Spring raises {@code NoResourceFoundException}
 * for a path no controller handles, and the catch-all handler is registered for
 * {@code Exception}, so it claimed it. A defect in the exception handler rather
 * than anything to do with ACB, and it is why every mistyped URL in this
 * application looked like a server fault -- register row <b>A18</b>.
 *
 * <p>✅ <b>Fixed by the ACB arc's {@code 00.4}</b> (2026-09-17):
 * {@code GlobalExceptionHandler} now declares a handler for
 * {@code NoResourceFoundException}, so the second test below runs rather than
 * being {@code @Disabled}. It was watched failing on {@code 829f252} first --
 * <i>expected:&lt;404&gt; but was:&lt;500&gt;</i> -- and the known-defect
 * constant it was pinned to is deleted.
 */
@WebMvcTest(ACBController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ACBController")
class ACBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("exposes no endpoint at all, which is what it looks like and is worth saying")
    void exposesNothing() throws Exception {
        // Asserted as "nothing here succeeds" rather than as a specific status,
        // because the status today is the defect below. This claim holds under
        // either status, and reddens the moment a real endpoint appears.
        assertThat(this.mockMvc.perform(get("/api/acb")).andReturn().getResponse().getStatus())
                .as("GET /api/acb answers an error, because there is no handler")
                .isGreaterThanOrEqualTo(400);
        assertThat(this.mockMvc.perform(post("/api/acb")).andReturn().getResponse().getStatus())
                .as("POST /api/acb answers an error, because there is no handler")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("a path no controller handles is a 404, not a 500")
    void anUnhandledPathIsNotFound() throws Exception {
        // Nothing to do with ACB in particular -- it is simply where this
        // surfaced, because ACB is the only controller with no endpoints to
        // hit. Every mistyped URL in the application answers 500 today, which
        // means a log or an alert cannot tell a wrong address from a real
        // server fault.
        this.mockMvc.perform(get("/api/acb")).andExpect(status().isNotFound());
    }
}
