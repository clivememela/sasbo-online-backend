package za.co.titandynamix.sasbo_online_backend.legacy.read;

import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A workbook, read row by row from its sheet's XML -- POI's event API, never its object model, which holds a whole
 * workbook in memory.
 *
 * <h2>Why the upload is written to a file first</h2>
 * A workbook is a zip, and a zip's directory is at its end: POI reads one from a stream only by holding every part of it
 * in memory. Written to a file, it is opened in place and its sheet streamed. The file is the application's own,
 * created and deleted around the read, and the upload's size ceiling applies before it is written. POI's own zip-bomb
 * limits are left as they are, never lowered.
 *
 * <h2>What a cell reads as</h2>
 * Its value as formatted -- except a date, which reads as ISO ({@code 2026-08-31}), and a whole number in the General
 * format, which reads as its digits: General shows an identity number as {@code 8.00101E+12}, and a cell that lost its
 * digits is no use to anyone. A formula's cached value is read; nothing is evaluated.
 */
public final class XlsxSourceReader implements SourceReader {

    @Override
    public ReadSummary read(InputStream in, ReaderSettings settings, ReadLimits limits, RowSink sink) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in, 64 * 1024);
        Sniff.requireWorkbook(buffered);
        Path workbook = Files.createTempFile("sasbo-import-", ".xlsx");
        try {
            Files.copy(buffered, workbook, StandardCopyOption.REPLACE_EXISTING);
            return readSheet(workbook, settings, limits, sink);
        } finally {
            Files.deleteIfExists(workbook);
        }
    }

    private static ReadSummary readSheet(Path workbook, ReaderSettings settings, ReadLimits limits, RowSink sink) {
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            RecordAssembler records = new RecordAssembler(settings, limits, sink);
            try (InputStream sheet = sheet(reader, settings.sheet())) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, new Collector(records, limits),
                        new IsoDataFormatter(), false));
                parser.parse(new InputSource(sheet));
            }
            return records.finish();
        } catch (SourceRefused refused) {
            throw refused;
        } catch (SAXException wrapped) {
            if (wrapped.getException() instanceof SourceRefused refused) {
                throw refused;
            }
            throw unreadable();
        } catch (IOException | OpenXML4JException | ParserConfigurationException | UnsupportedFileFormatException
                 | POIXMLException | OpenXML4JRuntimeException notAWorkbook) {
            throw unreadable();
        }
    }

    /** The sheet the mapping names, or the first. */
    private static InputStream sheet(XSSFReader reader, String wanted) throws IOException, OpenXML4JException {
        Iterator<InputStream> sheets = reader.getSheetsData();
        XSSFReader.SheetIterator named = (XSSFReader.SheetIterator) sheets;
        while (named.hasNext()) {
            InputStream sheet = named.next();
            if (wanted == null || wanted.equalsIgnoreCase(named.getSheetName())) {
                return sheet;
            }
            sheet.close();
        }
        throw new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                wanted == null ? "The workbook has no sheet." : "The workbook has no sheet with the name the mapping gives.");
    }

    private static SourceRefused unreadable() {
        return new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                "The workbook could not be opened: it is damaged, protected, or not an .xlsx.");
    }

    /** The rows of the sheet, handed to the assembler as they are parsed. */
    private static final class Collector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final RecordAssembler records;
        private final ReadLimits limits;
        private final List<String> cells = new ArrayList<>();
        private final Set<Integer> truncated = new HashSet<>();
        private boolean overflow;
        private int nextColumn;

        Collector(RecordAssembler records, ReadLimits limits) {
            this.records = records;
            this.limits = limits;
        }

        @Override
        public void startRow(int rowNum) {
            cells.clear();
            truncated.clear();
            overflow = false;
            nextColumn = 0;
        }

        @Override
        public void endRow(int rowNum) {
            // A sheet leaves out the cells a row does not fill: in a workbook a missing cell is blank, not a short row.
            while (cells.size() < records.width()) {
                cells.add("");
            }
            records.record(rowNum + 1, List.copyOf(cells), Set.copyOf(truncated), false, overflow);
        }

        @Override
        public void cell(String reference, String formattedValue, XSSFComment comment) {
            int column = reference == null ? nextColumn : new CellReference(reference).getCol();
            nextColumn = column + 1;
            if (column >= limits.maxColumns()) {
                overflow = true;
                return;
            }
            while (cells.size() < column) {
                cells.add("");
            }
            String value = formattedValue == null ? "" : formattedValue;
            if (value.length() > limits.maxCellChars()) {
                value = value.substring(0, limits.maxCellChars());
                truncated.add(column);
            }
            cells.add(value);
        }

        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Page headers and footers are not data.
        }

        public void endSheet() {
            // Nothing to close: the assembler finishes when the parse returns.
        }
    }

    /** Dates as ISO, and whole numbers in the General format as their digits. */
    static final class IsoDataFormatter extends DataFormatter {

        IsoDataFormatter() {
            super(Locale.ROOT);
        }

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString) {
            return formatRawCellContents(value, formatIndex, formatString, false);
        }

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString,
                                            boolean use1904Windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
                LocalDateTime at = DateUtil.getLocalDateTime(value, use1904Windowing);
                return at.toLocalTime().equals(LocalTime.MIDNIGHT) ? at.toLocalDate().toString() : at.toString();
            }
            boolean general = formatIndex == 0 || "General".equalsIgnoreCase(formatString);
            if (general && value == Math.rint(value) && Math.abs(value) < 1e15) {
                return BigDecimal.valueOf(value).toBigInteger().toString();
            }
            return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
        }
    }
}
