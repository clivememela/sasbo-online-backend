package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 🔴 A64, WHERE THE TRANSACTION IS REAL. A capture that asks for its welcome letter saves the member
 * first -- committed -- and a letter that fails cannot take the member back out.
 *
 * <p>The web slices cannot see this: they have no transaction manager, so a send inside the capture's
 * transaction and a send after it look identical there ({@code NoTransactions}). Here the capture runs
 * through the real service against PostgreSQL, and only the mail sender is a mock -- the one thing that
 * can fail on purpose.
 *
 * <p>Where mail is not configured at all ({@code EMAIL_OFF}) is {@code MemberCaptureRoundTripIT}: that
 * context has no mail bean, and this one exists to have one.
 */
@AutoConfigureMockMvc
@DisplayName("The welcome letter after a capture, against a real database (A64)")
class WelcomeLetterAfterCaptureIT extends AbstractWritingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private EmailService emailService;

    private static final String ON_RECORD = "SELECT count(*) FROM members WHERE identity_number = ?";

    /**
     * The capture form's payload -- {@code MemberCaptureRoundTripIT}'s, which is proven to save on this
     * schema -- with the personal address as the one thing a test varies. Nothing else in it is the subject.
     */
    private static String payload(String personalEmail) {
        return """
                {
                  "title": "Mr",
                  "initials": "S",
                  "firstNames": "Synthetic",
                  "surname": "Testperson",
                  "identityType": "I.D. Number",
                  "identityNumber": "%s",
                  "gender": "Male",
                  "race": "African",
                  "personalEmail": "%s",
                  "workEmail": "",
                  "preferredEmail": "personal",
                  "phone": "0115550000",
                  "address": "1 Test Street",
                  "joinDate": "2026-09-09",
                  "status": "Active",
                  "subscriptionFrequency": "Monthly",
                  "employeeNumber": "EMP-0001",
                  "bankName": "Nedbank",
                  "bankAccountNumber": "1234567890",
                  "accountType": "Cheque",
                  "branch": "Head Office",
                  "employmentType": "Permanent",
                  "paymentType": "Debit Order",
                  "debitDay": 20,
                  "isRepresentative": false,
                  "isCosatu": "N",
                  "cosatuPositions": [],
                  "selectedBecStructures": []
                }
                """.formatted(MemberFixture.SYNTHETIC_ID, personalEmail);
    }

    @Test
    @DisplayName("🔴 a letter that fails leaves the member on record -- and the answer says FAILED")
    void aFailedLetterDoesNotUndoTheCapture() throws Exception {
        // Before A64's fix the send ran inside the capture's @Transactional: this exception rolled the
        // member back, and a retry would capture -- and mail -- them a second time.
        doThrow(new RuntimeException("SMTP is down")).when(this.emailService).sendWelcomeLetter(any(Member.class));

        JsonNode answer = captureAskingForTheLetter(payload("thandi@example.invalid"));

        assertThat(answer.path("welcomeLetter").asText()).isEqualTo("FAILED");
        assertThat(this.jdbc.queryForObject(ON_RECORD, Integer.class, MemberFixture.SYNTHETIC_ID))
                .as("the failed letter took the member back out of the database")
                .isEqualTo(1);
        int status = this.mockMvc.perform(get("/api/members/{id}", answer.get("id").asInt()))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("the saved member can be read back").isEqualTo(200);
    }

    @Test
    @DisplayName("🔑 the letter is attempted only once the member is COMMITTED -- visible to another connection")
    void theLetterWaitsForTheCommit() throws Exception {
        // The ordering, proven rather than inferred: the sender looks for the member through a
        // connection of its own. Under READ COMMITTED an uncommitted row is invisible to it, so a send
        // still inside the capture's transaction would count 0 here.
        AtomicInteger seenFromOutside = new AtomicInteger(-1);
        doAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            try (Connection other = this.dataSource.getConnection();
                 PreparedStatement query = other.prepareStatement("SELECT count(*) FROM members WHERE id = ?")) {
                query.setInt(1, member.getId());
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    seenFromOutside.set(rows.getInt(1));
                }
            }
            return null;
        }).when(this.emailService).sendWelcomeLetter(any(Member.class));

        JsonNode answer = captureAskingForTheLetter(payload("thandi@example.invalid"));

        assertThat(answer.path("welcomeLetter").asText()).isEqualTo("SENT");
        assertThat(seenFromOutside.get())
                .as("when the letter was attempted, another connection could not see the member yet -- "
                        + "the send ran inside the capture's transaction")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 a member with no email address is captured, and no letter is attempted: NO_ADDRESS")
    void aMemberWithNoAddressIsCaptured() throws Exception {
        JsonNode answer = captureAskingForTheLetter(payload(""));

        assertThat(answer.path("welcomeLetter").asText()).isEqualTo("NO_ADDRESS");
        assertThat(this.jdbc.queryForObject(ON_RECORD, Integer.class, MemberFixture.SYNTHETIC_ID)).isEqualTo(1);
        verify(this.emailService, never()).sendWelcomeLetter(any(Member.class));
    }

    private JsonNode captureAskingForTheLetter(String body) throws Exception {
        MockHttpServletResponse response = this.mockMvc.perform(post("/api/members?sendWelcome=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus())
                .as("the capture was refused, and the server said: %s", response.getContentAsString())
                .isEqualTo(200);
        return this.objectMapper.readTree(response.getContentAsString());
    }
}
