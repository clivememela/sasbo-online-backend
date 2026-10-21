package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reference controller slice, built on the pattern
 * {@code DeletionCodeControllerTest} set. The other eleven copy this shape.
 *
 * <h2>Four rules, and each has a defect behind it</h2>
 * <ol>
 *   <li><b>Real DTOs, never a mocked mapper.</b> Mocking the mapper makes every
 *       {@code jsonPath} content assertion circular -- the test asserts the
 *       value it stubbed, one layer down. Mock the service; let the real
 *       serialisation run.
 *   <li><b>Every write endpoint captures its bound argument.</b>
 *       {@code when(service.create(any(X.class)))} never checks that the JSON
 *       bound to the right fields; the payload could be {@code {}} and the test
 *       would pass. That is "a field that reads but does not write" at the
 *       transport seam.
 *   <li><b>Payloads are built with {@code writeValueAsString}</b>, not typed as
 *       JSON strings, so a renamed field breaks the test at compile time
 *       instead of silently sending a field the server ignores.
 *   <li><b>{@code addFilters = false} is only allowed where the test makes no
 *       authorization claim.</b> This slice asserts transport and binding, so
 *       it is correct here. Any test claiming something about who may call an
 *       endpoint must import the real {@code SecurityConfig} instead -- see the
 *       POPIA test, where a bare slice would have asserted Spring Security's
 *       defaults and gone green while {@code permitAll()} stood in production.
 * </ol>
 */
