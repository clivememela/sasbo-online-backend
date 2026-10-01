package za.co.titandynamix.sasbo_online_backend.support;

import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto.Row;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto.Sheet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SummaryFixture}'s September, as each statistic answers it (month end 04.3 to 04.8) -- the one table H2 and
 * PostgreSQL are both held to (MV-49). Rows that name no employer and no table code are asserted cell for cell; the
 * named ones are too small to answer (rule C), and are asserted hidden; every total is asserted whole, and equal to
 * the figures' own (MV-16).
 */
public final class StatisticsExpectations {

    private StatisticsExpectations() {
    }

    /** A sheet by its key. */
    public static Sheet sheet(MonthEndStatisticDto answer, String key) {
        return answer.sheets().stream().filter(s -> s.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no sheet " + key + " in " + answer.statistic()));
    }

    /** Each row as {@code key: cells}, the cells as written -- {@code null}, {@code hidden} where rule C hid the row. */
    public static List<String> rows(Sheet sheet) {
        List<String> out = new ArrayList<>();
        for (Row row : sheet.rows()) {
            out.add(row.key() + ": " + (row.cells() == null ? (row.fewerThanFive() ? "fewer than five" : "withheld")
                    : written(row.cells())));
        }
        return out;
    }

    public static String total(Sheet sheet) {
        return written(sheet.total().cells());
    }

    private static String written(List<BigDecimal> cells) {
        List<String> out = new ArrayList<>();
        for (BigDecimal cell : cells) {
            out.add(cell == null ? "-" : cell.toPlainString());
        }
        return String.join(" ", out);
    }

    /** 04.3: September's new and deleted, by area, core and other. */
    public static void assertNewAndDeleted(MonthEndStatisticDto answer) {
        assertThat(answer.statistic()).isEqualTo("new-and-deleted");
        assertThat(answer.sheets()).extracting(Sheet::key)
                .containsExactly("month-joined", "month-left", "year-joined", "year-left");
        assertThat(rows(sheet(answer, "month-joined")))
                .containsExactly("A: 1 0 1", "B: 1 0 1", "~NO_WORKPLACE: - - 0");
        assertThat(total(sheet(answer, "month-joined"))).as("MV-16: the summary's joined").isEqualTo("2 0 2");
        assertThat(rows(sheet(answer, "month-left")))
                .containsExactly("A: 0 0 0", "B: 2 0 2", "~NO_WORKPLACE: - - 0");
        assertThat(total(sheet(answer, "month-left"))).as("MV-16: the summary's left").isEqualTo("2 0 2");
        assertThat(sheet(answer, "month-joined").rows().get(0).label()).isEqualTo("A - INVENTED NORTH");
    }

    /** 04.4: membership by group, by table code, and the suspended by code. */
    public static void assertMembership(MonthEndStatisticDto answer) {
        Sheet byTableCode = sheet(answer, "by-table-code");
        assertThat(rows(byTableCode)).as("901 holds eleven, 902 three: 902 is fewer than five, and 901 is withheld "
                + "beside it -- as the summary answers them").containsExactly("901: withheld", "902: fewer than five",
                "~NO_TABLE_CODE: 1 1 0 0.00 0 0.00 0 0.00 1 0.00 3",
                "~NO_WORKPLACE: 2 2 0 0.00 0 0.00 0 0.00 2 0.00 0");
        assertThat(total(byTableCode)).as("MV-16: on record 17, paying 16, suspended 1, complement 38 -- the summary's")
                .isEqualTo("17 16 1 0.00 0 0.00 0 0.00 16 0.00 38");
        Sheet byGroup = sheet(answer, "by-group");
        assertThat(rows(byGroup)).as("a group is guarded as its employers are; its employers hide with it, and no "
                + "core line gives a withheld group back").containsExactly("G1: withheld", "E1: withheld",
                "G2: fewer than five", "E2: fewer than five", "~NO_TABLE_CODE: 3 1 0 0 0", "~NO_WORKPLACE: 0 2 0 0 0");
        assertThat(total(byGroup)).isEqualTo("38 16 2 2 1");
        assertThat(rows(sheet(answer, "suspended-by-code"))).containsExactly("~NO_CODE: 1");
        assertThat(sheet(answer, "suspended-by-code").rows().get(0).label()).isEqualTo("No code on record");
    }

    /** 04.5: why members left. */
    public static void assertLeavers(MonthEndStatisticDto answer) {
        Sheet byArea = sheet(answer, "by-area");
        assertThat(byArea.columns()).extracting(MonthEndStatisticDto.Column::key)
                .containsExactly("code1", "code2", "code5", "noCode", "total");
        assertThat(byArea.columns().get(1).label()).isEqualTo("Resigned Society (2)");
        assertThat(rows(byArea)).containsExactly("A: 0 0 0 0 0", "B: 0 1 1 0 2");
        assertThat(total(byArea)).isEqualTo("0 1 1 0 2");
        // 04.5 "by area and by employer": the employers' rows are settled by their membership, as the branches' are.
        assertThat(rows(sheet(answer, "by-employer"))).containsExactly("E1: withheld", "E2: fewer than five");
        assertThat(total(sheet(answer, "by-employer"))).as("MV-16: the same leavers as by area").isEqualTo("0 1 1 0 2");
        assertThat(rows(sheet(answer, "by-table-code"))).containsExactly("901: withheld", "902: fewer than five");
        assertThat(total(sheet(answer, "by-table-code"))).isEqualTo("0 1 1 0 2");
        assertThat(total(sheet(answer, "joined-deleted"))).isEqualTo("2 2 0");
        assertThat(rows(sheet(answer, "resigned"))).as("the one who resigned stands at INVENTED BANK, of eleven")
                .containsExactly("E1: 1 1");
    }

    /** 04.6: the branches and their penetration. */
    public static void assertBranches(MonthEndStatisticDto answer) {
        Sheet byArea = sheet(answer, "by-area");
        assertThat(rows(byArea)).containsExactly("A: 30 11 0.3667 0.00 20 8", "B: 8 3 0.3750 0.00 5 2",
                "~NO_WORKPLACE: 0 2 - 0.00 0 0");
        assertThat(total(byArea)).as("MV-16: the summary's penetration, 16 over 38").isEqualTo("38 16 0.4211 0.00 25 10");
        assertThat(rows(sheet(answer, "by-employer"))).containsExactly("E1: withheld", "E2: fewer than five",
                "~NO_TABLE_CODE: 3 1 0.3333 0.00 0 0", "~NO_WORKPLACE: 0 2 - 0.00 0 0");
    }

    /** 04.7: recruitment -- September's two recruits, both at the core bank, with no recruiter and no channel on record. */
    public static void assertRecruitment(MonthEndStatisticDto answer, long yearToDate) {
        assertThat(rows(sheet(answer, "by-recruiter")))
                .containsExactly("~NO_RECRUITER: 2 " + yearToDate + " 0 0 2 " + yearToDate);
        assertThat(rows(sheet(answer, "by-channel"))).containsExactly("~NO_CHANNEL: 2 " + yearToDate);
        assertThat(rows(sheet(answer, "by-table-code"))).containsExactly("901: 2 1.0000 " + yearToDate + " 1.0000");
    }

    /**
     * The figures printed in two statistics or more, each where it stands -- the statistic, its sheet, the column of
     * its total. A figure added to a second statistic is added here, or it is a second figure by a name it shares.
     */
    public static final Map<String, List<String>> ONE_FIGURE = new LinkedHashMap<>();

    static {
        ONE_FIGURE.put("joined in the month", List.of("new-and-deleted month-joined total", "membership by-group joined",
                "leavers joined-deleted joined", "recruitment by-recruiter month", "recruitment by-channel month",
                "recruitment by-table-code month"));
        ONE_FIGURE.put("left in the month", List.of("new-and-deleted month-left total", "membership by-group left",
                "leavers by-area total", "leavers by-employer total", "leavers by-table-code total",
                "leavers joined-deleted left"));
        ONE_FIGURE.put("resigned in the month", List.of("leavers by-area code2", "leavers resigned total"));
        ONE_FIGURE.put("joined in the year", List.of("new-and-deleted year-joined total", "recruitment by-recruiter year",
                "recruitment by-channel year", "recruitment by-table-code year"));
        ONE_FIGURE.put("members, the suspended not among them", List.of("membership by-group members",
                "membership by-table-code members", "branches by-area members", "branches by-employer members"));
        ONE_FIGURE.put("the suspended", List.of("membership by-group suspended", "membership by-table-code suspended",
                "membership suspended-by-code members"));
        ONE_FIGURE.put("the complement", List.of("membership by-group complement", "membership by-table-code complement",
                "branches by-area complement", "branches by-employer complement"));
        ONE_FIGURE.put("the subscriptions", List.of("membership by-table-code subscriptions",
                "branches by-area subscriptions", "branches by-employer subscriptions"));
        ONE_FIGURE.put("the workplaces", List.of("representatives representatives workplaces",
                "representatives committee-members workplaces"));
    }

    /** P4: a figure printed in two statistics is one figure -- the same wherever it stands. */
    public static void assertOneFigure(List<MonthEndStatisticDto> all) {
        for (Map.Entry<String, List<String>> figure : ONE_FIGURE.entrySet()) {
            List<String> read = new ArrayList<>();
            Set<String> values = new LinkedHashSet<>();
            for (String place : figure.getValue()) {
                String[] at = place.split(" ");
                MonthEndStatisticDto answer = all.stream().filter(a -> a.statistic().equals(at[0])).findFirst()
                        .orElseThrow(() -> new AssertionError("no statistic " + at[0]));
                Sheet sheet = sheet(answer, at[1]);
                int column = -1;
                for (int c = 0; c < sheet.columns().size(); c++) {
                    if (sheet.columns().get(c).key().equals(at[2])) {
                        column = c;
                    }
                }
                assertThat(column).as("%s: the column %s", place, at[2]).isNotNegative();
                BigDecimal cell = sheet.total().cells().get(column);
                String value = cell == null ? "-" : cell.stripTrailingZeros().toPlainString();
                read.add(place + " = " + value);
                values.add(value);
            }
            assertThat(values).as("%s is one figure wherever it is printed: %s", figure.getKey(), read).hasSize(1);
        }
    }

    /** 04.8: representatives and committee members -- none on record in the fixture. */
    public static void assertRepresentatives(MonthEndStatisticDto answer) {
        Sheet reps = sheet(answer, "representatives");
        assertThat(rows(reps)).containsExactly("901: withheld", "902: fewer than five",
                "~NO_TABLE_CODE: 0 1 0 1 0.0000", "~NO_WORKPLACE: 0 0 0 0 -");
        assertThat(total(reps)).as("four workplaces, none with a representative").isEqualTo("0 4 0 4 0.0000");
        Sheet committees = sheet(answer, "committee-members");
        assertThat(rows(committees)).containsExactly("901: withheld", "902: fewer than five",
                "~NO_TABLE_CODE: 0 1 0 1 0.0000", "~NO_WORKPLACE: 0 0 0 0 -");
        assertThat(total(committees)).as("four workplaces, none with a committee member").isEqualTo("0 4 0 4 0.0000");
        assertThat(answer.notes()).anyMatch(n -> n.contains("Memnet"));
    }
}
