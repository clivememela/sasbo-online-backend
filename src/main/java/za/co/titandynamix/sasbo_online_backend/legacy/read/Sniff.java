package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A look at a file's first bytes, before any parser sees it: its name and extension are whatever its sender typed, so
 * the bytes decide. Text is refused when it is a workbook, a PDF, or binary (a NUL byte, in an encoding that has none);
 * a workbook is refused when it is not a zip.
 */
final class Sniff {

    /** How much of a file is looked at. */
    static final int HEAD = 8192;

    private Sniff() {
    }

    static byte[] head(BufferedInputStream in) throws IOException {
        in.mark(HEAD + 1);
        byte[] head = in.readNBytes(HEAD);
        in.reset();
        return head;
    }

    static boolean isZip(byte[] head) {
        return head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }

    static boolean isPdf(byte[] head) {
        return head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
    }

    /** UTF-16 and UTF-32 put NUL bytes in ordinary text; every other encoding a legacy export uses does not. */
    static boolean nulIsBinary(Charset charset) {
        String name = charset.name().toUpperCase(java.util.Locale.ROOT);
        return !name.startsWith("UTF-16") && !name.startsWith("UTF-32");
    }

    static void requireText(BufferedInputStream in, Charset charset) throws IOException {
        byte[] head = head(in);
        if (isZip(head)) {
            throw new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                    "The file is a workbook or another zip file, not text. Choose a mapping for a workbook, or save it as text.");
        }
        if (isPdf(head)) {
            throw new SourceRefused(SourceRefused.Reason.NOT_READABLE, "The file is a PDF, not text: it cannot be imported.");
        }
        if (nulIsBinary(charset)) {
            for (byte b : head) {
                if (b == 0) {
                    throw new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                            "The file is not text in the mapping's encoding: it holds binary data.");
                }
            }
        }
    }

    static void requireWorkbook(BufferedInputStream in) throws IOException {
        if (!isZip(head(in))) {
            throw new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                    "The file is not an .xlsx workbook. Choose a mapping for text, or save it as a workbook.");
        }
    }
}
