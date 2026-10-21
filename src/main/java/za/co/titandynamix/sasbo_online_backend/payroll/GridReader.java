package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reads the first sheet of a spreadsheet into a {@link SheetGrid}, within hard limits.
 *
 * <ul>
 *   <li><b>.xlsx</b> is STREAMED (POI's event reader): the sheet is never held as a workbook, so twenty thousand lines
 *       cost what their text costs. A formula is read as the value it last worked out to -- Bankserv's variance,
 *       Standard Bank's total -- and nothing is ever calculated. POI's zip guard stays as it is: a workbook that
 *       expands far beyond its size is refused, and its XML parser resolves no external entity.</li>
 *   <li><b>.xls</b> is small by nature (an old workbook carries at most 65,536 rows) and read whole.</li>
 *   <li><b>.csv</b> is UTF-8 (a byte-order mark allowed) or, where it is not valid UTF-8, Windows-1252; the separator
 *       is whichever of comma, semicolon or tab the first lines use most.</li>
 * </ul>
 * More rows than {@link PayrollLimits#MAX_ROWS} and the headings above them stop the reading at once,
 * {@code TOO_MANY_ROWS}. A damaged or password-protected workbook is {@code FILE_UNREADABLE}; a zip that holds no
 * workbook is {@code FILE_TYPE_NOT_ACCEPTED}.
 */
public final class GridReader {

    /** The rows a sheet may hold in all: the lines, the headings above them, and a margin for title rows. */
    static final int ROW_CEILING = PayrollLimits.MAX_ROWS + PayrollLimits.HEADING_SCAN_ROWS + 50;

    private GridReader() {
    }

    public static SheetGrid read(FileSniffer.Kind kind, byte[] bytes) {
        return switch (kind) {
            case XLSX -> xlsx(bytes);
            case XLS -> xls(bytes);
            case CSV -> csv(bytes);
            default -> throw new IllegalArgumentException("not a spreadsheet: " + kind);
        };
    }

    // ------------------------------------------------------------------------------------------------------- xlsx

    static SheetGrid xlsx(byte[] bytes) {
        ZipGuard.check(bytes);
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            if (!holdsAWorkbook(pkg)) {
                throw notASpreadsheet();
            }
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg, false);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw unreadable();
            }
            try (InputStream sheet = sheets.next()) {
                String name = sheets.getSheetName();
                Collector collector = new Collector();
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, collector,
                        new PlainFormatter(), false));
                parser.parse(new InputSource(sheet));
                return new SheetGrid(name, collector.rows);
            }
        } catch (PayrollRefusal refusal) {
            throw refusal;
        } catch (NotOfficeXmlFileException | InvalidFormatException notOoxml) {
            throw notASpreadsheet();
        } catch (TooManyRows tooMany) {
            throw tooManyRows();
        } catch (Exception damaged) {
            // A zip that expands far beyond its size, a broken part, a parser fault: the message may quote the file's
            // content, so it goes nowhere -- the refusal says what an administrator can do.
            throw unreadable();
        }
    }

    /** Whether the package's main document is a spreadsheet's -- a .docx is a zip with OOXML parts too. */
    private static boolean holdsAWorkbook(OPCPackage pkg) throws InvalidFormatException {
        PackageRelationshipCollection core = pkg.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT);
        if (core == null || core.size() == 0) {
            core = pkg.getRelationshipsByType(PackageRelationshipTypes.STRICT_CORE_DOCUMENT);
        }
        if (core == null || core.size() == 0) {
            return false;
        }
        PackagePart main = pkg.getPart(core.getRelationship(0));
        String type = main == null ? "" : main.getContentType();
        return type.contains("spreadsheetml") || type.contains("ms-excel.sheet");
    }

    /** Collects the streamed rows, one list of cells each, stopping the read past the ceiling. */
    private static final class Collector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final List<SheetGrid.Row> rows = new ArrayList<>();
        private List<String> cells;
        private int nextColumn;
        private int seen;

        @Override
        public void startRow(int rowNum) {
            if (++seen > ROW_CEILING) {
                throw new TooManyRows();
            }
            cells = new ArrayList<>();
            nextColumn = 0;
        }

        @Override
        public void endRow(int rowNum) {
            if (cells.stream().anyMatch(cell -> cell != null)) {
                rows.add(new SheetGrid.Row(rowNum + 1, cells));
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? nextColumn : new CellReference(cellReference).getCol();
            nextColumn = column + 1;
            if (column >= PayrollLimits.MAX_COLUMNS) {
                return;
            }
            while (cells.size() <= column) {
                cells.add(null);
            }
            cells.set(column, clean(formattedValue));
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Page headers and footers are no lines of the file.
        }
    }

    /** A stop signal: past the ceiling, the rest of the sheet is not read. */
    private static final class TooManyRows extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TooManyRows() {
            super(null, null, false, false);
        }
    }

    /**
     * Numbers as they are, not as they are shown: {@code -174} and not {@code (174.00)}, {@code 8106145689086} and not
     * {@code 8.11E+12}; a date as its ISO day. What a cell SHOWS depends on its format, and the comparison needs what
     * it holds.
     */
    static final class PlainFormatter extends DataFormatter {

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString) {
            return formatRawCellContents(value, formatIndex, formatString, false);
        }

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString,
                                            boolean use1904Windowing) {
            if (formatString != null && DateUtil.isADateFormat(formatIndex, formatString)
                    && DateUtil.isValidExcelDate(value)) {
                return DateUtil.getLocalDateTime(value, use1904Windowing).toLocalDate().toString();
            }
            return plain(value);
        }
    }

    static String plain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // -------------------------------------------------------------------------------------------------------- xls

    static SheetGrid xls(byte[] bytes) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw unreadable();
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() > ROW_CEILING) {
                throw tooManyRows();
            }
            List<SheetGrid.Row> rows = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int column = 0; column < Math.min(Math.max(row.getLastCellNum(), 0), PayrollLimits.MAX_COLUMNS);
                     column++) {
                    cells.add(clean(textOf(row.getCell(column))));
                }
                if (cells.stream().anyMatch(cell -> cell != null)) {
                    rows.add(new SheetGrid.Row(row.getRowNum() + 1, cells));
                }
            }
            return new SheetGrid(sheet.getSheetName(), rows);
        } catch (PayrollRefusal refusal) {
            throw refusal;
        } catch (Exception damaged) {
            // Encrypted, a Word document in an OLE2 container, a broken record: the message may quote the file.
            throw unreadable();
        }
    }

    private static String textOf(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getRichStringCellValue().getString();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) && cell.getLocalDateTimeCellValue() != null
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : plain(cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> null;
        };
    }

    // -------------------------------------------------------------------------------------------------------- csv

    static SheetGrid csv(byte[] bytes) {
        String text = decode(bytes);
        char separator = separatorOf(text);
        List<SheetGrid.Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int record = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else if (cell.length() < PayrollLimits.MAX_CELL_LENGTH * 4) {
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == separator) {
                addCell(cells, cell);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                addCell(cells, cell);
                record = endRecord(rows, cells, record);
                cells = new ArrayList<>();
            } else if (cell.length() < PayrollLimits.MAX_CELL_LENGTH * 4) {
                cell.append(c);
            }
        }
        if (!cell.isEmpty() || !cells.isEmpty()) {
            addCell(cells, cell);
            endRecord(rows, cells, record);
        }
        return new SheetGrid(null, rows);
    }

    private static void addCell(List<String> cells, StringBuilder cell) {
        if (cells.size() < PayrollLimits.MAX_COLUMNS) {
            cells.add(clean(cell.toString()));
        }
        cell.setLength(0);
    }

    private static int endRecord(List<SheetGrid.Row> rows, List<String> cells, int record) {
        if (cells.stream().anyMatch(c -> c != null)) {
            if (rows.size() >= ROW_CEILING) {
                throw tooManyRows();
            }
            rows.add(new SheetGrid.Row(record, cells));
        }
        return record + 1;
    }

    private static String decode(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, start, bytes.length - start);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, start, bytes.length - start, Charset.forName("windows-1252"));
        }
    }

    /** Whichever of comma, semicolon and tab the first lines use most, outside quotes. */
    static char separatorOf(String text) {
        Map<Character, Integer> counts = new java.util.HashMap<>(Map.of(',', 0, ';', 0, '\t', 0));
        boolean quoted = false;
        int lines = 0;
        for (int i = 0; i < text.length() && lines < 20; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && counts.containsKey(c)) {
                counts.merge(c, 1, Integer::sum);
            } else if (!quoted && c == '\n') {
                lines++;
            }
        }
        return Arrays.asList(',', ';', '\t').stream().max(java.util.Comparator.comparingInt(counts::get)).orElse(',');
    }

    // ------------------------------------------------------------------------------------------------------ cells

    /** Invisible marks Old Mutual wraps its numbers in (U+202D ... U+202C), and their kin. */
    private static final String INVISIBLE = "‪‫‬‭‮‎‏﻿ ";

    /** A cell's text: invisible marks removed, trimmed, capped; {@code null} when nothing is left. */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), PayrollLimits.MAX_CELL_LENGTH));
        for (int i = 0; i < raw.length() && out.length() < PayrollLimits.MAX_CELL_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                out.append(' ');
            } else if (INVISIBLE.indexOf(c) < 0 && (c >= ' ' || c == '\t')) {
                out.append(c);
            }
        }
        String cleaned = out.toString().strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // --------------------------------------------------------------------------------------------------- refusals

    static PayrollRefusal unreadable() {
        return new PayrollRefusal(PayrollRefusalCode.FILE_UNREADABLE, "This file could not be read. It may be "
                + "damaged or protected by a password: ask the employer for an unprotected copy.");
    }

    static PayrollRefusal notASpreadsheet() {
        return new PayrollRefusal(PayrollRefusalCode.FILE_TYPE_NOT_ACCEPTED, "This is not a spreadsheet the system "
                + "can read. Load the employer's file as Excel (.xlsx or .xls) or CSV.");
    }

    static PayrollRefusal tooManyRows() {
        return new PayrollRefusal(PayrollRefusalCode.TOO_MANY_ROWS, "This file has more lines than one employer's "
                + "file may carry.", Map.of("limit", String.valueOf(PayrollLimits.MAX_ROWS)));
    }
}
