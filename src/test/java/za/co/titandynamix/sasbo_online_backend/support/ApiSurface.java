package za.co.titandynamix.sasbo_online_backend.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import za.co.titandynamix.sasbo_online_backend.dto.member.PaymentTypeMetadataDto;
import za.co.titandynamix.sasbo_online_backend.exception.ApiErrorResponse;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;
import za.co.titandynamix.sasbo_online_backend.enums.AccountType;
import za.co.titandynamix.sasbo_online_backend.enums.BecExecutivePosition;
import za.co.titandynamix.sasbo_online_backend.enums.BecStructure;
import za.co.titandynamix.sasbo_online_backend.enums.CosatuPosition;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.enums.WelcomeLetterOutcome;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.enums.ListEmptyReason;
import za.co.titandynamix.sasbo_online_backend.enums.SeedFailure;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.services.SummaryGate;
import za.co.titandynamix.sasbo_online_backend.enums.SummaryGrouping;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The two artifacts the browser's contract guards read, derived from the
 * running application rather than written by hand.
 *
 * <h2>Why these are generated and never edited</h2>
 * A hand-maintained list of endpoints is a <b>second source of truth</b>. It
 * agrees with the application on the day it is written and drifts silently
 * afterwards, and its drift is invisible until production -- which is the exact
 * failure the guard reading it is supposed to catch. So the surface comes from
 * Spring's own {@link RequestMappingHandlerMapping}: the routes the container
 * will actually serve, not the annotations someone believes are there.
 *
 * <p>{@code ApiSurfaceSnapshotTest} is what makes the committed copy
 * trustworthy. Without it a generated file is just a stale file with a better
 * provenance story.
 *
 * <h2>Everything here comes from the WIRE type, never from a transcription</h2>
 * The same rule applies one level down, and it is easy to miss. The vocabulary
 * artifact is built by serialising the objects the controller itself returns,
 * through the application's own {@code ObjectMapper} -- not by reading
 * {@code PaymentType} and re-assembling the triple the controller assembles.
 * Re-assembling it would make this class a <b>second implementation of the
 * endpoint's response shape</b>: rename a field on
 * {@code PaymentTypeMetadataDto} and the endpoint would stop sending it while
 * the artifact kept publishing it, byte-identical and green. That is this
 * phase's own failure mode, reproduced inside the guard meant to prevent it.
 *
 * <h2>Why the project root, and not {@code src/main/resources}</h2>
 * Anything under {@code src/main/resources} is <b>packaged into the deployable
 * JAR</b>. These are test artifacts: shipping a description of the entire API
 * surface to production serves no runtime purpose and hands an attacker a map.
 * Both the generator and the freshness test are Gradle tests with the project
 * directory as their working directory, so neither needs the classpath.
 *
 * <h2>Line endings</h2>
 * Written with {@code \n} explicitly, never {@code System.lineSeparator()}, and
 * pinned to LF by a narrowly scoped {@code .gitattributes} rule. That rule is
 * also why the layout below is assembled here rather than by Jackson's pretty
 * printer, which indents with {@code SYSTEM_LINEFEED_INSTANCE} and would emit
 * CRLF on this machine.
 */
public final class ApiSurface {

    /** Set by the {@code generateApiSurface} Gradle task, and by nothing else. */
    private static final String WRITE_PROPERTY = "apiSurface.write";

    /** The routed surface: one {@code METHOD /path} per line. */
    public static final Path SURFACE_FILE = Paths.get("api-surface.txt");

    /** The companion artifact: the shared vocabulary, and the failure envelope. */
    public static final Path VOCABULARY_FILE = Paths.get("api-vocabulary.json");

    private ApiSurface() {
    }

    /** True when the run was asked to regenerate rather than to verify. */
    public static boolean writeRequested() {
        return Boolean.parseBoolean(System.getProperty(WRITE_PROPERTY, "false"));
    }

    /** The command a failure message tells the reader to run. */
    public static String regenerateCommand() {
        return "./gradlew generateApiSurface";
    }

    // -----------------------------------------------------------------------
    // The routed surface
    // -----------------------------------------------------------------------

