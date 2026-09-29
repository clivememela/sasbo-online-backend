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
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceListAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.ReferenceListService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SASBO's lists, one typed route a list (month end 01.1, MD41): each route answers its own shape with the day the
 * list's report was printed and the day SASBO confirmed it; no name of a list arrives in an address.
 */
@WebMvcTest(ReferenceListController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReferenceListController")
class ReferenceListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceListService lists;

    private static <T> ReferenceListAnswer<T> answer(String list, List<T> rows) {
        return new ReferenceListAnswer<>(list, "2026-08-31", null, rows.isEmpty() ? "NOT_LOADED" : null, rows);
    }

    @Test
    @DisplayName("each list at a route of its own, in a shape of its own, with printedOn and confirmedOn")
    void eachListAtItsOwnRoute() throws Exception {
        when(lists.regions()).thenReturn(answer("regions", List.of(new ReferenceRows.Region("NORTH", "NORTH"))));
        when(lists.areas()).thenReturn(answer("areas", List.of(new ReferenceRows.Area("A", "NORTH"))));
        when(lists.employerGroups()).thenReturn(answer("employerGroups",
                List.of(new ReferenceRows.EmployerGroup("INVENTED GROUP", true))));
        when(lists.employers()).thenReturn(answer("employers",
                List.of(new ReferenceRows.Employer(7, "INVENTED BANK", "INVENTED GROUP", true, List.of(901, 902)))));
        when(lists.tableCodes()).thenReturn(answer("tableCodes",
                List.of(new ReferenceRows.TableCode(902, "INVENTED BANK DEPARTMENTS", 7L, "INVENTED BANK", "DEPARTMENTS"))));
        when(lists.committeeProvinces()).thenReturn(answer("committeeProvinces",
                List.of(new ReferenceRows.CommitteeProvince("Q", null))));
        when(lists.committees()).thenReturn(answer("committees",
                List.of(new ReferenceRows.Committee("Q01", "Q", "INVENTED COMMITTEE", 99))));
        when(lists.suspensionCodes()).thenReturn(answer("suspensionCodes",
                List.of(new ReferenceRows.SuspensionCode(11, "NOT ON PAYROLL"))));
        when(lists.deletionCodes()).thenReturn(answer("deletionCodes",
                List.of(new ReferenceRows.DeletionCode(2, "Resigned Society", null),
                        new ReferenceRows.DeletionCode(null, "Financial", 2))));

        mockMvc.perform(get("/api/reference/regions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.list").value("regions"))
                .andExpect(jsonPath("$.printedOn").value("2026-08-31"))
                .andExpect(jsonPath("$.confirmedOn").doesNotExist())
                .andExpect(jsonPath("$.rows[0].code").value("NORTH"));
        mockMvc.perform(get("/api/reference/areas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].letter").value("A"))
                .andExpect(jsonPath("$.rows[0].region").value("NORTH"));
        mockMvc.perform(get("/api/reference/employer-groups")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].core").value(true));
        mockMvc.perform(get("/api/reference/employers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(7))
                .andExpect(jsonPath("$.rows[0].tableCodes[1]").value(902));
        mockMvc.perform(get("/api/reference/table-codes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].part").value("DEPARTMENTS"))
                .andExpect(jsonPath("$.rows[0].employerId").value(7));
        mockMvc.perform(get("/api/reference/committee-provinces")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].letter").value("Q"));
        mockMvc.perform(get("/api/reference/committees")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].recruitingNumber").value(99));
        mockMvc.perform(get("/api/reference/suspension-codes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].code").value(11));
        mockMvc.perform(get("/api/reference/deletion-codes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].code").value(2))
                .andExpect(jsonPath("$.rows[1].code").doesNotExist())
                .andExpect(jsonPath("$.rows[1].under").value(2));
    }

    @Test
    @DisplayName("ME-19: an empty list says why")
    void anEmptyListSaysWhy() throws Exception {
        when(lists.committees()).thenReturn(answer("committees", List.of()));

        mockMvc.perform(get("/api/reference/committees")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty())
                .andExpect(jsonPath("$.reason").value("NOT_LOADED"));
    }

    @Test
    @DisplayName("the status read: every list, the workplaces, and what the switch serves")
    void theStatusRead() throws Exception {
        when(lists.status()).thenReturn(new ReferenceStatusDto(
                List.of(new ReferenceStatusDto.ListStatus("regions", 7, "2026-08-31", null, "2026-09-29T08:00:00Z",
                        null, null)),
                new ReferenceStatusDto.ListStatus("workplaces", 0, null, null, null, "NOT_LOADED", null),
                "NONE"));

        mockMvc.perform(get("/api/reference/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lists[0].rows").value(7))
                .andExpect(jsonPath("$.workplaces.reason").value("NOT_LOADED"))
                .andExpect(jsonPath("$.workplacesServed").value("NONE"));
    }

    @Test
    @DisplayName("MR-SEC-11: no route takes a list's name from the address -- an unknown list is the ordinary 404")
    void noListIsNamedInTheAddress() throws Exception {
        mockMvc.perform(get("/api/reference/members")).andExpect(status().isNotFound());
    }
}
