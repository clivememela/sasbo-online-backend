package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import za.co.titandynamix.sasbo_online_backend.controller.MemberController;
import za.co.titandynamix.sasbo_online_backend.dto.member.PaymentTypeMetadataDto;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The committed API artifacts must equal what this application actually routes
 * and actually sends.
 *
 * <h2>What this is protecting</h2>
 * The browser cannot see this repository. It gets a <b>copy</b> of
 * {@code api-surface.txt}, and its own guard asserts that every path its
 * services call appears in that copy. That guard is only as good as the file it
 * reads: a stale artifact turns a contract test into a test of what the API
 * looked like the last time somebody remembered to regenerate.
 *
 * <p>So this is the test that makes the artifact trustworthy. Rename an
 * endpoint, add one, delete one -- the server build reddens here, in the
 * repository where the change was made, in the fast lane, in milliseconds.
 * Nobody has to remember anything.
 *
 * <h2>Why it lives in the fast lane, and what it costs</h2>
 * No Docker and no database: the handler mapping is populated at context
 * refresh, before a single query runs. The {@code @SpringBootTest} is bare on
 * purpose -- no {@code @ActiveProfiles}, no {@code @MockBean}, no
 * {@code webEnvironment} -- so it shares a context-cache key with
 * {@code SasboOnlineBackendApplicationTests} and boots <b>no extra context</b>.
 * Measured 2026-09-05: both classes released within 60ms of each other off one
 * load, and this class finishes ~3s before the suite's last slice. Added
 * fast-lane wall clock: none.
 *
 * <p>The instinct to "make it lighter" as a {@code @WebMvcTest} slice would do
 * the opposite: a slice listing twelve controllers is a new cache key, and
 * therefore a genuinely new boot.
 *
 * <h2>Regenerating</h2>
 * {@code ./gradlew generateApiSurface}, then commit the diff. That task runs
 * this same class with {@code -DapiSurface.write=true}, so the generator and
 * the checker cannot disagree about the format -- they are the same code.
 */
@SpringBootTest
@DisplayName("API surface snapshot")
class ApiSurfaceSnapshotTest {

    /**
     * Deliberately below the real count, with headroom.
     *
     * <p>Measured through this guard's own discovery on 2026-09-05: <b>41</b>
     * routed lines -- forty from eleven of the twelve controllers, plus the
     * framework's {@code ANY /error}. {@code ACBController} contributes none;
     * it declares no handler methods at all.
     *
     * <p>The floor is not a coverage target and must never be raised to match.
     * Delete a controller in a legitimate refactor and a floor of 41 reddens for
     * a reason nobody caused.
     *
     * <p>What it is actually for: {@code getHandlerMethods()} returning an empty
     * map. That writes an empty artifact, compares it against an empty committed
     * file, and <b>passes</b> -- a green run over a guard checking nothing.
     */
    private static final int ROUTE_FLOOR = 30;

    /**
     * Two payment types today. A floor of 1 catches the only vacuity that
     * matters here -- the endpoint returning nothing, which would publish
     * {@code "paymentTypes": []} and compare it happily against the empty list
     * it had just written.
     */
    private static final int PAYMENT_TYPE_FLOOR = 1;

    /** Six components today. Same reasoning, same headroom. */
    private static final int ENVELOPE_KEY_FLOOR = 3;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    /** The controller itself, so the artifact carries what it actually returns. */
    @Autowired
    private MemberController memberController;

    /** The application's mapper, so the artifact's keys are the wire's keys. */
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the committed surface is exactly what the application routes")
    void theCommittedSurfaceMatchesTheLiveMapping() {
        List<String> routes = ApiSurface.routes(handlerMapping);

        // Before comparing anything, and before WRITING anything: prove the
        // enumeration enumerated. A broken mapping lookup makes every other
        // assertion in this class vacuous, and vacuous reads as green.
        assertNonVacuousScan(routes, ROUTE_FLOOR, "routes read from RequestMappingHandlerMapping");

        String live = ApiSurface.renderSurface(routes);
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(ApiSurface.SURFACE_FILE, live);
        }

