package za.co.titandynamix.sasbo_online_backend.acb.opening;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The opening-position sheet SASBO's administrators fill in by hand (D24): one row per member --
 * membership number · unpaids standing · last unpaid month -- under one header row.
 *
 * <h2>🔒 Its limits are stated here, never inherited (SD3-B)</h2>
 * An {@code .xlsx} is a zip of XML. So: {@code .xlsx} only, checked by its bytes before POI sees them;
 * at most {@value #MAX_BYTES} bytes and {@value #MAX_ROWS} rows; one sheet; no formula and no external
 * link -- refused, never evaluated; POI's own zip-bomb limits left as they are, never lowered. A
 * problem names its row and field and never echoes a cell's value: the sheet carries members'
 * numbers, and a refusal is read by whoever sent it.
 */
public final class OpeningPositionSheet {

    /** One megabyte. */
    public static final int MAX_BYTES = 1024 * 1024;

    /** Tens are expected -- July's file re-debited thirty; five hundred is a mistake, not a sheet. */
    public static final int MAX_ROWS = 500;

    /** The column each field is read from. */
    static final int NUMBER = 0;
    static final int UNPAIDS = 1;
    static final int MONTH = 2;

    /** One data row, as read. A value that did not read is {@code null}, and its problem is listed. */
    public record Line(int row, String membershipNumber, Integer unpaidsStanding, YearMonth lastUnpaidMonth) {
    }

    /** A problem with a row -- or, as row 0, with the sheet. Never the cell's value. */
    public record Problem(int row, String field, String problem) {
    }

    /** What the sheet held, and everything wrong with it. */
    public record Read(List<Line> lines, List<Problem> problems) {
    }

    private OpeningPositionSheet() {
    }

    /**
     * Reads {@code content}. Refuses {@code UPLOAD_TOO_LARGE} over the byte limit and
     * {@code UPLOAD_NOT_A_SHEET} for anything that is not an {@code .xlsx} POI can open; everything
     * else wrong is a {@link Problem}.
     */
    public static Read read(byte[] content) {
        if (content == null || content.length == 0) {
            throw notASheet();
        }
        if (content.length > MAX_BYTES) {
            throw new AcbRefusal(AcbRefusalCode.UPLOAD_TOO_LARGE, "The sheet is larger than one megabyte.",
                    Map.of("limitBytes", String.valueOf(MAX_BYTES)));
        }
        // An .xlsx is a zip, and a zip begins PK\3\4: anything else is refused before POI reads a byte.
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K' || content[2] != 3 || content[3] != 4) {
            throw notASheet();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return read(workbook);
        } catch (AcbRefusal refused) {
            throw refused;
        } catch (IOException | RuntimeException unreadable) {
            // Not a workbook POI can open -- or one its own zip limits refused. Never evaluated further.
            throw notASheet();
        }
    }

    private static Read read(XSSFWorkbook workbook) {
        List<Problem> problems = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        if (workbook.getNumberOfSheets() != 1) {
            problems.add(new Problem(0, "sheet", "the workbook must hold exactly one sheet"));
        }
        if (!workbook.getExternalLinksTable().isEmpty()) {
            problems.add(new Problem(0, "sheet", "external links are refused"));
        }
        Sheet sheet = workbook.getSheetAt(0);
        Set<String> seen = new HashSet<>();
        int dataRows = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || blank(row)) {
                continue;
            }
            if (++dataRows > MAX_ROWS) {
                problems.add(new Problem(0, "sheet", "more than " + MAX_ROWS + " rows"));
                break;
            }
            int number = r + 1;
            boolean formula = false;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    problems.add(new Problem(number, fieldOf(cell.getColumnIndex()), "a formula -- type the value itself"));
                    formula = true;
                }
            }
            if (formula) {
                lines.add(new Line(number, null, null, null));
                continue;
            }
            String membership = membershipNumber(row.getCell(NUMBER));
            if (membership == null) {
                problems.add(new Problem(number, "membershipNumber", "missing"));
            } else if (!seen.add(membership)) {
                problems.add(new Problem(number, "membershipNumber", "appears on an earlier row too"));
            }
            Integer unpaids = wholeNumber(row.getCell(UNPAIDS));
            if (unpaids == null) {
                problems.add(new Problem(number, "unpaidsStanding", "not a whole number"));
            }
            YearMonth month = month(row.getCell(MONTH));
            if (month == null) {
                problems.add(new Problem(number, "lastUnpaidMonth", "not a month -- write it YYYY-MM"));
            }
            lines.add(new Line(number, membership, unpaids, month));
        }
        if (dataRows == 0) {
            problems.add(new Problem(0, "sheet", "no rows under the header"));
        }
        return new Read(List.copyOf(lines), List.copyOf(problems));
    }

    private static boolean blank(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK
                    && !(cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) {
                return false;
            }
        }
        return true;
    }

    private static String membershipNumber(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            return text.isEmpty() ? null : text;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double value = cell.getNumericCellValue();
            return value == Math.rint(value) && value > 0 ? String.valueOf((long) value) : null;
        }
        return null;
    }

    private static Integer wholeNumber(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double value = cell.getNumericCellValue();
            return value == Math.rint(value) && Math.abs(value) < 1000 ? (int) value : null;
        }
        if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().matches("[0-9]{1,3}")) {
            return Integer.valueOf(cell.getStringCellValue().trim());
        }
        return null;
    }

    private static YearMonth month(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return YearMonth.from(cell.getLocalDateTimeCellValue());
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return YearMonth.parse(cell.getStringCellValue().trim());
            } catch (DateTimeParseException notAMonth) {
                return null;
            }
        }
        return null;
    }

    private static String fieldOf(int column) {
        return switch (column) {
            case NUMBER -> "membershipNumber";
            case UNPAIDS -> "unpaidsStanding";
            case MONTH -> "lastUnpaidMonth";
            default -> "column " + (column + 1);
        };
    }

    private static AcbRefusal notASheet() {
        return new AcbRefusal(AcbRefusalCode.UPLOAD_NOT_A_SHEET,
                "This is not an .xlsx sheet that can be read. Fill in the opening-position sheet and send it as .xlsx.");
    }
}
