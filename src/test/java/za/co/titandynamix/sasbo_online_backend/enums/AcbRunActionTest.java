package za.co.titandynamix.sasbo_online_backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.APPROVED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.CANCELLED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.CLOSED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.DECLINED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.FAILED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.PREPARED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.PREPARING;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.RETURNED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.SUBMITTED;

/**
 * The run's state machine, enumerated rather than listed.
 *
 * <p>Every (state, action) pair is generated from the two enums, and each must match the one table
 * below -- written out by hand, as a specification, so that adding a state or an action with no
 * decision about it fails here by name. A test that listed "the transitions that matter" would pass
 * over the pair nobody thought of, which is the pair that ships.
 */
@DisplayName("The run's state machine")
class AcbRunActionTest {

    /** The specification: for each action, the states it moves and where to. Nothing else moves. */
    private static final Map<AcbRunAction, Map<AcbRunStatus, AcbRunStatus>> ALLOWED = allowed();

    private static Map<AcbRunAction, Map<AcbRunStatus, AcbRunStatus>> allowed() {
        Map<AcbRunAction, Map<AcbRunStatus, AcbRunStatus>> table = new EnumMap<>(AcbRunAction.class);
        table.put(AcbRunAction.COMPLETE_PREPARATION, Map.of(PREPARING, PREPARED));
        table.put(AcbRunAction.FAIL_PREPARATION, Map.of(PREPARING, FAILED));
        table.put(AcbRunAction.APPROVE, Map.of(PREPARED, APPROVED));
        table.put(AcbRunAction.RECORD_SUBMITTED, Map.of(APPROVED, SUBMITTED));
        table.put(AcbRunAction.RECORD_RETURNS, Map.of(SUBMITTED, RETURNED));
        table.put(AcbRunAction.CLOSE, Map.of(RETURNED, CLOSED));
        table.put(AcbRunAction.RECORD_DECLINED, Map.of(SUBMITTED, DECLINED));
        table.put(AcbRunAction.CANCEL, Map.of(PREPARED, CANCELLED, APPROVED, CANCELLED));
        return table;
    }

    @Test
    @DisplayName("every (state, action) pair answers exactly what the table says -- all of them")
    void everyPairIsDecided() {
        int pairs = 0;
        for (AcbRunStatus from : AcbRunStatus.values()) {
            for (AcbRunAction action : AcbRunAction.values()) {
                Map<AcbRunStatus, AcbRunStatus> spec = ALLOWED.get(action);
                assertThat(spec).as("%s has no row in this test's table -- decide what it does", action)
                        .isNotNull();
                Optional<AcbRunStatus> expected = Optional.ofNullable(spec.get(from));
                assertThat(action.from(from)).as("%s from %s", action, from).isEqualTo(expected);
                pairs++;
            }
        }
        // Derived, never typed: a new state or action changes this product and the loop covers it.
        assertThat(pairs).isEqualTo(AcbRunStatus.values().length * AcbRunAction.values().length);
    }

    @Test
    @DisplayName("FAILED, CANCELLED and DECLINED are terminal: nothing moves a dead run")
    void deadRunsStayDead() {
        for (AcbRunAction action : AcbRunAction.values()) {
            assertThat(action.from(FAILED)).as("%s from FAILED", action).isEmpty();
            assertThat(action.from(CANCELLED)).as("%s from CANCELLED", action).isEmpty();
            assertThat(action.from(DECLINED)).as("%s from DECLINED", action).isEmpty();
        }
    }

    @Test
    @DisplayName("a submitted run is never cancelled -- its unpaids are the record")
    void aSubmittedRunCannotBeCancelled() {
        assertThat(AcbRunAction.CANCEL.from(SUBMITTED)).isEmpty();
        assertThat(AcbRunAction.CANCEL.from(RETURNED)).isEmpty();
        assertThat(AcbRunAction.CANCEL.from(CLOSED)).isEmpty();
        // The must-pass twins: a proposal is cancellable.
        assertThat(AcbRunAction.CANCEL.from(PREPARED)).contains(CANCELLED);
        assertThat(AcbRunAction.CANCEL.from(APPROVED)).contains(CANCELLED);
    }

    @Test
    @DisplayName("the states that release their month are exactly the ones V41's partial index excludes")
    void holdsItsMonthAgreesWithTheIndex() throws IOException {
        // Read from the migration itself, so the Java predicate and the database's cannot drift.
        Path v41 = Paths.get("src", "main", "resources", "db", "migration", "V41__acb_runs.sql");
        String sql = Files.readString(v41, StandardCharsets.UTF_8);
        Matcher where = Pattern.compile(
                "CREATE UNIQUE INDEX acb_runs_one_live_run_per_month\\s+ON acb_runs \\(action_month\\)\\s+"
                        + "WHERE status NOT IN \\(([^)]*)\\)").matcher(sql);
        assertThat(where.find()).as("the partial unique index in V41, in the shape this test reads").isTrue();
        Set<AcbRunStatus> releasedBySql = Arrays.stream(where.group(1).split(","))
                .map(s -> s.trim().replace("'", ""))
                .map(AcbRunStatus::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AcbRunStatus.class)));

        Set<AcbRunStatus> releasedByJava = Arrays.stream(AcbRunStatus.values())
                .filter(s -> !s.holdsItsMonth())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AcbRunStatus.class)));

        assertThat(releasedByJava).isEqualTo(releasedBySql).containsExactlyInAnyOrder(FAILED, CANCELLED, DECLINED);
    }

    @Test
    @DisplayName("lines are final once the file has gone to the bank, and not before")
    void linesAreFinalFromSubmission() {
        assertThat(EnumSet.allOf(AcbRunStatus.class).stream().filter(AcbRunStatus::linesAreFinal))
                .containsExactlyInAnyOrder(SUBMITTED, RETURNED, CLOSED, DECLINED);
    }
}
