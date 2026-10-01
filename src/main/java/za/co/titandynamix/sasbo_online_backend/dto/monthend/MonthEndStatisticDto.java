package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.math.BigDecimal;
import java.util.List;

/**
 * One statistic of a month (month end 04.3 to 04.8): its sheets, each a table of counts -- the screen renders them
 * as they come, and the statistics pack writes the same sheets. Every sheet is worked out from the month's facts
 * when it is asked for, never stored (MD43): a taken month's from what was frozen, the open month's as it stands.
 * Dates are ISO text (MD16).
 *
 * @param statistic        the statistic's name, as its route spells it
 * @param legacy           the legacy reports it answers, by their file names
 * @param month            the month, {@code 2026-08}
 * @param state            {@code TAKEN} or {@code OPEN}
 * @param takenAt          when the month was taken, ISO-8601; {@code null} while open
 * @param takenBy          who took it; {@code null} while open
 * @param area             the area asked for, or {@code null}
 * @param region           the region asked for, or {@code null}
 * @param workplacesServed what the deployment's switch says (MD62): below {@code ALL} a sheet cut by where members
 *                         work is the totals and one row, and nothing made from a staff number is answered
 * @param sheets           the statistic's sheets, in SASBO's order
 * @param notes            what a reader must know to read them -- one sentence each
 */
public record MonthEndStatisticDto(String statistic, List<String> legacy, String month, String state, String takenAt,
                                   String takenBy, String area, String region, String workplacesServed,
                                   List<Sheet> sheets, List<String> notes) {

    /**
     * One table.
     *
     * @param key         the sheet's name within its statistic
     * @param title       its plain title
     * @param legacyTitle the title SASBO's report prints, until SASBO chooses the plain names (MD8, PA-13)
     * @param columns     its columns, in order
     * @param rows        its rows, in order
     * @param total       the sheet's total -- never suppressed; {@code null} where a total says nothing
     */
    public record Sheet(String key, String title, String legacyTitle, List<Column> columns, List<Row> rows,
                        Row total) {
    }

    /**
     * One column.
     *
     * @param key   its name
     * @param label what the screen prints
     * @param kind  {@value #COUNT}, {@value #RAND} or {@value #SHARE}
     */
    public record Column(String key, String label, String kind) {

        /** A number of members, of workplaces or of staff. */
        public static final String COUNT = "COUNT";

        /** An amount of money, in rand, to the cent. */
        public static final String RAND = "RAND";

        /** A share of one, to four places -- a penetration. */
        public static final String SHARE = "SHARE";
    }

    /**
     * One row.
     *
     * @param key           its key -- a letter, a code, a number, or one of {@code ~}'s own
     * @param label         what the screen prints
     * @param level         {@value #ROW}, {@value #GROUP} (a heading that sums the rows under it) or {@value #TOTAL}
     * @param fewerThanFive a membership of one to four at a named employer, table code or workplace (rule C) -- no
     *                      number on the row
     * @param withheld      withheld so that no answer gives a row of fewer than five back
     * @param cells         one a column, in the columns' order; {@code null} where the row answers none, or where the
     *                      cell has no meaning -- a penetration with no complement, core and other with no workplace
     */
    public record Row(String key, String label, String level, boolean fewerThanFive, boolean withheld,
                      List<BigDecimal> cells) {

        public static final String ROW = "ROW";
        public static final String GROUP = "GROUP";
        public static final String TOTAL = "TOTAL";
    }
}