        assertThat(ApiSurface.readCommittedNormalised(ApiSurface.SURFACE_FILE))
                .as("%s is STALE. The browser's contract guard reads a copy of this file, so "
                        + "while it is stale that guard is checking an API this server no longer "
                        + "has -- it will pass for a path that now 404s, and redden for one that "
                        + "works. Regenerate and commit the diff: %s",
                        ApiSurface.SURFACE_FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("the committed vocabulary is exactly what the server publishes")
    void theCommittedVocabularyMatchesTheServersOwn() {
        // The endpoint's own response, not a list rebuilt from the enum. That is
        // the difference between publishing the contract and publishing a second
        // implementation of it.
        List<PaymentTypeMetadataDto> metadata =
                memberController.getPaymentTypeMetadata().getBody();

        assertNonVacuousScan(
                metadata == null ? List.of() : metadata,
                PAYMENT_TYPE_FLOOR,
                "payment types returned by GET /api/members/metadata/payment-types");
        assertNonVacuousScan(
                ApiSurface.errorEnvelopeKeys(),
                ENVELOPE_KEY_FLOOR,
                "keys declared by ApiErrorResponse");

        String live = ApiSurface.renderVocabulary(metadata, objectMapper);
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(ApiSurface.VOCABULARY_FILE, live);
        }

        assertThat(ApiSurface.readCommittedNormalised(ApiSurface.VOCABULARY_FILE))
                .as("%s is STALE. The browser derives its payment-type options AND its "
                        + "'does this need a bank account' rule from this file, and parses error "
                        + "responses by the keys in it. While it is stale the browser is offering "
                        + "a vocabulary this server does not accept, which reaches a real person "
                        + "as a failed capture rather than as a warning. Regenerate: %s",
                        ApiSurface.VOCABULARY_FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("the committed artifacts carry LF endings, on every platform")
    void theArtifactsAreWrittenWithUnixEndings() {
        // This owns the line-ending claim ALONE. The two freshness tests above
        // read normalised content precisely so that a CRLF checkout reddens
        // here, naming the .gitattributes rule, instead of reddening there and
        // announcing an API change that never happened.
        //
        // It asserts the files exist first, because a missing file returns a
        // sentinel string that contains no \r -- which would let this pass
        // vacuously over an artifact that is not there at all.
        for (var file : List.of(ApiSurface.SURFACE_FILE, ApiSurface.VOCABULARY_FILE)) {
            assertThat(Files.isRegularFile(file))
                    .as("%s does not exist. Run %s", file, ApiSurface.regenerateCommand())
                    .isTrue();
            assertThat(ApiSurface.readCommitted(file))
                    .as("%s has CRLF endings. The browser's guard compares this file's bytes, so "
                            + "check the `%s text eol=lf` rule is present in .gitattributes and "
                            + "re-check-out the file", file, file)
                    .doesNotContain("\r");
        }
    }

    @Test
    @DisplayName("the error envelope still carries the keys the browser parses")
    void theErrorEnvelopeKeysAreDiscoveredNotAssumed() {
        // The three named keys are an ANCHOR, not the contract: these are the
        // ones the browser's error handling actually reads -- the status it
        // switches on, the message it shows, and the per-field map it renders
        // beside the inputs. The other three (timestamp, error, path) are
        // equally on the wire and equally published by the artifact; they are
        // simply not ones the browser branches on, so losing one is a smaller
        // event than losing these.
        //
        // Discovery does the general half: whatever ApiErrorResponse declares
        // gets published, so a new component reaches the browser by
        // regenerating rather than by anyone remembering.
        assertThat(ApiSurface.errorEnvelopeKeys())
                .as("the keys the browser's error handling reads, from ApiErrorResponse's own "
                        + "record components. Losing one of these does not break a server test -- "
                        + "field errors simply stop appearing on the form")
                .contains("status", "message", "validationErrors");
    }
}
