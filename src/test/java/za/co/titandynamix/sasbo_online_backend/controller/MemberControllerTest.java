package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapperImpl;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;
import za.co.titandynamix.sasbo_online_backend.services.impl.WelcomeLetterDispatcher;
import za.co.titandynamix.sasbo_online_backend.support.NoTransactions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rewritten. The previous version mocked {@code MemberMapper}, so every
 * {@code jsonPath} content assertion returned the value the test had stubbed --
 * a circular assertion, one layer down. And no write endpoint checked what the
 * JSON had bound to: {@code when(service.create(any(Member.class)))} is
 * satisfied by a payload of {@code {}}, which is "a field that reads but does
 * not write" at the transport seam, on the application's capture path.
 *
 * <p>Two changes, both from the reference pattern:
 * <ul>
 *   <li>The <b>real</b> {@code MemberMapperImpl} is used, so a content
 *       assertion is about the response the browser actually receives.
 *   <li>Every write endpoint <b>captures its bound argument</b> and asserts the
 *       fields, so a payload that did not bind fails here rather than in
 *       production.
 * </ul>
 */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, MemberMapperImpl.class,
        WelcomeLetterDispatcher.class, NoTransactions.class})
@AutoConfigureMockMvc(addFilters = false)
// The @Nested blocks below name the outer fields bare -- mockMvc, not
// this.mockMvc. Inside a nested class `this` is the INNER instance, so the
// qualified form does not compile and MemberControllerTest.this.mockMvc is
// noise on every line. Nothing shadows them.
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RepresentativeService representativeService;

    /** Month end 01.3: does nothing here, so every answer is the mapper's, as it was before the workplace. */
    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.MemberWorkplaces memberWorkplaces;

    // MemberMapperImpl is imported for real, so its three @Autowired
    // repositories have to exist. They are only used on the toEntity path,
    // which this slice does not exercise -- but the context will not start
    // without them.
    @MockitoBean
    private RepresentativeRepository representativeRepository;

    @MockitoBean
    private UnionStructureRepository unionStructureRepository;

    @MockitoBean
    private RepresentativeRoleRepository representativeRoleRepository;

    /** Present here, so the endpoint is configured; its absence is MemberControllerWithoutMailTest. */
    @MockitoBean
    private EmailService emailService;

    private static Member member(Integer id, String surname) {
        Member member = new Member();
        member.setId(id);
        member.setSurname(surname);
        member.setFirstNames("Thandi");
        member.setIdentityNumber("9404275800086");
        member.setPaymentType("Payroll");
        return member;
    }

    @Nested
    @DisplayName("GET /api/members")
    class GetAllMembers {

        @Test
        @DisplayName("serialises real DTOs, so the field names are the ones the client reads")
        void serialisesRealDtos() throws Exception {
            when(memberService.findAll())
                    .thenReturn(List.of(member(1, "Khumalo")));

            mockMvc.perform(get("/api/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].surname").value("Khumalo"))
                    .andExpect(jsonPath("$[0].firstNames").value("Thandi"));
        }

        @Test
        @DisplayName("answers with an empty array, not a 404, when there are no members")
        void emptyIsASuccessfulAnswer() throws Exception {
            when(memberService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/members/{id}")
    class GetMemberById {

        @Test
        @DisplayName("returns the member when the record exists")
        void returnsTheMember() throws Exception {
            when(memberService.findById(1))
                    .thenReturn(Optional.of(member(1, "Naidoo")));

            mockMvc.perform(get("/api/members/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("Naidoo"));
        }

        @Test
        @DisplayName("returns 404 when the record does not exist")
        void returns404WhenMissing() throws Exception {
            when(memberService.findById(99)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/members/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/members")
    class CreateMember {

        @Test
        @DisplayName("binds every field of the payload it was sent")
        void bindsThePayload() throws Exception {
            // The assertion the previous version was missing entirely. Without
            // the captor, this endpoint could discard the whole body and the
            // test would pass on the stubbed response.
            when(memberService.create(any(Member.class)))
                    .thenReturn(member(3, "Botha"));

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\",\"firstNames\":\"Thandi\","
                                    + "\"identityNumber\":\"9404275800086\","
                                    + "\"paymentType\":\"Payroll\"}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Member> bound = ArgumentCaptor.forClass(Member.class);
            verify(memberService).create(bound.capture());
            assertThat(bound.getValue().getSurname()).isEqualTo("Botha");
            assertThat(bound.getValue().getFirstNames()).isEqualTo("Thandi");
            assertThat(bound.getValue().getIdentityNumber()).isEqualTo("9404275800086");
            assertThat(bound.getValue().getPaymentType()).isEqualTo("Payroll");
        }

        @Test
        @DisplayName("answers 200 with no Location header -- today's behaviour, not the intended one")
        void answers200Today() throws Exception {
            // THE ONE DELIBERATE ASSERTION OF CURRENT BEHAVIOUR IN THIS SUITE.
            //
            // A created resource should answer 201 with a Location header, and
            // this one answers 200 with neither. Correcting that is a production
            // change and a breaking one for the browser, which reads the status.
            //
            // It is asserted AS IT IS rather than left untested, because
            // untested it is invisible: the day someone does fix it, this test
            // goes red and points straight at this comment. A test written to
            // 201 instead would be red from day one for something nobody is
            // going to change in a testing pass -- and a permanently red box
            // teaches everyone to ignore red boxes.
            //
            // Intended shape: 201 Created, Location: /api/members/{id}.
            when(memberService.create(any(Member.class)))
                    .thenReturn(member(3, "Botha"));

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"));
        }

        @Test
        @DisplayName("rejects an elected role with no election date, as a 400")
        void rejectsAnElectedRoleWithNoDate() throws Exception {
            // A real business rule in the controller, and one nothing covered.
            // It throws IllegalArgumentException, which the handler turns into
            // a 400 -- so this also proves the two are wired together.
            RepresentativeRole elected = new RepresentativeRole();
            elected.setIsElectedRole(true);

            stubResolveToRole(elected);

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\",\"isRepresentative\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verify(memberService,
                    never()).create(any(Member.class));
        }

        @Test
        @DisplayName("rejects a structure-requiring role with no structure, as a 400")
        void rejectsAStructureRequiringRoleWithNoStructure() throws Exception {
            RepresentativeRole needsStructure = new RepresentativeRole();
            needsStructure.setRequiresStructure(true);

            stubResolveToRole(needsStructure);

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\",\"isRepresentative\":true}"))
                    .andExpect(status().isBadRequest());

            verify(memberService,
                    never()).create(any(Member.class));
        }

        @Test
        @DisplayName("accepts a structure-requiring role once a structure is resolved")
        void acceptsWhenTheStructureIsPresent() throws Exception {
            // The positive half. Without it, the two rejections above would
            // still pass against a controller that rejected every
            // representative unconditionally.
            RepresentativeRole needsStructure = new RepresentativeRole();
            needsStructure.setRequiresStructure(true);

            doAnswer(invocation -> {
                Member incoming = invocation.getArgument(0);
                incoming.setRole(needsStructure);
                incoming.setStructure(new UnionStructure());
                return null;
            }).when(memberService)
                    .resolveStructureAndRole(any(Member.class));

            when(memberService.create(any(Member.class)))
                    .thenReturn(member(3, "Botha"));

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\",\"isRepresentative\":true}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("when sendWelcome is requested, the member is saved FIRST, then sent the letter, and the answer says SENT")
        void sendsWelcomeLetterWhenRequested() throws Exception {
            Member created = withAddress(member(3, "Botha"));
            when(memberService.create(any(Member.class))).thenReturn(created);

            mockMvc.perform(post("/api/members?sendWelcome=true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.welcomeLetter").value("SENT"));

            // The order is the fix (A64): a letter only ever goes to a member who is already saved.
            InOrder sequence = inOrder(memberService, emailService);
            sequence.verify(memberService).create(any(Member.class));
            sequence.verify(emailService).sendWelcomeLetter(created);
        }

        @Test
        @DisplayName("🔴 A64: when the letter fails, the capture still answers 200 with the saved member, and says FAILED")
        void aFailedSendStillAnswersWithTheSavedMember() throws Exception {
            // This answered with the send's error, and the capture's transaction had rolled the
            // member back underneath it. The member is saved; only the letter failed.
            Member created = withAddress(member(3, "Botha"));
            when(memberService.create(any(Member.class))).thenReturn(created);
            doThrow(new RuntimeException("Failed to send welcome letter to thandi.botha@example.invalid: 535"))
                    .when(emailService).sendWelcomeLetter(created);

            mockMvc.perform(post("/api/members?sendWelcome=true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.welcomeLetter").value("FAILED"))
                    // B8: the failure's text names the recipient; the answer carries none of it.
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .doesNotContain("Failed to send"));
        }

        @Test
        @DisplayName("🔴 A64: a member with no email address is saved, no letter is attempted, and the answer says NO_ADDRESS")
        void aMemberWithNoAddressIsSavedWithoutAnAttempt() throws Exception {
            when(memberService.create(any(Member.class))).thenReturn(member(3, "Botha"));

            mockMvc.perform(post("/api/members?sendWelcome=true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.welcomeLetter").value("NO_ADDRESS"));

            verify(emailService, never()).sendWelcomeLetter(any());
        }

        @Test
        @DisplayName("a capture that did not ask for the letter sends none, and its answer carries no welcomeLetter key")
        void noLetterWasAskedFor() throws Exception {
            // The must-pass half of the three above: the key is there because a letter was asked for.
            when(memberService.create(any(Member.class))).thenReturn(withAddress(member(3, "Botha")));

            mockMvc.perform(post("/api/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Botha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.welcomeLetter").doesNotExist());

            verify(emailService, never()).sendWelcomeLetter(any());
        }
    }

    /** A member the letter can reach -- the {@link #member} helper carries no address. */
    private static Member withAddress(Member member) {
        member.setPersonalEmail("thandi.botha@example.invalid");
        return member;
    }

    @Nested
    @DisplayName("GET /api/members/metadata/welcome-letter")
    class WelcomeLetterMetadata {

        @Test
        @DisplayName("says the letter is emailed, where mail is configured")
        void saysEmailIsOn() throws Exception {
            mockMvc.perform(get("/api/members/metadata/welcome-letter"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailEnabled").value(true));
        }
    }

    @Nested
    @DisplayName("PUT /api/members/{id}")
    class UpdateMember {

        @Test
        @DisplayName("passes the path id and the bound payload through")
        void bindsIdAndPayload() throws Exception {
            when(memberService.update(eq(1), any(Member.class)))
                    .thenReturn(Optional.of(member(1, "Abrahams")));

            mockMvc.perform(put("/api/members/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Abrahams\",\"firstNames\":\"Thandi\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.surname").value("Abrahams"));

            ArgumentCaptor<Member> bound = ArgumentCaptor.forClass(Member.class);
            verify(memberService).update(eq(1), bound.capture());
            assertThat(bound.getValue().getSurname()).isEqualTo("Abrahams");
            assertThat(bound.getValue().getFirstNames()).isEqualTo("Thandi");
        }

        @Test
        @DisplayName("answers 404 when the member does not exist")
        void notFound() throws Exception {
            when(memberService.update(eq(99), any(Member.class)))
                    .thenReturn(Optional.empty());

            mockMvc.perform(put("/api/members/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"surname\":\"Nobody\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/{id}/banking")
    class UpdateBanking {

        @Test
        @DisplayName("binds the banking fields it was sent")
        void bindsTheBankingFields() throws Exception {
            when(memberService.updateBanking(eq(1), any(Member.class)))
                    .thenReturn(Optional.of(member(1, "Zulu")));

            mockMvc.perform(patch("/api/members/1/banking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bankName\":\"Absa\",\"bankAccountNumber\":\"62001234567\","
                                    + "\"debitDay\":25}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Member> bound = ArgumentCaptor.forClass(Member.class);
            verify(memberService).updateBanking(eq(1), bound.capture());
            assertThat(bound.getValue().getBankAccountNumber()).isEqualTo("62001234567");
            assertThat(bound.getValue().getDebitDay()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("GET /api/members/metadata/payment-types")
    class GetPaymentTypeMetadata {

        @Test
        @DisplayName("describes every payment type the enum defines")
        void describesEveryPaymentType() throws Exception {
            // Discovered from the enum rather than counted by hand, so a new
            // payment type is covered the day it is added.
            int defined = PaymentType.values().length;

            mockMvc.perform(
                            get("/api/members/metadata/payment-types"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(defined)))
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].displayName").exists())
                    .andExpect(jsonPath("$[0].requiresBankAccount").exists());
        }
    }

    /** Makes {@code resolveStructureAndRole} attach the given role, as the real service would. */
    private void stubResolveToRole(RepresentativeRole role) {
        doAnswer(invocation -> {
            Member incoming = invocation.getArgument(0);
            incoming.setRole(role);
            return null;
        }).when(memberService).resolveStructureAndRole(any(Member.class));
    }

    @Nested
    @DisplayName("POST /api/members/{id}/send-welcome")
    class SendWelcome {

        @Test
        @DisplayName("sends the letter for THAT member and answers 200 with no body")
        void sendsForThatMember() throws Exception {
            Member member = member(7, "Mokoena");
            when(memberService.findById(7)).thenReturn(Optional.of(member));

            mockMvc.perform(post("/api/members/7/send-welcome"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .as("an EMPTY 200 -- the client must not parse it as JSON (register A50)")
                            .isEmpty());

            verify(emailService).sendWelcomeLetter(member);
        }

        @Test
        @DisplayName("answers 400 naming the reason when the member has no email address")
        void noAddressIs400() throws Exception {
            // Not a defect, and pinned so it stays that way: the client's dialog offers Send
            // for a member with no address (register A56), and this is what it gets back.
            Member member = member(8, "Dlamini");
            when(memberService.findById(8)).thenReturn(Optional.of(member));
            doThrow(new IllegalArgumentException("No email address is available for this member."))
                    .when(emailService).sendWelcomeLetter(member);

            mockMvc.perform(post("/api/members/8/send-welcome"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("No email address is available for this member."));
        }

        @Test
        @DisplayName("answers 404, and sends nothing, for a member that does not exist")
        void unknownMemberSendsNothing() throws Exception {
            when(memberService.findById(99)).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/members/99/send-welcome"))
                    .andExpect(status().isNotFound());

            verify(emailService, never()).sendWelcomeLetter(any());
        }
    }

    @Nested
    @DisplayName("GET /api/members/{id}/welcome-letter-preview")
    class WelcomeLetterPreview {

        @Test
        @DisplayName("returns inline PDF bytes for THAT member")
        void returnsPdfPreviewForThatMember() throws Exception {
            Member member = member(12, "Mahlangu");
            byte[] pdf = "sample-pdf".getBytes();
            when(memberService.findById(12)).thenReturn(Optional.of(member));
            when(emailService.buildWelcomeLetterPdf(member)).thenReturn(pdf);

            mockMvc.perform(get("/api/members/12/welcome-letter-preview"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"Sasbo Welcome Letter.pdf\""))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(pdf));

            verify(emailService).buildWelcomeLetterPdf(member);
        }

        @Test
        @DisplayName("answers 404 and does not build preview when member does not exist")
        void returns404ForUnknownMember() throws Exception {
            when(memberService.findById(404)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/members/404/welcome-letter-preview"))
                    .andExpect(status().isNotFound());

            verify(emailService, never()).buildWelcomeLetterPdf(any());
        }
    }
}
