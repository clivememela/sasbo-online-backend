package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 🔴 <b>Register row A33.</b> Reading a subscription and writing it back
 * unchanged answers <b>500 — after the write has already been committed.</b>
 *
 * <h2>Why it has to be an integration test</h2>
 * The failure is a lifecycle one, and it is invisible to every cheaper layer.
 * {@code SubscriptionsServiceImpl.update} loads the row with {@code findById}
 * (not the {@code findByIdWithBank} sitting beside it), saves it, and then maps
 * the saved entity to its DTO. {@code Subscriptions.bank} is
 * {@code FetchType.LAZY} and the method carries no transaction, so each
 * repository call is its own unit of work: by the time the mapper reaches
 * {@code getBank()} the session is closed and Hibernate throws
 * <i>"Could not initialize proxy [Bank#…] - no session"</i>.
 *
 * <p>A mocked service test cannot see it — there is no session to close. A
 * {@code @DataJpaTest} cannot either, because it wraps each test in a
 * transaction that <b>keeps the session open</b> and initialises the proxy,
 * which is precisely the condition production does not have. This class extends
 * {@link AbstractWritingIT} and is <b>deliberately not transactional</b>, so the
 * boundaries are the real ones.
 *
 * <h2>What the defect costs, which is more than a 500</h2>
 * The update is already in the database when the exception is thrown. The
 * administrator is told the save failed, the screen keeps showing the old value,
 * and the obvious response is to try again — while the money table has already
 * changed underneath them. Verified against production behaviour on 2026-09-06:
 * a PUT that answered 500 had changed {@code acbDebitDay} from 25 to 21.
 *
 * <h2>The body is the one the API just produced</h2>
 * Each test reads the row over HTTP and sends <b>that exact JSON</b> back. A
 * hand-written payload would have to guess the shape of
 * {@code registrationDate}, which this application serialises as a numeric array
 * rather than a string (register A20), and a round-trip test that cannot
 * round-trip its own date format is testing the author's memory.
 */
@AutoConfigureMockMvc
@DisplayName("PUT /api/subscriptions/{id} against a real database")
class SubscriptionsUpdateRoundTripIT extends AbstractWritingIT {

    /** Fixed. Nothing here reads the wall clock. */
    private static final LocalDateTime REGISTERED = LocalDateTime.of(2024, 3, 1, 9, 0);

    private static final long SUBSCRIPTION_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BankRepository bankRepository;

    @Test
    @DisplayName("an unchanged row is written back and answered with the same body")
    void anUnchangedRoundTripIsKept() throws Exception {
        seedSubscription();
        JsonNode before = readSubscription();

        MockHttpServletResponse response = putSubscription(before);

        assertThat(response.getStatus())
                .as("PUT of an unchanged subscription, whose body the API itself just produced")
                .isEqualTo(200);

        JsonNode answered = this.objectMapper.readTree(response.getContentAsString());
        assertThat(answered.get("bankName").asText())
                .as("the bank survives the round trip -- the lazy proxy A33 could not initialise")
                .isEqualTo("Absa");
        assertThat(answered.get("acbDebitDay").asInt()).isEqualTo(before.get("acbDebitDay").asInt());
        assertThat(answered.get("subscriptionAmount").decimalValue())
                .isEqualByComparingTo(before.get("subscriptionAmount").decimalValue());
        assertThat(answered.get("status").asText()).isEqualTo(before.get("status").asText());
    }

    @Test
    @DisplayName("a corrected A.C.B. debit day is kept, and the answer says so")
    void aCorrectedDebitDayIsKept() throws Exception {
        // The automation suite's @defect:A33 scenario, in the lane that can prove
        // it without a browser: "the A.C.B. debit day on <bank> is corrected to 21".
        seedSubscription();
        ObjectNode corrected = (ObjectNode) readSubscription();
        corrected.put("acbDebitDay", 21);

        MockHttpServletResponse response = putSubscription(corrected);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(this.objectMapper.readTree(response.getContentAsString()).get("acbDebitDay").asInt())
                .as("what the API answered")
                .isEqualTo(21);
        assertThat(readSubscription().get("acbDebitDay").asInt())
                .as("what the database holds afterwards -- A33 committed this much and still answered 500")
                .isEqualTo(21);
    }

    @Test
    @DisplayName("a row that does not exist is a 404, not a 200 and not a 500")
    void aMissingRowIsNotFound() throws Exception {
        // The must-pass twin. Without it, a route that answered 404 to everything
        // would satisfy neither test above and nobody would know which half broke:
        // this one fails the moment the endpoint stops finding rows at all.
        seedSubscription();
        ObjectNode body = (ObjectNode) readSubscription();

        MockHttpServletResponse response = this.mockMvc.perform(
                        put("/api/subscriptions/999999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(this.objectMapper.writeValueAsString(body)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
    }

    private void seedSubscription() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.jdbc.update(
                "INSERT INTO subscriptions "
                        + "(id, acb_debit_day, core_banking, registration_date, status, "
                        + " subscription_amount, bank_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                SUBSCRIPTION_ID, 25, true, REGISTERED, SubscriptionsStatus.ACTIVE.name(),
                new BigDecimal("120.50"), absa.getId());
    }

    private JsonNode readSubscription() throws Exception {
        MockHttpServletResponse response = this.mockMvc
                .perform(get("/api/subscriptions/" + SUBSCRIPTION_ID))
                .andReturn().getResponse();
        assertThat(response.getStatus())
                .as("reading the row back is the precondition of every test here")
                .isEqualTo(200);
        return this.objectMapper.readTree(response.getContentAsString());
    }

    private MockHttpServletResponse putSubscription(JsonNode body) throws Exception {
        return this.mockMvc.perform(
                        put("/api/subscriptions/" + SUBSCRIPTION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(this.objectMapper.writeValueAsString(body)))
                .andReturn().getResponse();
    }
}
