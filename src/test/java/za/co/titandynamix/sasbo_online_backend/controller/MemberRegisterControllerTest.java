package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/members/register/search} and {@code GET /api/members/register/overview} (register row A10): what a
 * request carries reaches the service as it was sent, the answer is the page's own shape and nothing more, and a request
 * that cannot be read is {@code REQUEST_MALFORMED} -- never a 500. Permissions and the caller's scope are
 * {@code MemberRegisterControllerSecurityTest}'s, under the real security configuration.
 */
@WebMvcTest(MemberRegisterController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MemberRegisterController")
class MemberRegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRegisterService register;

    private static MemberRegisterPageDto aPage() {
        return new MemberRegisterPageDto(List.of(new MemberRegisterPageDto.Row(7, "M-900007", "E-7", "Mx", "I",
                "Invented", "Synthetic Person", "INVENTED BANK", "INVENTED BRANCH", "Active")), 2, 25, 51, 3,
                "bank,desc");
    }

    @Test
    @DisplayName("the question reaches the service as sent, and the answer is the page: rows, page, size, totals, order")
    void theQuestionReachesTheService() throws Exception {
        when(register.page(any(), any())).thenReturn(aPage());

        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\": \"inv\", \"status\": \"Active\", \"bank\": \"INVENTED BANK\", \"page\": 2,"
                                + " \"size\": 25, \"sort\": \"bank,desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].membershipNumber").value("M-900007"))
                .andExpect(jsonPath("$.content[0].firstNames").value("Synthetic Person"))
                .andExpect(jsonPath("$.content[0].status").value("Active"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.sort").value("bank,desc"));

        ArgumentCaptor<MemberRegisterQuery> asked = ArgumentCaptor.forClass(MemberRegisterQuery.class);
        verify(register).page(asked.capture(), eq(CallerScope.all()));
        assertThat(asked.getValue().q()).isEqualTo("inv");
        assertThat(asked.getValue().status()).isEqualTo("Active");
        assertThat(asked.getValue().bank()).isEqualTo("INVENTED BANK");
        assertThat(asked.getValue().page()).isEqualTo(2);
        assertThat(asked.getValue().size()).isEqualTo(25);
        assertThat(asked.getValue().sort()).isEqualTo("bank,desc");
    }

    @Test
    @DisplayName("🔒 a row on the wire has the register's columns and the id -- no identity, account or contact field")
    void aRowOnTheWireIsSlim() throws Exception {
        when(register.page(any(), any())).thenReturn(aPage());

        String body = mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("identityNumber", "passportNumber", "bankAccountNumber", "phone", "Email",
                "monthlySubscription", "address");
    }

    @Test
    @DisplayName("no body at all is the register's first page: the service is asked with nothing")
    void noBodyIsTheFirstPage() throws Exception {
        when(register.page(any(), any())).thenReturn(aPage());

        mockMvc.perform(post("/api/members/register/search"))
                .andExpect(status().isOk());

        verify(register).page(isNull(), eq(CallerScope.all()));
    }

    @Test
    @DisplayName("🔒 ME-11: a body that is not JSON is REQUEST_MALFORMED, not a 500 -- and nothing is read")
    void aBodyThatIsNotJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
        verifyNoInteractions(register);
    }

    @Test
    @DisplayName("🔒 ME-11: a word where a page number is wanted is REQUEST_MALFORMED, naming the field")
    void aWordForAPageIsMalformed() throws Exception {
        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\": \"first\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"));
        verifyNoInteractions(register);
    }

    @Test
    @DisplayName("a refusal of the service's is its code and its status, with its details")
    void aRefusalIsItsCode() throws Exception {
        when(register.page(any(), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.REGISTER_SORT_UNKNOWN,
                "The register cannot be put in that order.", Map.of("sorts", "surname,membershipNumber")));

        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sort\": \"identityNumber\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTER_SORT_UNKNOWN"))
                .andExpect(jsonPath("$.details.sorts").value("surname,membershipNumber"));
    }

    @Test
    @DisplayName("the overview is the strip's figures, the branches and the day they are of")
    void theOverview() throws Exception {
        when(register.overview(CallerScope.all())).thenReturn(new MemberRegisterOverviewDto(310, 280, 6, 9, 12, 7, 3,
                List.of("INVENTED BRANCH A", "INVENTED BRANCH B"), "2026-10-06"));

        mockMvc.perform(get("/api/members/register/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(310))
                .andExpect(jsonPath("$.active").value(280))
                .andExpect(jsonPath("$.pending").value(6))
                .andExpect(jsonPath("$.suspended").value(9))
                .andExpect(jsonPath("$.newThisMonth").value(12))
                .andExpect(jsonPath("$.newDebitOrders").value(7))
                .andExpect(jsonPath("$.updatedToday").value(3))
                .andExpect(jsonPath("$.branches[1]").value("INVENTED BRANCH B"))
                .andExpect(jsonPath("$.asOf").value("2026-10-06"));
    }
}
