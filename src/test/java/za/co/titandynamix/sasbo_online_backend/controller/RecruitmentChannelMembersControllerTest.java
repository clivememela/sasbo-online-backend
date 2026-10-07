package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.MemberCandidatesDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelMembersService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RC15's routes, with the application's real security configuration: the figures are open (they name no member), the
 * lists need {@code MEMBERS_VIEW}, and putting a member on a channel or taking them off needs {@code MEMBERS_EDIT}.
 */
@WebMvcTest(RecruitmentChannelMembersController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
@DisplayName("RecruitmentChannelMembersController (RC15)")
class RecruitmentChannelMembersControllerTest {

    private static final ChannelMemberDto MEMBER = new ChannelMemberDto(7, "T-0001", "Mr", "A", "Alpha", "Testperson",
            "Synthetic Bank", "Main Street", "Active");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecruitmentChannelMembersService service;

    /** An anonymous caller's refusal: a 4xx and never the catch-all's 500 (403 now, 401 once sign-in lands). */
    private static ResultMatcher refusedAsAnonymous() {
        return result -> assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("the figures are open to anyone, in the DTO's own field names")
    void theFiguresAreOpen() throws Exception {
        when(service.summary()).thenReturn(List.of(new ChannelSummaryDto(1L, "BEC", true, 3,
                List.of("Invented Bank", "Synthetic Bank"), List.of("Harbour", "Main Street"))));

        this.mockMvc.perform(get("/api/recruitment-channels/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelId").value(1))
                .andExpect(jsonPath("$[0].name").value("BEC"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].members").value(3))
                .andExpect(jsonPath("$[0].banks[1]").value("Synthetic Bank"))
                .andExpect(jsonPath("$[0].branches[0]").value("Harbour"));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a channel's list binds its page and each criterion, for a user holding MEMBERS_VIEW")
    void theListBindsItsCriteria() throws Exception {
        when(service.members(4L, "0001", "sam", "synthetic", "main", "act", 2, 25))
                .thenReturn(new ChannelMembersPageDto(List.of(MEMBER), 2, 25, 51, 3));

        this.mockMvc.perform(get("/api/recruitment-channels/4/members")
                        .param("page", "2").param("size", "25").param("membershipNumber", "0001").param("name", "sam")
                        .param("bank", "synthetic").param("branch", "main").param("status", "act"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].membershipNumber").value("T-0001"))
                .andExpect(jsonPath("$.content[0].bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("the defaults: the first page of twenty")
    void theListsDefaults() throws Exception {
        when(service.members(4L, null, null, null, null, null, 0, 20))
                .thenReturn(new ChannelMembersPageDto(List.of(), 0, 20, 0, 0));

        this.mockMvc.perform(get("/api/recruitment-channels/4/members")).andExpect(status().isOk());

        verify(service).members(4L, null, null, null, null, null, 0, 20);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a picker binds its search and the channel to leave out")
    void thePickerBindsItsSearch() throws Exception {
        when(service.candidates("sample", 4L)).thenReturn(new MemberCandidatesDto(List.of(MEMBER), true));

        this.mockMvc.perform(get("/api/recruitment-channels/member-candidates")
                        .param("q", "sample").param("excludeChannel", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].surname").value("Testperson"))
                .andExpect(jsonPath("$.more").value(true));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_EDIT")
    @DisplayName("a user holding MEMBERS_EDIT adds a member to a channel and takes them off")
    void addAndRemove() throws Exception {
        when(service.assign(4L, 7)).thenReturn(MEMBER);

        this.mockMvc.perform(put("/api/recruitment-channels/4/members/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        this.mockMvc.perform(delete("/api/recruitment-channels/4/members/7"))
                .andExpect(status().isNoContent());

        verify(service).unassign(4L, 7);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_EDIT")
    @DisplayName("without MEMBERS_VIEW the lists are refused, 403, and nothing reaches the service")
    void theListsNeedMembersView() throws Exception {
        this.mockMvc.perform(get("/api/recruitment-channels/4/members")).andExpect(status().isForbidden());
        this.mockMvc.perform(get("/api/recruitment-channels/member-candidates")).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("without MEMBERS_EDIT the writes are refused, 403, and nothing reaches the service")
    void theWritesNeedMembersEdit() throws Exception {
        this.mockMvc.perform(put("/api/recruitment-channels/4/members/7")).andExpect(status().isForbidden());
        this.mockMvc.perform(delete("/api/recruitment-channels/4/members/7")).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("an anonymous caller is refused every list and write while sign-in is open")
    void anAnonymousCallerIsRefused() throws Exception {
        this.mockMvc.perform(get("/api/recruitment-channels/4/members")).andExpect(refusedAsAnonymous());
        this.mockMvc.perform(get("/api/recruitment-channels/member-candidates")).andExpect(refusedAsAnonymous());
        this.mockMvc.perform(put("/api/recruitment-channels/4/members/7")).andExpect(refusedAsAnonymous());
        this.mockMvc.perform(delete("/api/recruitment-channels/4/members/7")).andExpect(refusedAsAnonymous());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"MEMBERS_VIEW", "MEMBERS_EDIT"})
    @DisplayName("a refusal is its code and its sentence: a channel no longer on record, a page past the limit, a "
            + "stale removal")
    void theRefusals() throws Exception {
        when(service.members(anyLong(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), anyInt(), anyInt()))
                .thenThrow(new ReferenceRefusal(ReferenceRefusalCode.REQUEST_INVALID, "A page holds 1 to 100 members.",
                        Map.of("field", "size")));
        when(service.assign(99L, 7)).thenThrow(new ReferenceRefusal(ReferenceRefusalCode.CHANNEL_NOT_FOUND,
                "That recruitment channel is no longer on record. Reload the list, then try again.",
                Map.of("channelId", "99")));
        org.mockito.Mockito.doThrow(new ReferenceRefusal(ReferenceRefusalCode.MEMBER_NOT_IN_CHANNEL,
                        "That member is recorded as recruited through Website, not BEC. Reload the list to see where "
                                + "they are now.", Map.of("channel", "Website")))
                .when(service).unassign(4L, 7);

        this.mockMvc.perform(get("/api/recruitment-channels/4/members").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.details.field").value("size"));
        this.mockMvc.perform(put("/api/recruitment-channels/99/members/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHANNEL_NOT_FOUND"));
        this.mockMvc.perform(delete("/api/recruitment-channels/4/members/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_IN_CHANNEL"))
                .andExpect(jsonPath("$.message").value("That member is recorded as recruited through Website, not "
                        + "BEC. Reload the list to see where they are now."));
    }
}
