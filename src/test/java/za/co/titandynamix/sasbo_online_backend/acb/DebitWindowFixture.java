package za.co.titandynamix.sasbo_online_backend.acb;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders {@code debit-window.json}: every (month, debit day, holiday set) cell of the fixture's
 * range, computed by {@link DebitWindow} -- the production rule, never a second implementation.
 *
 * <h2>Why an artefact at all</h2>
 * The client computes the same dates in its own language. Two implementations that agree can both
 * be wrong, and on 17 September they were: the client and this arc's plan disagreed on 56 of 288
 * cells while every worked example anybody had written a test for agreed. So the two are never
 * compared with each other. Each is compared with THIS file, and the merged gate compares the two
 * committed copies byte for byte.
 *
 * <h2>The generator's contract</h2>
 * It takes no clock -- the range is constants, so the bytes never change by themselves at a month
 * boundary. It asserts the rule's invariants over every cell BEFORE it renders anything, so a
 * broken rule can never be published as an agreed one. And its bytes are deterministic: fixed key
 * order, two-space indentation, {@code \n}, one final newline.
 */
public final class DebitWindowFixture {

    /** At the project root, beside {@code api-surface.txt}, and for the same reason. */
    public static final Path FILE = Paths.get("debit-window.json");

    /** The rule under test, so the generator's own refusal can be proved with a broken one. */
    @FunctionalInterface
    public interface Rule {
        LocalDate actionDate(HolidayCalendar set, YearMonth month, int debitDay);
    }

    /** The production rule. */
    public static final Rule PRODUCTION = (set, month, day) -> new DebitWindow(set).actionDate(month, day);

    private DebitWindowFixture() {
    }

    /** The fixture's bytes for the production rule. */
    public static String render() {
        return render(PRODUCTION, DebitWindow.ruleName());
    }

    /**
     * The fixture's bytes for {@code rule}, after checking every cell.
     *
     * @throws IllegalStateException naming the first cell that breaks an invariant -- nothing is
     *         rendered for a rule that fails one
     */
    public static String render(Rule rule, String ruleName) {
        List<String> rows = new ArrayList<>();
        for (YearMonth month : AcbCalendars.months()) {
            for (HolidayCalendar set : AcbCalendars.SETS) {
                List<String> days = new ArrayList<>();
                for (int day = DebitWindow.FIRST_DAY; day <= DebitWindow.LAST_DAY; day++) {
                    LocalDate result = rule.actionDate(set, month, day);
                    requireInvariants(set, month, day, result);
                    days.add(String.valueOf(result.getDayOfMonth()));
                }
                rows.add("    { \"month\": \"" + month + "\", \"set\": \"" + set.name()
                        + "\", \"days\": [" + String.join(", ", days) + "] }");
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"rule\": \"").append(ruleName).append("\",\n");
        json.append("  \"window\": { \"firstDay\": ").append(DebitWindow.FIRST_DAY)
                .append(", \"lastDay\": ").append(DebitWindow.LAST_DAY).append(" },\n");
        json.append("  \"zone\": \"").append(AcbTime.ZONE.getId()).append("\",\n");
        json.append("  \"from\": \"").append(AcbCalendars.FROM).append("\",\n");
        json.append("  \"to\": \"").append(AcbCalendars.TO).append("\",\n");
        json.append("  \"holidaySets\": {\n");
        List<String> sets = new ArrayList<>();
        for (HolidayCalendar set : AcbCalendars.SETS) {
            String knownUntil = set.knownUntil().map(d -> "\"" + d + "\"").orElse("null");
            String dates = set.dates().stream().map(d -> "\"" + d + "\"").collect(Collectors.joining(", "));
            sets.add("    \"" + set.name() + "\": { \"knownUntil\": " + knownUntil
                    + ", \"dates\": [" + dates + "] }");
        }
        json.append(String.join(",\n", sets)).append("\n");
        json.append("  },\n");
        json.append("  \"months\": [\n");
        json.append(String.join(",\n", rows)).append("\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * The five invariants, over one cell. Any reading of the rule SASBO might confirm satisfies all
     * five -- which is why they are asserted here rather than a direction.
     */
    static void requireInvariants(HolidayCalendar set, YearMonth month, int day, LocalDate result) {
        String cell = set.name() + " " + month + " day " + day + " -> " + result;
        if (!YearMonth.from(result).equals(month)) {
            throw new IllegalStateException(cell + ": left its month");
        }
        if (result.getDayOfMonth() < DebitWindow.FIRST_DAY) {
            throw new IllegalStateException(cell + ": fell below the 20th");
        }
        if (result.getDayOfMonth() > month.lengthOfMonth()) {
            throw new IllegalStateException(cell + ": passed the month's end");
        }
        if (!set.isBusinessDay(result)) {
            throw new IllegalStateException(cell + ": is not a business day");
        }
        LocalDate asked = month.atDay(Math.min(day, month.lengthOfMonth()));
        if (set.isBusinessDay(asked) && !result.equals(asked)) {
            throw new IllegalStateException(cell + ": moved a day that was already a business day");
        }
    }
}
