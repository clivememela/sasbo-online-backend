package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
 * {@code Exception}, so it claimed it -- register row <b>A18</b>.
 *
 * <p>✅ <b>Fixed by the ACB arc's {@code 00.4}</b> (2026-09-17):
 * {@code GlobalExceptionHandler} answers both not-found exceptions with a 404, so
 * the second test below runs rather than being {@code @Disabled}. It was watched
 * failing on {@code 829f252} first -- <i>expected:&lt;404&gt; but was:&lt;500&gt;</i>
 * -- and the known-defect constant it was pinned to is deleted.
 *
 * <h2>🔴 And what the fix revealed: the 500 was this slice's, not production's</h2>
 * {@code src/main/resources/application.yaml} sets
 * {@code spring.mvc.throw-exception-if-no-handler-found: true} and
 * {@code spring.web.resources.add-mappings: false}, so the deployed application
 * raises {@code NoHandlerFoundException} -- which has answered 404 since long
 * before this test existed. Measured against the deployed API on 2026-09-18:
 * {@code GET /api/acb} answered <b>404</b>, not 500.
 *
 * <p>This slice saw a 500 because {@code src/test/resources/application.yaml}
 * <b>shadows</b> the production file and carries neither key, so resources are
 * mapped here and are not there. That is register row <b>C12</b>'s mechanism, and
 * A18 is the second defect it has produced. {@code @TestPropertySource} below
 * therefore pins the two keys the 404 behaviour actually depends on: this test now
 * asks the question production answers, and the handler covers the other
 * configuration as well.
 */
@WebMvcTest(ACBController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
// Copied from src/main/resources/application.yaml, because the test yaml shadows it and drops both.
// Without these the slice maps static resources and production does not, so "what happens to an
// unknown URL" gets a different answer here than on the deployed API -- which is exactly how A18
// came to be believed. Pinned here rather than added to the test yaml: this is the one test whose
// subject is the not-found path, and changing the shared file would silently re-route every other
// slice's 404s at the same time.
@TestPropertySource(properties = {
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
@DisplayName("ACBController")
class ACBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("exposes no endpoint at all, which is what it looks like and is worth saying")
    void exposesNothing() throws Exception {
        // Asserted as "nothing here succeeds" rather than as a specific status,
        // so that it says only what it is about -- the absence of endpoints --
        // and the status is the next test's subject. It reddens the moment a
        // real endpoint appears, under whatever status that endpoint answers.
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
        // hit. Under the properties above this asks production's question: a
        // mistyped URL must be distinguishable from a real server fault by
        // anyone reading a log or an alert.
        MvcResult result = this.mockMvc.perform(get("/api/acb"))
                .andExpect(status().isNotFound())
                .andReturn();

        // 🔑 The status alone cannot tell the two configurations apart, because
        // the handler answers both with the same envelope on purpose. So the
        // exception is named: without this, dropping @TestPropertySource would
        // leave this test passing while silently testing the OTHER arm, which
        // is the nested class's job. Each test proves it is where it claims.
        assertThat(result.getResolvedException())
                .as("production's configuration raises NoHandlerFoundException")
                .isInstanceOf(NoHandlerFoundException.class);
    }

    /**
     * The same URL under Boot's <b>default</b> {@code spring.web.resources.add-mappings},
     * which is the configuration {@code NoResourceFoundException} exists for.
     *
     * <p>It is here because the handler covers two exception types and production
     * raises only one of them. Without this class the second type is dead as far as
     * any test can tell, and deleting it from the handler would leave the suite
     * green -- the definition of a guard that is not guarding. Deleting
     * {@code NoResourceFoundException.class} from the handler was watched turning
     * this class red, and this class only.
     *
     * <p>The nested property merges over the enclosing one rather than replacing it,
     * so {@code throw-exception-if-no-handler-found} stays true here too: this is
     * production's configuration with the one line somebody might remove, removed.
     */
    @Nested
    @TestPropertySource(properties = "spring.web.resources.add-mappings=true")
    @DisplayName("with the static resource handler mapped, as Boot defaults it")
    class WithResourceMappingsOn {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("is still a 404, because the other not-found exception is handled too")
        void stillNotFound() throws Exception {
            MvcResult result = this.mockMvc.perform(get("/api/acb"))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(result.getResolvedException())
                    .as("with resources mapped it is the resource handler that refuses the path")
                    .isInstanceOf(NoResourceFoundException.class);
        }
    }
}
