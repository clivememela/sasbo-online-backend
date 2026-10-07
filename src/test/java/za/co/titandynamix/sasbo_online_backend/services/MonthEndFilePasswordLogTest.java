package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔒 A file's password is never logged (October build; Stephne MEN-05): a bank's file made and downloaded through its
 * routes -- the whole stack, the controller, Jackson and Spring's own request handling -- under a capture of the
 * application's loggers and Spring's at TRACE, the finest there is. The password the answer carried appears in none of
 * it; Spring's TRACE line that writes the answer out is there, and says the password is withheld.
 *
 * <p>@Isolated: it captures Logback loggers, and a context started beside it resets Logback (see SeedStartupTest).
 */
@Isolated
@SpringBootTest(properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-password-log;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Import(MonthEndFilePasswordLogTest.AtTheFixturesClock.class)
@DisplayName("A file's password is never logged")
class MonthEndFilePasswordLogTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SendFixture.NOW, AcbTime.ZONE);
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private ObjectMapper json;

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("🔒 made and downloaded through the routes, under a TRACE capture: the password is in the answer, and in no log line")
    void thePasswordIsNeverLogged() throws Exception {
        SendFixture.clear(jdbc);
        SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
        takes.take(SendFixture.TAKEN, "seed");

        try (LogCapture application = LogCapture.under("za.co.titandynamix.sasbo_online_backend");
             LogCapture spring = LogCapture.under("org.springframework")) {
            MvcResult made = mockMvc.perform(post("/api/month-end/2026-08/files/banks/INVENTED_BANK"))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode answer = json.readTree(made.getResponse().getContentAsString());
            String shown = answer.path("password").asText();
            mockMvc.perform(get("/api/month-end/files/" + answer.path("file").path("id").asLong()))
                    .andExpect(status().isOk());

            assertThat(shown).matches("[A-HJKMNP-Z2-9]{4}(-[A-HJKMNP-Z2-9]{4}){3}");
            assertThat(application.rendered()).as("the capture is live: the file's own INFO line")
                    .contains("Month end: file");
            assertThat(spring.rendered()).as("Spring wrote the answer out at TRACE -- and withheld the password")
                    .contains("password withheld");
            assertThat(application.rendered()).doesNotContain(shown);
            assertThat(spring.rendered()).doesNotContain(shown);
        }
    }
}
