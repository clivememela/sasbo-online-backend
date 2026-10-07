package za.co.titandynamix.sasbo_online_backend.legacy.read;

import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Columns by position, as old systems print their records: a layout of field, start (1-based) and length, applied to
 * each line. Lines before the data, blank lines and the lines a layout names as not records (a header or trailer
 * record's marker) are passed over; a row is numbered by its line.
 *
 * <p>A value is kept as the line has it, less its trailing spaces -- the padding of the field -- and anything else is
 * the mapping's to clean. A line that ends before a field begins is {@code ROW_SHORT}: the fields it does not reach are
 * blank. A line longer than {@value ReadLimits#MAX_LINE_CHARS} characters is not a record -- a file with no line breaks,
 * or binary read as text -- and is {@code ROW_MALFORMED}; no more of it is held than the layout needs.
 */
public final class FixedWidthSourceReader implements SourceReader {

    @Override
    public ReadSummary read(InputStream in, ReaderSettings settings, ReadLimits limits, RowSink sink) throws IOException {
        List<FixedWidthField> layout = settings.layout();
        if (layout.isEmpty()) {
            throw new IllegalArgumentException("a fixed-width file needs a layout");
        }
        BufferedInputStream buffered = new BufferedInputStream(in, 64 * 1024);
        Sniff.requireText(buffered, settings.charset());
        CharsetDecoder decoder = settings.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        int layoutEnd = layout.stream().mapToInt(FixedWidthField::end).max().orElse(0);
        // One past the layout: room for a byte-order mark on the first line without cutting its last field short.
        Lines lines = new Lines(new InputStreamReader(buffered, decoder), layoutEnd + 1);

        List<String> columns = layout.stream().map(FixedWidthField::name).toList();
        int lineNumber = 0;
        int rows = 0;
        Line line;
        while ((line = lines.next()) != null) {
            lineNumber++;
            String text = line.text();
            if (lineNumber == 1 && !text.isEmpty() && text.charAt(0) == '﻿') {
                text = text.substring(1);
            }
            if (lineNumber <= settings.skipRows() || (text.isBlank() && !line.tooLong()) || skipped(text, settings)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            List<Problem> problems = new ArrayList<>();
            boolean shortLine = false;
            for (FixedWidthField field : layout) {
                int from = field.start() - 1;
                if (text.length() <= from) {
                    values.put(field.name(), "");
                    shortLine = true;
                } else {
                    values.put(field.name(), text.substring(from, Math.min(field.end(), text.length())).stripTrailing());
                }
            }
            if (line.tooLong()) {
                problems.add(new Problem(RuleCode.ROW_MALFORMED, null));
            }
            if (shortLine) {
                problems.add(new Problem(RuleCode.ROW_SHORT, null));
            }
            if (values.values().stream().anyMatch(value -> value.indexOf('�') >= 0)) {
                problems.add(new Problem(RuleCode.ENCODING_SUSPECT, null));
            }
            rows++;
            if (rows > limits.maxRows()) {
                throw new SourceRefused(SourceRefused.Reason.TOO_MANY_ROWS, limits.maxRows(),
                        "The file has more rows than the limit.");
            }
            sink.accept(new SourceRow(lineNumber, values, problems));
        }
        if (rows == 0) {
            throw new SourceRefused(SourceRefused.Reason.EMPTY, "The file holds no rows to import.");
        }
        return new ReadSummary(rows, columns);
    }

    private static boolean skipped(String text, ReaderSettings settings) {
        for (String marker : settings.skipLinesStartingWith()) {
            if (text.startsWith(marker)) {
                return true;
            }
        }
        return false;
    }

    /** A line as read: the part of it the layout reaches, and whether it ran past any record's length. */
    private record Line(String text, boolean tooLong) {
    }

    /** Lines of either line break, each kept only as far as the layout reaches. */
    private static final class Lines {

        private final Reader reader;
        private final int keep;
        private final char[] buffer = new char[16 * 1024];
        private int position;
        private int length;
        private boolean ended;

        Lines(Reader reader, int keep) {
            this.reader = reader;
            this.keep = keep;
        }

        Line next() throws IOException {
            if (ended) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            int total = 0;
            boolean any = false;
            int c;
            while ((c = read()) != -1) {
                any = true;
                if (c == '\n') {
                    return new Line(text.toString(), total > ReadLimits.MAX_LINE_CHARS);
                }
                if (c == '\r') {
                    if (peek() == '\n') {
                        read();
                    }
                    return new Line(text.toString(), total > ReadLimits.MAX_LINE_CHARS);
                }
                total++;
                if (text.length() < keep) {
                    text.append((char) c);
                }
            }
            ended = true;
            return any ? new Line(text.toString(), total > ReadLimits.MAX_LINE_CHARS) : null;
        }

        private int read() throws IOException {
            if (position >= length && !fill()) {
                return -1;
            }
            return buffer[position++];
        }

        private int peek() throws IOException {
            if (position >= length && !fill()) {
                return -1;
            }
            return buffer[position];
        }

        private boolean fill() throws IOException {
            length = reader.read(buffer, 0, buffer.length);
            position = 0;
            return length > 0;
        }
    }
}
