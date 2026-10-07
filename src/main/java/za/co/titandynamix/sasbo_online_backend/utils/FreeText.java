package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * Words an administrator types -- a status change's note, the reason a month was taken again, a reason's name on a
 * list -- held to what such words are: ordinary text, of a length the column holds.
 *
 * <ul>
 *   <li>Trimmed, and blank is none;</li>
 *   <li>counted as PostgreSQL counts a {@code VARCHAR}: by character (code point), never by Java's UTF-16 unit, so a
 *       limit here is the column's limit there;</li>
 *   <li>no control characters -- none of the C0 or C1 set, where a note may keep its line breaks and a name may not;
 *       none of the characters that reverse or isolate the direction text is shown in, which can make a note read as
 *       something else on the screen; no line or paragraph separator; and no half of a broken character, which the
 *       database would refuse outright.</li>
 * </ul>
 * What was typed is never given back in a refusal: the caller names the field and the limit (ME-14).
 */
public final class FreeText {

    /**
     * How long a status change's note may be: 500 characters, the column's own since V56. SASBO asked at the 5 October
     * Show &amp; Tell -- "And how of our characters?" (transcript ¶47@30) -- and were told only "free text"; this is the
     * answer, stated.
     */
    public static final int NOTE_LIMIT = 500;

    /** Why a text was refused. */
    public enum Problem {
        /** Longer than its limit, in characters. */
        TOO_LONG,
        /** It holds a control character, a direction control or a broken character. */
        CONTROL_CHARACTERS
    }

    private FreeText() {
    }

    /** The text trimmed, or {@code null} where it is none or blank. */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * What is wrong with {@code text}, or {@code null} where nothing is.
     *
     * @param text       the text as it will be stored -- {@link #clean cleaned} first
     * @param limit      the most characters it may hold
     * @param lineBreaks whether a line break ({@code \n}, {@code \r}) is part of ordinary text here
     */
    public static Problem problem(String text, int limit, boolean lineBreaks) {
        if (text == null) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > limit) {
            return Problem.TOO_LONG;
        }
        return text.codePoints().anyMatch(c -> refused(c, lineBreaks)) ? Problem.CONTROL_CHARACTERS : null;
    }

    private static boolean refused(int c, boolean lineBreaks) {
        if (c == '\n' || c == '\r') {
            return !lineBreaks;
        }
        return Character.getType(c) == Character.CONTROL
                || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069)
                || c == 0x2028 || c == 0x2029
                || (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE);
    }
}
