package za.co.titandynamix.sasbo_online_backend.payroll;

/**
 * What a file IS, judged by its first bytes -- never by its name or the content type a browser claims (BRIEF §6: never
 * trust a file name or extension).
 */
public final class FileSniffer {

    /** The kinds a payroll upload can turn out to be. */
    public enum Kind {
        /** A zip container: an Office Open XML workbook, if it holds one (checked when it is read). */
        XLSX,
        /** An OLE2 container: an Excel 97-2003 workbook -- or a password-protected workbook of any age. */
        XLS,
        /** Text with no NUL byte in it: comma-, semicolon- or tab-separated values. */
        CSV,
        /** A PDF, like GBS's payment notice: the rows are typed in. */
        PDF,
        /** A picture, like SA Reserve Bank's: the rows are typed in. */
        PICTURE,
        /** Anything else. */
        UNKNOWN
    }

    private static final int TEXT_PROBE = 8192;

    private FileSniffer() {
    }

    public static Kind sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Kind.UNKNOWN;
        }
        if (startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)) {
            return Kind.XLSX;
        }
        if (startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return Kind.XLS;
        }
        if (startsWith(bytes, '%', 'P', 'D', 'F')) {
            return Kind.PDF;
        }
        if (startsWith(bytes, 0x89, 'P', 'N', 'G') || startsWith(bytes, 0xFF, 0xD8, 0xFF)
                || startsWith(bytes, 'G', 'I', 'F', '8') || startsWith(bytes, 'I', 'I', 0x2A, 0x00)
                || startsWith(bytes, 'M', 'M', 0x00, 0x2A)
                || (bytes.length >= 12 && startsWith(bytes, 'R', 'I', 'F', 'F') && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P')) {
            return Kind.PICTURE;
        }
        return looksLikeText(bytes) ? Kind.CSV : Kind.UNKNOWN;
    }

    private static boolean looksLikeText(byte[] bytes) {
        int probe = Math.min(bytes.length, TEXT_PROBE);
        for (int i = 0; i < probe; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0 || (b < 0x09) || (b > 0x0D && b < 0x20 && b != 0x1B)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
