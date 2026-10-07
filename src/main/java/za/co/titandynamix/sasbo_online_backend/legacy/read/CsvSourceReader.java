package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delimited text, read a character at a time: any delimiter and quote, any encoding Java knows, line breaks of either
 * kind, a byte-order mark passed over. A quoted cell may hold the delimiter, a doubled quote and line breaks; a row is
 * numbered by the line it starts on.
 *
 * <h2>What a badly formed row does</h2>
 * Never stops the read: the row is handed on with {@code ROW_MALFORMED} -- a quote never closed, text after a closing
 * quote, a quote inside a bare cell -- and read as leniently as it can be, so the report shows the line. A quote never
 * closed takes the rest of the file into its cell, as every CSV reader does; the cell is cut at its ceiling, so memory
 * stays bounded whatever the file.
 *
 * <h2>Characters the encoding cannot read</h2>
 * Become U+FFFD, never an exception, and the row says {@code ENCODING_SUSPECT}: a Windows-1252 export read as UTF-8 is
 * the usual cause, and the person fixing it needs the row, not a stack trace.
 */
public final class CsvSourceReader implements SourceReader {

    @Override
    public ReadSummary read(InputStream in, ReaderSettings settings, ReadLimits limits, RowSink sink) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in, 64 * 1024);
        Sniff.requireText(buffered, settings.charset());
        CharsetDecoder decoder = settings.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new Parser(new InputStreamReader(buffered, decoder), settings, limits,
                new RecordAssembler(settings, limits, sink)).run();
    }

    /** One pass over the characters. Holds one record and one buffer of text, never more. */
    private static final class Parser {

        private final Reader reader;
        private final char delimiter;
        private final char quote;
        private final ReadLimits limits;
        private final RecordAssembler records;

        private final char[] buffer = new char[16 * 1024];
        private int position;
        private int length;
        private int line = 1;
        private boolean first = true;

        // The record being read.
        private final List<String> cells = new ArrayList<>();
        private final Set<Integer> truncated = new HashSet<>();
        private final StringBuilder cell = new StringBuilder();
        private int recordLine = 1;
        private boolean malformed;
        private boolean overflow;

        // The cell being read.
        private boolean quoted;
        private boolean closedQuote;
        private boolean cellTruncated;
        private boolean cellHasText;

        Parser(Reader reader, ReaderSettings settings, ReadLimits limits, RecordAssembler records) {
            this.reader = reader;
            this.delimiter = settings.delimiter();
            this.quote = settings.quote();
            this.limits = limits;
            this.records = records;
        }

        ReadSummary run() throws IOException {
            int c;
            while ((c = next()) != -1) {
                if (first) {
                    first = false;
                    if (c == '﻿') {
                        continue;
                    }
                }
                if (quoted && !closedQuote) {
                    inQuotes(c);
                    continue;
                }
                if (c == delimiter) {
                    endCell();
                } else if (c == '\r' || c == '\n') {
                    if (c == '\r' && peek() == '\n') {
                        next();
                    }
                    endCell();
                    endRecord();
                    line++;
                    recordLine = line;
                } else if (c == quote && !quoted && !cellHasText) {
                    // A cell opens its quote -- after nothing but spaces, which a quoted cell does not keep.
                    cell.setLength(0);
                    quoted = true;
                } else {
                    if (closedQuote || c == quote) {
                        malformed = true;
                    }
                    append(c);
                }
            }
            if (quoted && !closedQuote) {
                malformed = true;
            }
            if (cell.length() > 0 || !cells.isEmpty() || quoted || malformed) {
                endCell();
                endRecord();
            }
            return records.finish();
        }

        private void inQuotes(int c) throws IOException {
            if (c == quote) {
                if (peek() == quote) {
                    next();
                    append(quote);
                } else {
                    closedQuote = true;
                }
            } else if (c == '\r') {
                if (peek() == '\n') {
                    next();
                }
                line++;
                append('\n');
            } else {
                if (c == '\n') {
                    line++;
                }
                append(c);
            }
        }

        private void append(int c) {
            if (cell.length() < limits.maxCellChars()) {
                cell.append((char) c);
            } else {
                cellTruncated = true;
            }
            if (c != ' ' && c != '\t') {
                cellHasText = true;
            }
        }

        private void endCell() {
            if (cells.size() < limits.maxColumns()) {
                if (cellTruncated) {
                    truncated.add(cells.size());
                }
                cells.add(cell.toString());
            } else {
                overflow = true;
            }
            cell.setLength(0);
            quoted = false;
            closedQuote = false;
            cellTruncated = false;
            cellHasText = false;
        }

        private void endRecord() {
            records.record(recordLine, List.copyOf(cells), Set.copyOf(truncated), malformed, overflow);
            cells.clear();
            truncated.clear();
            malformed = false;
            overflow = false;
        }

        private int next() throws IOException {
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
