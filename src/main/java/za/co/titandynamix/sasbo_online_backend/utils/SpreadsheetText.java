package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * Text a spreadsheet will not read as a formula (month end 04.10, MV-13). A cell whose text begins with one of the
 * {@value #FIRST_CHARACTERS_COUNT} first characters a spreadsheet takes as the start of a formula or of a DDE call --
 * {@code =}, {@code +}, {@code -}, {@code @}, a tab, a carriage return -- is written with an apostrophe before it, so
 * it is shown as the text it is. Every text a month-end workbook writes passes through here: a label is data, and data
 * is typed by whoever captured it.
 */
public final class SpreadsheetText {

    /** The first characters a spreadsheet reads as the start of a formula. */
    public static final String FIRST_CHARACTERS = "=+-@\t\r";

    /** How many there are -- six. */
    public static final int FIRST_CHARACTERS_COUNT = 6;

    private SpreadsheetText() {
    }

    /** {@code text} as a spreadsheet will show it -- never run it. */
    public static String safe(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return FIRST_CHARACTERS.indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }
}
