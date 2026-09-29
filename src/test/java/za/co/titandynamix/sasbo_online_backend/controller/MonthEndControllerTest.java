package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/month-end} (month end 04.2) -- the controller over the real {@link MonthEndTakes}, the database's
 * answers stood in for: which months are taken, one month's record, and the take that refuses every caller -- after
 * saying what it knows of the month, and without ever writing.
 */
@WebMvcTest(MonthEndController.class)
@Import({MonthEndTakes.class, MonthEndControllerTest.MidSeptember.class, GlobalExceptionHandler.class,
        MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MonthEndController")
class MonthEndControllerTest {

    static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");

    @TestConfiguration
    static class MidSeptember {
        @Bean
        Clock midSeptember() {
            return Clock.fixed(NOW, AcbTime.ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndFactsRepository facts;

    @MockitoBean
    private SasboEnvironment environment;

    @MockitoBean
    private SeedMemberNumbers seedNumbers;

    @MockitoBean
    private PlatformTransactionManager transactions;

    private static final TakenMonth AUGUST = new TakenMonth(3, LocalDate.of(2026, 8, 1), "seed",
            Instant.parse("2026-09-01T06:00:00Z"), Instant.parse("2026-06-01T08:00:00Z"));

    @Test
    @DisplayName("the months taken, the newest first, beside the open month -- in SASBO's zone")
    void theMonthsTaken() throws Exception {
        when(facts.takenMonths()).thenReturn(List.of(AUGUST));

        mockMvc.perform(get("/api/month-end/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openMonth").value("2026-09"))
                .andExpect(jsonPath("$.taken[0].month").value("2026-08"))
                .andExpect(jsonPath("$.taken[0].state").value("TAKEN"))
                .andExpect(jsonPath("$.taken[0].takenAt").value("2026-09-01T06:00:00Z"))
                .andExpect(jsonPath("$.taken[0].takenBy").value("seed"));
    }

    @Test
    @DisplayName("one month: taken, with when and by whom; the open month, open; a past month not taken, MONTH_NOT_TAKEN")
    void oneMonth() throws Exception {
        when(facts.taken(LocalDate.of(2026, 8, 1))).thenReturn(Optional.of(AUGUST));
        when(facts.taken(LocalDate.of(2026, 9, 1))).thenReturn(Optional.empty());
        when(facts.taken(LocalDate.of(2026, 7, 1))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/month-end/2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("TAKEN"))
                .andExpect(jsonPath("$.takenBy").value("seed"));
        mockMvc.perform(get("/api/month-end/2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.takenAt").doesNotExist());
        mockMvc.perform(get("/api/month-end/2026-07"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_TAKEN"))
                .andExpect(jsonPath("$.details.month").value("2026-07"))
                .andExpect(jsonPath("$.details.open").value("2026-09"));
    }

    @Test
    @DisplayName("ME-11, ME-14: a month that cannot be read is REQUEST_MALFORMED naming the field, never giving it back")
    void aMonthThatCannotBeRead() throws Exception {
        mockMvc.perform(get("/api/month-end/PLANTEDMONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("month"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PLANTEDMONTH"))));
        mockMvc.perform(post("/api/month-end/2026-13/take"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
    }

    @Test
    @DisplayName("🔒 MD4: the take refuses every caller, IDENTITY_REQUIRED -- and writes nothing")
    void theTakeRefusesEveryCaller() throws Exception {
        when(facts.taken(any())).thenReturn(Optional.empty());
        when(facts.workplacesOnRecord()).thenReturn(4L);

        mockMvc.perform(post("/api/month-end/2026-08/take"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.month").value("2026-08"));
        verify(facts, never()).insertMonth(any(), any(), any());
    }

    @Test
    @DisplayName("before it refuses, the take says what it knows: not begun, taken already and by whom, nothing to freeze")
    void theTakeSaysWhatItKnows() throws Exception {
        mockMvc.perform(post("/api/month-end/2026-10/take"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_BEGUN"))
                .andExpect(jsonPath("$.details.current").value("2026-09"));

        when(facts.taken(LocalDate.of(2026, 8, 1))).thenReturn(Optional.of(AUGUST));
        mockMvc.perform(post("/api/month-end/2026-08/take"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_ALREADY_TAKEN"))
                .andExpect(jsonPath("$.details.takenBy").value("seed"))
                .andExpect(jsonPath("$.details.takenAt").value("2026-09-01T06:00:00Z"));

        when(facts.taken(LocalDate.of(2026, 7, 1))).thenReturn(Optional.empty());
        when(facts.workplacesOnRecord()).thenReturn(0L);
        mockMvc.perform(post("/api/month-end/2026-07/take"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_READY"))
                .andExpect(jsonPath("$.details.missing").value("workplaces"));
        verify(facts, never()).insertMonth(any(), any(), any());
    }
}