@WebMvcTest(BankController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BankController")
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BankService bankService;

    @MockitoBean
    private BankBranchService bankBranchService;

    @Test
    @DisplayName("GET /api/banks serialises the DTO's exact field names")
    void getAllBanksReturnsDtoFieldNames() throws Exception {
        // Real BankDto, real serialisation. If someone renames a field the
        // client reads, this reddens -- which a mocked mapper would not.
        when(this.bankService.findAllDtos()).thenReturn(List.of(bankDto(1, "Absa")));

        this.mockMvc.perform(get("/api/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Absa"))
                .andExpect(jsonPath("$[0].universalBranchCode").value("632005"));
    }

    @Test
    @DisplayName("GET /api/banks answers with an empty array, not a 404, when there are none")
    void getAllBanksEmpty() throws Exception {
        // An empty collection is a successful answer. A 404 here would make the
        // client treat "no banks configured" as a broken endpoint.
        when(this.bankService.findAllDtos()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/banks/branches returns the branch list")
    void getAllBranches() throws Exception {
        BankBranch branch = new BankBranch();
        branch.setId(4);
        branch.setBranchName("Sandton");
        branch.setBranchCode("632005");
        when(this.bankBranchService.findAll()).thenReturn(List.of(branch));

        this.mockMvc.perform(get("/api/banks/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].branchName").value("Sandton"))
                .andExpect(jsonPath("$[0].branchCode").value("632005"));
    }

    @Test
    @DisplayName("POST /api/banks/branches binds every field of the payload it was sent")
    void createBranchBindsThePayload() throws Exception {
        // Rule 2. Without the captor this test passes against a controller that
        // forwards an empty BankBranchDto, because the response is stubbed.
        BankBranchDto sent = new BankBranchDto();
        sent.setBranch("Sandton");
        sent.setBranchCode("632005");
        sent.setBankName("Absa");
        sent.setArea("Gauteng");

        BankBranch created = new BankBranch();
        created.setId(4);
        when(this.bankBranchService.createFromDto(any(BankBranchDto.class))).thenReturn(created);

        this.mockMvc.perform(post("/api/banks/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(sent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));

        ArgumentCaptor<BankBranchDto> bound = ArgumentCaptor.forClass(BankBranchDto.class);
        verify(this.bankBranchService).createFromDto(bound.capture());
        assertThat(bound.getValue().getBranch()).isEqualTo("Sandton");
        assertThat(bound.getValue().getBranchCode()).isEqualTo("632005");
        assertThat(bound.getValue().getBankName()).isEqualTo("Absa");
        assertThat(bound.getValue().getArea()).isEqualTo("Gauteng");
    }

    @Test
    @DisplayName("POST /api/banks/branches accepts branchName as an alias for branch")
    void createBranchAcceptsTheBranchNameAlias() throws Exception {
        // BankBranchDto carries @JsonAlias("branchName") on `branch`. The
        // client sends one name and the server calls it another, so the alias
        // is load-bearing and nothing was asserting it.
        BankBranch created = new BankBranch();
        created.setId(5);
        when(this.bankBranchService.createFromDto(any(BankBranchDto.class))).thenReturn(created);

        this.mockMvc.perform(post("/api/banks/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Absa\",\"branchName\":\"Rosebank\",\"branchCode\":\"632005\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BankBranchDto> bound = ArgumentCaptor.forClass(BankBranchDto.class);
        verify(this.bankBranchService).createFromDto(bound.capture());
        assertThat(bound.getValue().getBranch()).isEqualTo("Rosebank");
    }

    @Test
    @DisplayName("POST /api/banks/branches flattens a structured physical address to one string")
    void createBranchFlattensAStructuredAddress() throws Exception {
        // FlexibleStringDeserializer accepts either a string or an object with
        // streetNumber/streetName. The object form is the one no test covered,
        // and it is the form the branch import actually sends.
        BankBranch created = new BankBranch();
        created.setId(6);
        when(this.bankBranchService.createFromDto(any(BankBranchDto.class))).thenReturn(created);

        this.mockMvc.perform(post("/api/banks/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Absa\",\"branch\":\"Rivonia\","
                                + "\"physicalAddress\":{\"streetNumber\":\"12\","
                                + "\"streetName\":\"Rivonia Road\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BankBranchDto> bound = ArgumentCaptor.forClass(BankBranchDto.class);
        verify(this.bankBranchService).createFromDto(bound.capture());
        assertThat(bound.getValue().getPhysicalAddress()).isEqualTo("12 Rivonia Road");
    }

    @Test
    @DisplayName("PUT /api/banks/branches/{id} passes the path id and the bound payload through")
    void updateBranchBindsIdAndPayload() throws Exception {
        BankBranchDto sent = new BankBranchDto();
        sent.setBranch("Sandton City");
        sent.setBankName("Absa");

        BankBranch updated = new BankBranch();
        updated.setId(4);
        when(this.bankBranchService.updateFromDto(eq(4), any(BankBranchDto.class)))
                .thenReturn(updated);

        this.mockMvc.perform(put("/api/banks/branches/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(sent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));

        ArgumentCaptor<BankBranchDto> bound = ArgumentCaptor.forClass(BankBranchDto.class);
        verify(this.bankBranchService).updateFromDto(eq(4), bound.capture());
        assertThat(bound.getValue().getBranch()).isEqualTo("Sandton City");
    }

    @Test
    @DisplayName("a service failure becomes the error envelope, not a bare stack trace")
    void serviceFailureProducesTheEnvelope() throws Exception {
        // Exercises GlobalExceptionHandler rather than asserting a status the
        // controller returned itself. The envelope's shape is what the client
        // parses, so it is the contract.
        when(this.bankBranchService.updateFromDto(eq(99), any(BankBranchDto.class)))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Branch 99 not found"));

        // A valid body since A29: `{}` is refused for its missing bank and branch before the service is asked.
        this.mockMvc.perform(put("/api/banks/branches/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Absa\",\"branch\":\"Sandton\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/banks/branches/99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============================================================
    // A29's server half -- every refusal of a branch save is a sentence a SASBO administrator can act on.
    // ============================================================

    @Test
    @DisplayName("A29: a save without its bank or branch is refused with each field named, and nothing is asked of the service")
    void aSaveWithoutItsRequiredFieldsIsRefusedByField() throws Exception {
        this.mockMvc.perform(put("/api/banks/branches/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"  \",\"branchCode\":\"632005\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.bankName").value("Bank Name is required."))
                .andExpect(jsonPath("$.validationErrors.branch").value("Branch is required."));

        org.mockito.Mockito.verifyNoInteractions(this.bankBranchService);
    }

    @Test
    @DisplayName("A29: a value longer than its column is refused with the field named, not failed in the database")
    void aValueLongerThanItsColumnIsRefusedByField() throws Exception {
        BankBranchDto sent = new BankBranchDto();
        sent.setBankName("Absa");
        sent.setBranch("Sandton");
        sent.setBranchCode("6320051");

        this.mockMvc.perform(post("/api/banks/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(sent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.branchCode").value("Branch Code is at most 6 characters."));

        org.mockito.Mockito.verifyNoInteractions(this.bankBranchService);
    }

    @Test
    @DisplayName("A29: a branch no longer on record is a 404 with its code and a sentence, not the catch-all's 500")
    void aBranchNoLongerOnRecordIsCoded() throws Exception {
        when(this.bankBranchService.updateFromDto(eq(99), any(BankBranchDto.class)))
                .thenThrow(new ReferenceRefusal(ReferenceRefusalCode.BRANCH_NOT_FOUND,
                        "That branch is no longer on record. Reload the branch list, then try again.",
                        java.util.Map.of("branchId", "99")));

        this.mockMvc.perform(put("/api/banks/branches/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Absa\",\"branch\":\"Sandton\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("That branch is no longer on record. Reload the branch list, then try again."))
                .andExpect(jsonPath("$.details.branchId").value("99"));
    }

    @Test
    @DisplayName("A29: a branch code another branch of the bank holds is a 409 with its code, naming the bank and code")
    void aTakenBranchCodeIsCoded() throws Exception {
        when(this.bankBranchService.updateFromDto(eq(4), any(BankBranchDto.class)))
                .thenThrow(new ReferenceRefusal(ReferenceRefusalCode.BRANCH_CODE_TAKEN,
                        "Another Absa branch already has branch code 632005. Each branch of a bank has its own code.",
                        java.util.Map.of("bankName", "Absa", "branchCode", "632005")));

        this.mockMvc.perform(put("/api/banks/branches/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Absa\",\"branch\":\"Sandton\",\"branchCode\":\"632005\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRANCH_CODE_TAKEN"))
                .andExpect(jsonPath("$.details.branchCode").value("632005"));
    }

    private static BankDto bankDto(Integer id, String name) {
        BankDto dto = new BankDto();
        dto.setId(id);
        dto.setName(name);
        dto.setUniversalBranchCode("632005");
        return dto;
    }
}
