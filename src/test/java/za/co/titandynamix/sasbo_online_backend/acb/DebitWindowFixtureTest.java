package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.nio.file.Files;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code debit-window.json} is exactly what the production rule generates -- and the generator
 * refuses to publish a rule that breaks an invariant.
 *
 * <p>The generator and the checker are this one class, run two ways, like the API surface:
 * {@code ./gradlew generateApiSurface} writes the file ({@code -DapiSurface.write=true}), and the
 * fast lane asserts the committed bytes equal what would be written. Two implementations of one
 * format drift, and the symptom is a freshness test failing on a file the generator just wrote.
 *
 * <p>The client carries a copy of this file and asserts its own implementation reproduces every
 * cell. Regenerating it here is half the change; copying it into the client is the other half.
 */
@DisplayName("debit-window.json -- the window rule as one artefact")
class DebitWindowFixtureTest {

    @Test
    @DisplayName("the committed fixture is exactly what the production rule generates")
    void theCommittedFixtureIsWhatTheRuleGenerates() {
        String live = DebitWindowFixture.render();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(DebitWindowFixture.FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(DebitWindowFixture.FILE))
                .as("%s is STALE: the window rule changed and the fixture the client is tested "
                        + "against did not. Regenerate with %s and copy the file to the client",
                        DebitWindowFixture.FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("the committed fixture carries LF endings, on every platform")
    void theFixtureHasUnixEndings() {
        assertThat(Files.isRegularFile(DebitWindowFixture.FILE))
                .as("%s does not exist. Run %s", DebitWindowFixture.FILE, ApiSurface.regenerateCommand())
                .isTrue();
        assertThat(ApiSurface.readCommitted(DebitWindowFixture.FILE)).doesNotContain("\r");
    }

    @Test
    @DisplayName("names the rule it encodes, and covers 24 months in two holiday sets")
    void theFixtureSaysWhatItIs() {
        String live = DebitWindowFixture.render();
        assertThat(live).contains("\"rule\": \"" + DebitWindow.ruleName() + "\"");
        // One row per month per set, derived rather than typed.
        long rows = live.lines().filter(l -> l.contains("\"days\": [")).count();
        assertThat(rows).isEqualTo(AcbCalendars.months().size() * AcbCalendars.SETS.size()).isEqualTo(48);
        // W6, the cell that separates the two readings: days 25 and 26 of July 2026 fall on the
        // weekend and are debited on Friday the 24th. Re-derived by the planning pack's own script,
        // not typed from memory -- the first draft of this line had it wrong.
        assertThat(live).contains("{ \"month\": \"2026-07\", \"set\": \"weekends-only\", "
                + "\"days\": [20, 21, 22, 23, 24, 24, 24, 27, 28, 29, 30, 31] }");
    }

    @Test
    @DisplayName("refuses to render a rule that breaks an invariant -- nothing is published for it")
    void theGeneratorRefusesABrokenRule() {
        // A rule that ignores weekends entirely: Saturday 25 July 2026 stays the 25th.
        DebitWindowFixture.Rule ignoresWeekends = (set, month, day) ->
                month.atDay(Math.min(day, month.lengthOfMonth()));
        assertThatThrownBy(() -> DebitWindowFixture.render(ignoresWeekends, "broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not a business day");

        // A rule that falls below the window: always the 19th.
        DebitWindowFixture.Rule below = (set, month, day) -> month.atDay(19);
        assertThatThrownBy(() -> DebitWindowFixture.render(below, "broken"))
                .isInstanceOf(IllegalStateException.class);

        // The must-pass twin: the production rule renders.
        assertThat(DebitWindowFixture.render(DebitWindowFixture.PRODUCTION, "production")).isNotBlank();
    }

    @Test
    @DisplayName("an invariant checker that cannot see a bad cell would pass anything -- it can")
    void theInvariantCheckSeesEachFailure() {
        HolidayCalendar weekends = HolidayCalendar.WEEKENDS_ONLY;
        java.time.YearMonth july = java.time.YearMonth.of(2026, 7);
        assertThatThrownBy(() -> DebitWindowFixture.requireInvariants(weekends, july, 25, LocalDate.of(2026, 8, 3)))
                .hasMessageContaining("left its month");
        assertThatThrownBy(() -> DebitWindowFixture.requireInvariants(weekends, july, 20, LocalDate.of(2026, 7, 17)))
                .hasMessageContaining("below the 20th");
        assertThatThrownBy(() -> DebitWindowFixture.requireInvariants(weekends, july, 22, LocalDate.of(2026, 7, 21)))
                .hasMessageContaining("already a business day");
    }
}
