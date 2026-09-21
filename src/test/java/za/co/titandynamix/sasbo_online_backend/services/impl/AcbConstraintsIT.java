package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbHolidaySource;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🧪 R1-22, T2-B: every enum a {@code CHECK} mirrors agrees with PostgreSQL's OWN definition of it --
 * read back with {@code pg_get_constraintdef} and {@code pg_indexes.indexdef} after the whole chain,
 * never from the migration's text. The fast lane builds H2 from the entities, which carry no CHECK
 * at all, so this is the only place a value the enum gained and the CHECK did not -- or the reverse --
 * is seen before production refuses a row.
 *
 * <p>It reads the QUOTED LITERALS, since PostgreSQL rewrites {@code IN (...)} as
 * {@code = ANY (ARRAY[...])} (F60). It found its first drift before it ever ran: V42 admitted a
 * {@code RE_PRESENTED} outcome no code writes, removed in place (unpushed, F53).
 */
@DisplayName("Every CHECK an enum mirrors, as PostgreSQL defines it (R1-22)")
class AcbConstraintsIT extends AbstractAcbIT {

    static Stream<Arguments> mirroredChecks() {
        return Stream.of(
                Arguments.of("acb_runs_status_known", names(AcbRunStatus.values())),
                Arguments.of("acb_runs_declined_reason_known", names(AcbDeclineReason.values())),
                Arguments.of("acb_run_lines_outcome_known", names(AcbLineOutcome.values())),
                Arguments.of("acb_run_lines_decision_known", names(UnpaidDecision.values())),
                Arguments.of("acb_letters_status_known", names(LetterStatus.values())),
                Arguments.of("acb_letters_template_known", names(LetterTemplate.values())),
                Arguments.of("acb_letters_channel_known", names(LetterChannel.values())),
                Arguments.of("acb_holidays_source_known", names(AcbHolidaySource.values())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mirroredChecks")
    @DisplayName("the CHECK names exactly the enum's values")
    void theCheckIsTheEnum(String constraint, List<String> values) {
        assertThat(quotedLiterals(constraintDef(constraint))).containsExactlyInAnyOrderElementsOf(values);
    }

    @Test
    @DisplayName("the one-live-run index releases exactly the states that do not hold their month")
    void theIndexIsTheReleasedStates() {
        String index = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'acb_runs_one_live_run_per_month'",
                String.class);
        assertThat(quotedLiterals(index)).containsExactlyInAnyOrderElementsOf(names(Arrays.stream(AcbRunStatus.values())
                .filter(status -> !status.holdsItsMonth()).toArray(AcbRunStatus[]::new)));
    }

    @Test
    @DisplayName("canary: a deliberately wrong expectation fails, and the reader finds literals in PostgreSQL's own shape")
    void theComparisonCanFail() {
        List<String> oneShort = new ArrayList<>(names(AcbRunStatus.values()));
        oneShort.remove(0);
        assertThat(quotedLiterals(constraintDef("acb_runs_status_known"))).isNotEqualTo(oneShort)
                .hasSize(AcbRunStatus.values().length);
        assertThat(quotedLiterals("CHECK (((status)::text = ANY ((ARRAY['A'::character varying, 'B'::character varying])::text[])))"))
                .containsExactly("A", "B");
    }

    private String constraintDef(String name) {
        String definition = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class, name);
        assertThat(definition).as("the constraint %s exists", name).isNotBlank();
        return definition;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static List<String> quotedLiterals(String sql) {
        Matcher m = Pattern.compile("'([^']*)'").matcher(sql);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }
}
