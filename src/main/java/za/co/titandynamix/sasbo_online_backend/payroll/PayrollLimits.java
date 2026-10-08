package za.co.titandynamix.sasbo_online_backend.payroll;

import java.math.BigDecimal;

/**
 * The payroll module's hard limits, in one place -- published to the client in the vocabulary, so the screen refuses
 * what the server would before anything is sent.
 *
 * <p>Sized for SASBO: the largest employer file is a few thousand lines today (65,600 members across sixteen
 * employers); 20,000 lines leaves room for growth and still reads in seconds. 10 MB is several times the largest
 * spreadsheet that many lines makes.
 */
public final class PayrollLimits {

    /** The largest file an upload may carry: 10 MB. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** The most lines a file may carry. */
    public static final int MAX_ROWS = 20_000;

    /** The most rows a typed entry may carry: typed by hand, from a letter or a picture. */
    public static final int MAX_TYPED_ROWS = 5_000;

    /** How far down a sheet the row of headings is looked for (African Bank's is row 7, under a title block). */
    public static final int HEADING_SCAN_ROWS = 40;

    /** The columns read from a sheet; anything to the right of these is no payroll field. */
    public static final int MAX_COLUMNS = 60;

    /** The longest text kept from one cell. */
    public static final int MAX_CELL_LENGTH = 300;

    /** The longest note. */
    public static final int NOTE_MAX_LENGTH = 1000;

    /** The largest page a list answers. */
    public static final int PAGE_MAX_SIZE = 100;

    /** The most months one larger amount is read as catching up (PAY-25, PAY-26). */
    public static final int MAX_CATCH_UP_MONTHS = 12;

    /** More than this on one line is no subscription a member could owe: the line is not read. */
    public static final BigDecimal MAX_LINE_AMOUNT = new BigDecimal("100000.00");

    /** How long a file and its lines are kept (PAY-39: "One year"). */
    public static final int RETAIN_YEARS = 1;

    /** How many months back the history of missed months and catch-ups is read. */
    public static final int HISTORY_MONTHS = 12;

    private PayrollLimits() {
    }
}
