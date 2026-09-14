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
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A search endpoint, so the assertions are about the parameters rather than the
 * body: what the controller passes down is the only thing it decides.
 */
@WebMvcTest(BankBranchController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BankBranchController")
class BankBranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BankBranchService bankBranchService;

    @Test
    @DisplayName("GET /api/branches passes all four filters through in the right order")
    void passesEveryFilterThrough() throws Exception {
        // Four String parameters in a row is exactly the shape where two get
        // swapped and nothing notices: every one of them is a String, so the
        // compiler cannot help and a stubbed response looks the same either way.
        when(this.bankBranchService.search("Absa", "Sandton", "Gauteng", "12345"))
                .thenReturn(List.of());

        this.mockMvc.perform(get("/api/branches")
                        .param("bankName", "Absa")
                        .param("branch", "Sandton")
                        .param("area", "Gauteng")
                        .param("randomAccessNumber", "12345"))
                .andExpect(status().isOk());

        verify(this.bankBranchService).search("Absa", "Sandton", "Gauteng", "12345");
    }

    @Test
    @DisplayName("an omitted filter arrives as an empty string, not as null")
    void omittedFiltersDefaultToEmpty() throws Exception {
        // The controller declares defaultValue = "". A service written to expect
        // null for "not filtering" would behave differently, so which one it
        // gets is part of the contract between them.
        when(this.bankBranchService.search("", "", "", "")).thenReturn(List.of());

        this.mockMvc.perform(get("/api/branches")).andExpect(status().isOk());

        verify(this.bankBranchService).search("", "", "", "");
    }

    @Test
    @DisplayName("serialises a branch with the bank name pulled from its parent")
    void serialisesTheDerivedBankName() throws Exception {
        // BankBranch.getBankName() reads through the bank relation, falling back
        // to a transient field. It is a hand-written getter on an entity that is
        // serialised directly, so it is part of the wire contract.
        BankBranch branch = new BankBranch();
        branch.setId(4);
        branch.setBranchName("Sandton");
        branch.setBranchCode("632005");
        when(this.bankBranchService.search("", "", "", "")).thenReturn(List.of(branch));

        this.mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].branchName").value("Sandton"))
                .andExpect(jsonPath("$[0].branchCode").value("632005"));
    }
}