    /**
     * One route, kept as its two parts until the moment it is rendered.
     *
     * <p>A record rather than a formatted string, so the {@code "METHOD path"}
     * format lives in exactly one place. Sorting formatted strings meant
     * splitting each one back apart on every comparison, which put the format
     * in two places thirty lines apart -- change the separator and one of them
     * gets forgotten.
     */
    private record Route(String method, String path) {

        private static final Comparator<Route> BY_PATH_THEN_METHOD =
                Comparator.comparing(Route::path).thenComparing(Route::method);

        private String line() {
            return method + " " + path;
        }
    }

    /**
     * Every route the application will actually serve, as {@code METHOD /path}.
     *
     * <p>Sorted by <b>path first, then method</b>, so the file groups by
     * resource and a diff shows one endpoint moving rather than a reshuffle.
     *
     * <p>A mapping declared with no HTTP method answers all of them; it is
     * rendered {@code ANY /path} rather than expanded into seven lines that
     * would imply seven separate declarations.
     *
     * <p><b>Nothing is filtered.</b> Framework routes such as {@code /error}
     * are part of the routed surface and appear here. Hiding them would make
     * this file a curated list -- and a curated list is the hand-maintained
     * second source of truth this class exists to avoid.
     */
    public static List<String> routes(RequestMappingHandlerMapping mapping) {
        Set<Route> found = new TreeSet<>(Route.BY_PATH_THEN_METHOD);
        mapping.getHandlerMethods().keySet().forEach(info -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patternsOf(info)) {
                if (methods.isEmpty()) {
                    found.add(new Route("ANY", pattern));
                } else {
                    methods.forEach(method -> found.add(new Route(method.name(), pattern)));
                }
            }
        });
        return found.stream().map(Route::line).toList();
    }

    /** The file body for {@link #SURFACE_FILE}, trailing newline included. */
    public static String renderSurface(List<String> routes) {
        return String.join("\n", routes) + "\n";
    }

    /**
     * Spring 6 parses paths with {@code PathPatternParser} by default, and then
     * {@code getPatternsCondition()} is null rather than empty. Asking the wrong
     * one first is how this returns nothing and the guard passes over an empty
     * file, so both are tried and neither is assumed.
     */
    private static Set<String> patternsOf(RequestMappingInfo info) {
        var pathPatterns = info.getPathPatternsCondition();
        if (pathPatterns != null) {
            return pathPatterns.getPatternValues();
        }
        var patterns = info.getPatternsCondition();
        return patterns != null ? patterns.getPatterns() : Set.of();
    }

    // -----------------------------------------------------------------------
    // The companion artifact -- vocabulary and failure envelope
    // -----------------------------------------------------------------------

    /**
     * The shared vocabulary and the failure envelope, as JSON.
     *
     * <h2>Why {@code requiresBankAccount} travels with the display name</h2>
     * A duplicated <b>value</b> is one defect; a duplicated <b>rule</b> is the
     * same defect one level up. The server publishes "debit order needs a bank
     * account"; the browser re-derives it from a string comparison. Publishing
     * the rule beside the value is what lets the browser guard assert the two
     * agree for <b>every</b> option, rather than for the one that happened to
     * prompt the test.
     *
     * <h2>Why the error envelope is in here at all</h2>
     * The routed surface proves a <b>path</b> exists. The vocabulary proves a
     * <b>value</b> matches. Neither looks at what comes back when the call
     * fails -- and the failure envelope is as much a contract as the success
     * payload.
     *
     * <p><b>Checked on 2026-09-05, because the plan's premise had expired.</b>
     * The plan justified this by saying a rename of {@code validationErrors}
     * would be invisible to every test in the arc. That was true when it was
     * written and is not true now: P2 added {@code GlobalExceptionHandlerTest},
     * which calls {@code body.validationErrors()} and would fail to COMPILE,
     * and {@code DeletionCodeControllerTest}, which asserts the JSON path. The
     * server half of that hole is closed.
     *
     * <p>The half still open is the browser's, and nothing on the server can
     * close it: field-level validation messages would simply stop appearing on
     * the form, while every server test stayed green and the API kept answering
     * 400 exactly as it always had.
     *
     * @param paymentTypeMetadata the list the metadata endpoint itself returned
     *                            -- not a list rebuilt from the enum
     * @param mapper              the application's own {@code ObjectMapper}, so
     *                            the keys written here are the keys that go on
     *                            the wire
     */
    public static String renderVocabulary(
            List<PaymentTypeMetadataDto> paymentTypeMetadata, ObjectMapper mapper) {

        String types = paymentTypeMetadata.stream()
                .map(dto -> "    " + writeCompact(mapper, dto))
                .collect(Collectors.joining(",\n"));

        String keys = errorEnvelopeKeys().stream()
                .map(ApiSurface::quote)
                .collect(Collectors.joining(", "));

        return """
                {
                  "_generated": "%s -- do not edit by hand",
                  "paymentTypes": [
                %s
                  ],
                  "accountTypes": [%s],
                  "memberStructures": {
                    "becStructures": [%s],
                    "becExecutivePositions": [%s],
                    "cosatuPositions": [%s]
                  },
                  "errorEnvelopeKeys": [%s],
                  "jobGradeMatching": {
                    "outcomes": [%s],
                    "cases": [
                %s
                    ]
                  },
                  "welcomeLetter": {
                    "outcomes": [%s]
                  },
                  "acb": {
                    "runStatuses": [%s],
                    "lineOutcomes": [%s],
                    "unpaidDecisions": [%s],
                    "unpaidReasons": [%s],
                    "letterTemplates": [%s],
                    "letterStatuses": [%s],
                    "declineReasons": [%s],
                    "exclusionReasons": [
                %s
                    ],
                    "refusalCodes": [
                %s
                    ]
                  },
                  "monthEnd": {
                    "workplaceSwitch": [%s],
                    "listEmptyReasons": [%s],
                    "seedFailures": [%s],
                    "workplacePageMaxSize": %d,
                    "searchMaxLength": %d,
                    "registerPageMaxSize": %d,
                    "registerSorts": [%s],
                    "summaryGroupings": [%s],
                    "summaryTimeoutSeconds": %d,
                    "summaryAtOnce": %d,
                    "memberStatuses": [
                %s
                    ],
                    "statusReasons": [%s],
                    "deletionCodes": [
                %s
                    ],
                    "suspensionCodes": [
                %s
                    ],
                    "acbReasonReadings": [
                      {"reason": "SUBS_IN_ARREARS", "deletionCode": 5, "proposed": true},
                      {"reason": "UNPAID_SUSPENDED", "suspensionCode": 13, "proposed": true}
                    ],
                    "refusalCodes": [
                %s
                    ]
                  }
                }
                """.formatted(regenerateCommand(), types, accountTypeNames(),
                displayNames(BecStructure.values(), BecStructure::getDisplayName),
                displayNames(BecExecutivePosition.values(), BecExecutivePosition::getDisplayName),
                displayNames(CosatuPosition.values(), CosatuPosition::getDisplayName),
                keys, jobGradeOutcomeNames(),
                jobGradeMatchingCases(), names(WelcomeLetterOutcome.values()),
                names(AcbRunStatus.values()), names(AcbLineOutcome.values()),
                names(UnpaidDecision.values()), names(UnpaidReason.values()), names(LetterTemplate.values()),
                names(LetterStatus.values()), names(AcbDeclineReason.values()), acbExclusionReasons(),
                acbRefusalCodes(),
                names(WorkplaceSwitch.Mode.values()), names(ListEmptyReason.values()), names(SeedFailure.values()),
                WorkplaceService.MAX_PAGE_SIZE, WorkplaceService.MAX_SEARCH_LENGTH,
                MemberRegisterService.MAX_PAGE_SIZE,
                MemberRegisterService.SORTS.stream().map(ApiSurface::quote).collect(Collectors.joining(", ")),
                Arrays.stream(SummaryGrouping.values()).map(g -> quote(g.wire())).collect(Collectors.joining(", ")),
                MemberSummaryRepository.TIMEOUT_SECONDS, SummaryGate.AT_ONCE,
                memberStatuses(), names(MemberStatusReason.values()), seededCodes("deletionCodes"),
                seededCodes("suspensionCodes"),
                monthEndRefusalCodes());
    }

    /**
     * Every status the member record takes, with its KIND -- the one thing a figure reads of a status (03.2, PA-4).
     * Read off {@code MemberStatuses.KINDS}, the server's own table, never typed out here.
     */
    private static String memberStatuses() {
        return MemberStatuses.KINDS.entrySet().stream()
                .map(e -> "      {\"name\": %s, \"kind\": %s}".formatted(quote(e.getKey()), quote(e.getValue().name())))
                .collect(Collectors.joining(",\n"));
    }

    /**
     * SASBO's deletion or suspension codes as the seed resource holds them: SASBO's number and its words -- never the
     * table's id (RC7). A reason for resigning has no number of SASBO's, and is published under its code.
     */
    private static String seededCodes(String list) {
        try (var stream = new org.springframework.core.io.ClassPathResource(ReferenceListSeeder.RESOURCE).getInputStream()) {
            var resource = new ObjectMapper().readTree(stream);
            List<String> rows = new java.util.ArrayList<>();
            for (var node : resource.path("lists")) {
                if (!list.equals(node.path("list").asText())) {
                    continue;
                }
                for (var row : node.path("rows")) {
                    if (list.equals("deletionCodes")) {
                        rows.add(row.path("sasboCode").isNumber()
                                ? "      {\"code\": %d, \"name\": %s}".formatted(row.path("sasboCode").asInt(),
                                        quote(row.path("name").asText()))
                                : "      {\"code\": null, \"under\": %d, \"name\": %s}".formatted(
                                        row.path("parent").asInt(), quote(row.path("name").asText())));
                    } else {
                        rows.add("      {\"code\": %d, \"name\": %s}".formatted(row.path("code").asInt(),
                                quote(row.path("name").asText())));
                    }
                }
            }
            return String.join(",\n", rows);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the seed resource " + ReferenceListSeeder.RESOURCE + " could not be read",
                    unreadable);
        }
    }

    /**
     * Month end's refusal codes, each with the status it answers (MD40) -- read off the enum the arc throws, never
     * typed out, so a code added on the server reaches the browser's parity test by regenerating this file.
     */
    private static String monthEndRefusalCodes() {
        return Arrays.stream(MonthEndRefusalCode.values())
                .map(c -> "      {\"code\": %s, \"status\": %d}".formatted(quote(c.name()), c.status().value()))
                .collect(Collectors.joining(",\n"));
    }

    /**
     * The ACB module's vocabulary: the states a run and a line can be in, the decisions an unpaid
     * can carry, why a member is left out of a run, and every refusal code with the status it
     * answers. Read off the enums the server throws and returns, never typed out, so a code added
     * on the server reaches the browser's parity test by regenerating this file.
     */
    private static String names(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .map(ApiSurface::quote)
                .collect(Collectors.joining(", "));
    }

    /** Each reason, and whether its members are listed by name or only counted. */
    private static String acbExclusionReasons() {
        return Arrays.stream(AcbExclusionReason.values())
                .map(r -> "      {\"name\": %s, \"listed\": %s}".formatted(quote(r.name()), r.listed()))
                .collect(Collectors.joining(",\n"));
    }

    /** Each refusal code, and the HTTP status it answers with. */
    private static String acbRefusalCodes() {
        return Arrays.stream(AcbRefusalCode.values())
                .map(c -> "      {\"code\": %s, \"status\": %d}".formatted(quote(c.name()), c.status().value()))
                .collect(Collectors.joining(",\n"));
    }

    /**
     * The job-grade matching rule, published so the browser cannot re-invent it
     * (02.5, ux.md 6c clause 1).
     *
     * <h2>Why the RULE is published and not just the values</h2>
     * This project carries four registered defects that are all one rule implemented
     * twice and left to drift -- A6, A7, A17, A9. The job-grade fallback needs both
     * halves to agree on what "the same grade" means: if the browser collapses double
     * spaces and the server does not, {@code "Grade  c"} matches in the form, is
     * treated as a brand-new grade on save, and the user is told it worked.
     *
     * <p>So these rows are not transcribed. Each is COMPUTED by calling
     * {@link JobGradeMatcher} -- the very code the server resolves with -- and the
     * browser's suite asserts its own implementation reproduces every one. Change the
     * rule on either side and the artifact goes stale or the browser guard reddens.
     *
     * <p>The inputs are the fixture from 6c clause 5, plus the whitespace and casing
     * shapes that are the actual drift risk.
     */
    private static String jobGradeMatchingCases() {
        List<String> inputs = List.of(
                "ba2", "BA2", "BA2 ", " ba2",
                "grade c", "Grade c", "Grade  c",
                "SBG4", "SBG04", "Prokureur", "  Senior   Clerk  ", "");

        return inputs.stream()
                .map(input -> "      {\"input\": %s, \"comparisonKey\": %s, \"storedForm\": %s, \"blank\": %s}"
                        .formatted(quote(input),
                                quote(JobGradeMatcher.comparisonKey(input)),
                                quote(JobGradeMatcher.storedForm(input)),
                                JobGradeMatcher.isBlank(input)))
                .collect(Collectors.joining(",\n"));
    }

    /**
     * 00.5 -- the account types, as the member record stores them and the capture screen offers them. Read off
     * the enum, so the browser's options are the server's list and never a second copy of it.
     */
    private static String accountTypeNames() {
        return Arrays.stream(AccountType.values())
                .map(AccountType::getDisplayName)
                .map(ApiSurface::quote)
                .collect(Collectors.joining(", "));
    }

    /**
     * A SASBO-structures vocabulary (V55) as the member record stores it: SASBO's spelling, in the
     * order of their structures tree. Read off the enum, so the record's checkboxes and options are
     * the server's lists and never a second copy of them.
     */
    private static <E extends Enum<E>> String displayNames(E[] values, Function<E, String> displayName) {
        return Arrays.stream(values)
                .map(displayName)
                .map(ApiSurface::quote)
                .collect(Collectors.joining(", "));
    }

    /**
     * The outcome names the server can report on a save. Read off the enum rather than
     * typed out, so adding a fourth outcome cannot leave the browser unaware of it.
     */
    private static String jobGradeOutcomeNames() {
        return Arrays.stream(JobGradeOutcome.values())
                .map(Enum::name)
                .map(ApiSurface::quote)
                .collect(Collectors.joining(", "));
    }

    /**
     * The keys an error response can carry, in the order the record declares
     * them -- which is the order they appear on the wire.
     *
     * <p>Declared components rather than a serialised sample, deliberately:
     * {@code ApiErrorResponse} is {@code @JsonInclude(NON_NULL)}, so a sample
     * omits {@code validationErrors} on every error that is not a validation
     * failure. The contract the browser needs is "the keys a response CAN
     * carry", which is the superset.
     */
    public static List<String> errorEnvelopeKeys() {
        return Arrays.stream(ApiErrorResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    /** One object on one line, through the application's own mapper. */
    private static String writeCompact(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialise " + value + " with the application's ObjectMapper. This "
                            + "artifact must carry what the endpoint actually sends, so it fails "
                            + "here rather than falling back to a hand-built copy.", e);
        }
    }

    /**
     * Jackson's own escaper, not a hand-rolled one.
     *
     * <p>A two-replacement escaper covers {@code \} and {@code "} and emits a
     * tab or a control character raw -- invalid JSON that the freshness test
     * then accepts happily, because the generator wrote it and live equals
     * committed. The browser is where it would fail.
     */
    private static String quote(String value) {
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(value)) + "\"";
    }

    // -----------------------------------------------------------------------
    // Reading and writing
    // -----------------------------------------------------------------------

    /**
     * The committed copy exactly as it sits on disk, or a message saying it is
     * missing.
     *
     * <p>A missing file is returned as content that cannot match rather than as
     * an exception, so the freshness test fails with "run this command" instead
     * of a stack trace from the file system.
     */
    public static String readCommitted(Path file) {
        if (!Files.isRegularFile(file)) {
            return "<< " + file + " does not exist. Run " + regenerateCommand() + " >>";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    /**
     * The committed copy with CRLF folded to LF, for the freshness comparison.
     *
     * <p><b>The two claims are separated at the read, not at the assertion</b>,
     * so each failure names its own cause. Comparing raw bytes made a CRLF
     * checkout redden the freshness test with "the browser's guard is checking
     * an API this server no longer has" -- about a change to nothing at all,
     * which is exactly the misdiagnosis the {@code .gitattributes} rule exists
     * to prevent. Line endings are asserted separately, against
     * {@link #readCommitted}, which still reads the bytes as they are.
     */
    public static String readCommittedNormalised(Path file) {
        return readCommitted(file).replace("\r\n", "\n");
    }

    /** Writes an artifact with LF endings, whatever platform is running. */
    public static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file.toAbsolutePath(), e);
        }
    }
}
