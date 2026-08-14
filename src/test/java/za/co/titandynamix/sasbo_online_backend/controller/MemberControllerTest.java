package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberMapper memberMapper;

    @Nested
    @DisplayName("GET /api/members")
    class GetAllMembers {
        @Test
        @DisplayName("should return a list of member DTOs")
        void shouldReturnList() throws Exception {
            Member member = new Member();
            MemberDto dto = new MemberDto();
            dto.setSurname("Smith");

            when(memberService.findAll()).thenReturn(List.of(member));
            when(memberMapper.toDto(any(Member.class))).thenReturn(dto);

            mockMvc.perform(get("/api/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].surname").value("Smith")); // Fixed JsonPath for array root
        }
    }

    @Nested
    @DisplayName("GET /api/members/{id}")
    class GetMemberById {
        @Test
        @DisplayName("should return member DTO when record exists")
        void shouldReturnMemberWhenFound() throws Exception {
            Member member = new Member();
            MemberDto dto = new MemberDto();
            dto.setSurname("Doe");

            when(memberService.findById(1)).thenReturn(Optional.of(member));
            when(memberMapper.toDto(any(Member.class))).thenReturn(dto);

            mockMvc.perform(get("/api/members/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("Doe"));
        }

        @Test
        @DisplayName("should return 404 when record does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            when(memberService.findById(99)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/members/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/members")
    class CreateMember {
        @Test
        @DisplayName("should create a new member and return the saved member DTO")
        void shouldCreateMemberAndReturnDto() throws Exception {
            Member savedMember = new Member();
            MemberDto responseDto = new MemberDto();
            responseDto.setSurname("NewMember");

            when(memberService.create(any(Member.class))).thenReturn(savedMember);
            when(memberMapper.toDto(any(Member.class))).thenReturn(responseDto);

            String jsonPayload = "{\"surname\": \"NewMember\", \"firstName\": \"John\"}";

            mockMvc.perform(post("/api/members")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("NewMember"));
        }
    }

    @Nested
    @DisplayName("PUT /api/members/{id}")
    class UpdateMember {
        @Test
        @DisplayName("should update member details and return updated DTO")
        void shouldUpdateAndReturnDto() throws Exception {
            Member member = new Member();
            MemberDto dto = new MemberDto();
            dto.setSurname("UpdatedSurname");

            when(memberService.update(eq(1), any(Member.class))).thenReturn(Optional.of(member));
            when(memberMapper.toDto(any(Member.class))).thenReturn(dto);

            String json = "{\"surname\": \"UpdatedSurname\"}";

            mockMvc.perform(put("/api/members/1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("UpdatedSurname"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/{id}/banking")
    class UpdateBanking {
        @Test
        @DisplayName("should update financial metadata configurations successfully")
        void shouldUpdateBankingDetails() throws Exception {
            Member member = new Member();
            MemberDto dto = new MemberDto();
            dto.setSurname("Doe");

            when(memberService.updateBanking(eq(1), any(Member.class))).thenReturn(Optional.of(member));
            when(memberMapper.toDto(any(Member.class))).thenReturn(dto);

            String json = "{\"bankName\": \"FNB\", \"accountNumber\": \"123456\"}";

            mockMvc.perform(patch("/api/members/1/banking")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("Doe"));
        }
    }

    @Nested
    @DisplayName("GET /api/members/metadata/payment-types")
    class GetPaymentTypeMetadata {
        @Test
        @DisplayName("should return static application setup options for payment types")
        void shouldReturnMetadataList() throws Exception {
            mockMvc.perform(get("/api/members/metadata/payment-types"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].name").exists()) // Fixed JsonPath array indexing
                    .andExpect(jsonPath("$[0].displayName").exists())
                    .andExpect(jsonPath("$[0].requiresBankAccount").exists());
        }
    }
}
