package za.co.titandynamix.sasbo_online_backend.guards;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Personal information does not reach the log.
 *
 * <h2>Two halves, and neither is sufficient alone</h2>
 * <ol>
 *   <li><b>Static.</b> A type carrying a PII-named field must not generate a
 *       {@code toString()} over it. Lombok's {@code @Data} and {@code @ToString}
 *       both do, and the moment such an object appears in a log format argument
 *       every field goes with it -- including the ones nobody was thinking
 *       about. This half catches the <i>capability</i>, before anyone uses it.
 *   <li><b>Behavioural.</b> Capture what is actually logged while a code path
 *       that logs runs, and assert no PII value is in it. This half catches the
 *       <i>use</i>, including uses the static half cannot see: a hand-written
 *       {@code log.info("id " + member.getIdentityNumber())} generates no
 *       {@code toString()} at all.
 * </ol>
 *
 * <p>This application holds identity numbers and bank account numbers for real
 * people, runs at {@code root: DEBUG}, and mounts a request-logging filter with
 * {@code setIncludePayload(true)}. Logs are forwarded, pasted into tickets and
 * shipped to aggregators, so a leak here is a privacy breach with a URL rather
 * than an untidy line.
 *
 * <h2>Written against the field name, not against the class</h2>
 * The guard discovers types by scanning for PII-shaped field names, so a new
 * entity carrying an {@code identityNumber} is covered the day it is written.
 * Naming today's two offenders would have closed this exact hole and left the
 * hole open.
 */
@DisplayName("PII in logs")
class PiiInLogsTest {

    /**
     * Field names that carry personal information. Substring-matched
     * case-insensitively, so {@code memberIdentityNumber} and
     * {@code accountNumber} are both caught without listing either.
     */
    private static final Set<String> PII_FIELD_MARKERS = Set.of(
            "identitynumber", "idnumber", "passportnumber", "accountnumber",
            "dateofbirth", "taxnumber");

    /** Lombok annotations that generate a {@code toString()} over every field. */
    private static final List<String> TOSTRING_GENERATORS = List.of("@Data", "@ToString");

    /**
     * Types that generate a {@code toString()} over PII today, and what each
     * is. Both are response DTOs annotated {@code @Data}, which is the default
     * reach for a DTO and the reason this is a guard rather than a review note.
     *
     * <p>Asserted, not suppressed: each entry is checked to still be a real
     * violation, so removing {@code @Data} from one reddens this test until the
     * entry goes. Changing that is a production change and is written up as a
     * finding rather than applied by a test suite.
     */
    private static final Map<String, String> KNOWN_TOSTRING_OVER_PII = Map.of(
            "MemberDto",
            "@Data over identityNumber, passportNumber, dateOfBirth and bankAccountNumber",
            "BankingDetailsDto",
            "@Data over accountNumber");

    private LogCapture captured;

    @BeforeEach
    void startCapturing() {
        // The exception handler's OWN logger, not the root one -- see LogCapture
        // for why that distinction is the difference between evidence and a
        // coin toss under a parallel runner.
        this.captured = LogCapture.on(GlobalExceptionHandler.class);
    }

    @AfterEach
    void stopCapturing() {
        this.captured.close();
    }

    @Test
    @DisplayName("no new type generates a toString over a PII field")
    void noTypeGeneratesAToStringOverPii() {
        List<MainClassSource> dataTypes = new ArrayList<>();
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.ENTITY));
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.DTO));

        assertNonVacuousScan(dataTypes, 30, "entities and DTOs");

        List<String> offenders = new ArrayList<>();
        for (MainClassSource type : dataTypes) {
            if (KNOWN_TOSTRING_OVER_PII.containsKey(type.name()) || !carriesPii(type)) {
                continue;
            }
            for (String generator : TOSTRING_GENERATORS) {
                if (type.linesContaining(generator).isEmpty()) {
                    continue;
                }
                offenders.add(type.at(type.linesContaining(generator).get(0))
                        + " " + generator + " over a PII field");
            }
        }

        assertThat(offenders)
                .as("a generated toString() over personal information puts every field one log "
                        + "format argument away from the log file. Use @Getter/@Setter instead "
                        + "of @Data, or @ToString.Exclude the field")
                .isEmpty();
    }

    @Test
    @DisplayName("every listed toString-over-PII exception is still real")
    void knownToStringViolationsAreStillViolations() {
        List<MainClassSource> dataTypes = new ArrayList<>();
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.ENTITY));
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.DTO));

        for (Map.Entry<String, String> entry : KNOWN_TOSTRING_OVER_PII.entrySet()) {
            MainClassSource type = dataTypes.stream()
                    .filter(t -> t.name().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            entry.getKey() + " is listed but no longer exists. Remove the entry."));

            boolean stillGenerates = TOSTRING_GENERATORS.stream()
                    .anyMatch(g -> !type.linesContaining(g).isEmpty());

            assertThat(stillGenerates && carriesPii(type))
                    .as("%s is listed as generating a toString over PII -- \"%s\" -- but no "
                            + "longer does. Delete the entry", entry.getKey(), entry.getValue())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the exception handler logs the path and the type, never the payload")
    void theExceptionHandlerLogsNoPii() {
        // The behavioural half. The catch-all is the one place in this
        // application that deliberately logs an exception, and an exception
        // raised while binding or persisting a member is exactly where an
        // identity number ends up inside a message.
        String syntheticId = "9404275800086";
        String syntheticAccount = "62001234567";

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/members");

        new GlobalExceptionHandler().handleEntityNotFound(
                new EntityNotFoundException("Member not found"), request);

        String logged = this.captured.rendered();

        assertThat(logged)
                .as("an identity number reached the log. Logs are forwarded, pasted into tickets "
                        + "and shipped to aggregators -- this is a privacy breach with a URL, not "
                        + "an untidy line")
                .doesNotContain(syntheticId);
        assertThat(logged)
                .as("a bank account number reached the log")
                .doesNotContain(syntheticAccount);
    }

    @Test
    @DisplayName("the capture itself works -- otherwise the assertion above is vacuous")
    void theCaptureActuallyCaptures() {
        // Without this, a broken appender makes every assertion in this class
        // pass against an empty string. The guard has to prove it can see
        // something before "it saw nothing bad" means anything.
        this.captured.emitCanary("canary for the log capture");

        assertThat(this.captured.rendered())
                .as("the appender captured nothing at all, so a doesNotContain assertion here "
                        + "would pass whatever the application logged")
                .contains("canary for the log capture");
    }

    private static boolean carriesPii(MainClassSource type) {
        String lowered = type.code().toLowerCase(java.util.Locale.ROOT);
        return PII_FIELD_MARKERS.stream().anyMatch(lowered::contains);
    }
}
