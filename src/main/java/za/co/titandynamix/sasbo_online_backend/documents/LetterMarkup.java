package za.co.titandynamix.sasbo_online_backend.documents;

import java.util.ArrayList;
import java.util.List;

/**
 * A letter's text, read into the blocks its pages are laid out from.
 *
 * <h2>The text is the letter</h2>
 * A filled letter is plain text -- what is hashed when it is recorded and compared before it is sent (03.3) -- and its
 * pages are a function of that text alone. A line's first characters say what it is:
 * <pre>
 *   &#64;date 14 October 2026                the date
 *   &#64;to Ms Z Exemplé                     a line of the member's address (a blank one is left out)
 *   &#64;ref Membership number | M-000000    a reference: label, then value
 *   # Your debit order ...               the subject
 *   ## What you need to do               a heading
 *   | Amount | R174.00                   a row of the table (rows in a row are one table)
 *   - Please make sure ...               a point (a plain line straight after one continues it)
 *   &#64;sign Membership Administration      the signing position (the first line of the block in bold)
 *   &#64;specimen ...                        the band that marks a specimen
 * </pre>
 * Any other line belongs to a paragraph, joined to the lines next to it; a blank line ends a paragraph.
 *
 * <h2>🔒 A member's details can never become layout</h2>
 * Every placeholder is filled in the middle of a line -- {@code LetterRenderer} refuses a template line that begins
 * with one -- and a line break or any other control character inside a value becomes a space. So no surname or
 * address can begin a line, and only a template's own words say what a line is.
 */
public final class LetterMarkup {

    private LetterMarkup() {
    }

    /** A letter's subject and its blocks, in order. */
    public record Letter(String subject, List<Block> blocks) {
    }

    /** One block of a letter. */
    public sealed interface Block permits Specimen, DateLine, Recipient, References, Subject, Heading, Paragraph,
            Table, Points, Signature {
    }

    /** The band across the top of a specimen. */
    public record Specimen(String text) implements Block {
    }

    /** The letter's date. */
    public record DateLine(String text) implements Block {
    }

    /** The member's name and address, line by line. */
    public record Recipient(List<String> lines) implements Block {
    }

    /** The references beside the address -- the membership number. */
    public record References(List<Row> rows) implements Block {
    }

    /** What the letter is about, in a line. */
    public record Subject(String text) implements Block {
    }

    /** A heading inside the letter. */
    public record Heading(String text) implements Block {
    }

    /** A paragraph. */
    public record Paragraph(String text) implements Block {
    }

    /** The small table of the amount and the dates. */
    public record Table(List<Row> rows) implements Block {
    }

    /** A list of points: what the member must do. */
    public record Points(List<String> items) implements Block {
    }

    /** The signing position and the union's name -- never a person's name (Stephne, DOD-35). */
    public record Signature(List<String> lines) implements Block {
    }

    /** A label and its value. */
    public record Row(String label, String value) {
    }

    /** {@code text} read into its blocks. */
    public static Letter parse(String text) {
        Parser parser = new Parser();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            parser.line(spaced(line));
        }
        return parser.finish();
    }

    /** The letter's subject line, or {@code null} when it has none. */
    public static String subjectOf(String text) {
        return parse(text).subject();
    }

    /** Every control character as a space: a tab in a template is a space, never a layout instruction. */
    private static String spaced(String line) {
        StringBuilder out = new StringBuilder(line.length());
        line.codePoints().forEach(cp -> out.appendCodePoint(Character.isISOControl(cp) ? ' ' : cp));
        return out.toString();
    }

    private enum Kind { NONE, PARAGRAPH, RECIPIENT, REFERENCES, TABLE, POINTS, SIGNATURE }

    /** Reads lines into blocks, gathering the lines of the block in hand until a line starts another. */
    private static final class Parser {

        private final List<Block> blocks = new ArrayList<>();
        private final List<String> lines = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private final StringBuilder paragraph = new StringBuilder();
        private Kind open = Kind.NONE;
        private String subject;

        void line(String line) {
            if (line.isBlank()) {
                close();
            } else if (line.startsWith("@")) {
                directive(line);
            } else if (line.startsWith("## ")) {
                close();
                blocks.add(new Heading(line.substring(3).strip()));
            } else if (line.startsWith("# ")) {
                close();
                String text = line.substring(2).strip();
                if (subject == null) {
                    subject = text;
                }
                blocks.add(new Subject(text));
            } else if (line.startsWith("| ")) {
                gather(Kind.TABLE);
                rows.add(row(line.substring(2)));
            } else if (line.startsWith("- ")) {
                gather(Kind.POINTS);
                lines.add(line.substring(2).strip());
            } else if (open == Kind.POINTS) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + " " + line.strip());
            } else {
                gather(Kind.PARAGRAPH);
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(line.strip());
            }
        }

        private void directive(String line) {
            int space = line.indexOf(' ');
            String name = space < 0 ? line.substring(1) : line.substring(1, space);
            String value = space < 0 ? "" : line.substring(space + 1).strip();
            switch (name) {
                case "date" -> {
                    close();
                    blocks.add(new DateLine(value));
                }
                case "specimen" -> {
                    close();
                    blocks.add(new Specimen(value));
                }
                case "to" -> {
                    gather(Kind.RECIPIENT);
                    if (!value.isEmpty()) {
                        lines.add(value);
                    }
                }
                case "ref" -> {
                    gather(Kind.REFERENCES);
                    rows.add(row(value));
                }
                case "sign" -> {
                    gather(Kind.SIGNATURE);
                    lines.add(value);
                }
                default -> throw new IllegalArgumentException("A letter's line begins with @" + name
                        + ", which says nothing this layout knows");
            }
        }

        private void gather(Kind kind) {
            if (open != kind) {
                close();
                open = kind;
            }
        }

        private void close() {
            switch (open) {
                case PARAGRAPH -> blocks.add(new Paragraph(paragraph.toString()));
                case RECIPIENT -> blocks.add(new Recipient(List.copyOf(lines)));
                case REFERENCES -> blocks.add(new References(List.copyOf(rows)));
                case TABLE -> blocks.add(new Table(List.copyOf(rows)));
                case POINTS -> blocks.add(new Points(List.copyOf(lines)));
                case SIGNATURE -> blocks.add(new Signature(List.copyOf(lines)));
                case NONE -> {
                    // Nothing in hand.
                }
            }
            open = Kind.NONE;
            lines.clear();
            rows.clear();
            paragraph.setLength(0);
        }

        Letter finish() {
            close();
            return new Letter(subject, List.copyOf(blocks));
        }

        /** "label | value": the value is everything after the first bar, so a bar inside it stays text. */
        private static Row row(String text) {
            int bar = text.indexOf(" | ");
            return bar < 0 ? new Row(text.strip(), "")
                    : new Row(text.substring(0, bar).strip(), text.substring(bar + 3).strip());
        }
    }
}
