package za.co.titandynamix.sasbo_online_backend.services;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The statistics pack's two workbooks (month end 04.10), on H2 from {@link SummaryFixture}: the statistics workbook is
 * every sheet of every statistic, cell for cell as the statistics answer -- checked against them, never against a
 * typed workbook (MD23) -- a hidden row saying so in words; the month-on-month tables add up from the months taken;
 * below {@code all} both follow the switch as the statistics do (MD62); and a label planted to run as a formula is
 * written as the text it is (MV-13).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-workbooks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndWorkbooksTest.AtTheFixturesClock.class)
@DisplayName("The statistics pack's workbooks")
class MonthEndWorkbooksTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SummaryFixture.NOW, AcbTime.ZONE);
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndWorkbooks workbooks;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndStatistics statistics;

    /** What a statistics service and a workbook writer at another switch are built from (MD62). */
    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository facts;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository codes;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment environment;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers seedNumbers;

    @Autowired
    private SummaryGate gate;

    @BeforeEach
    void thePopulationAndNothingTaken() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
        jdbc.update("DELETE FROM month_end_counts");
        jdbc.update("DELETE FROM month_end_workplaces");
        jdbc.update("DELETE FROM month_end_months");
    }

    @Test
    @DisplayName("🔒 MD23: the statistics workbook is every sheet of every statistic, cell for cell as the statistics answer")
    void theStatistics() throws IOException {
        takes.take(SummaryFixture.MONTH, "seed");
        List<MonthEndStatisticDto> all = statistics.all("2026-09");

        try (XSSFWorkbook book = read(workbooks.statistics("2026-09"))) {
            assertThat(book.getNumberOfSheets()).as("a sheet a statistic's sheet")
                    .isEqualTo(all.stream().mapToInt(a -> a.sheets().size()).sum());
            for (MonthEndStatisticDto answer : all) {
                for (MonthEndStatisticDto.Sheet sheet : answer.sheets()) {
                    Sheet written = sheetOf(book, answer.statistic() + " " + sheet.key());
                    assertThat(cellsOf(written, sheet.total().label()))
                            .as("%s %s: the total, cell for cell", answer.statistic(), sheet.key())
                            .isEqualTo(expected(sheet.total()));
                    for (MonthEndStatisticDto.Row row : sheet.rows()) {
                        if (row.cells() != null) {
                            assertThat(cellsOf(written, row.label()))
                                    .as("%s %s: row %s", answer.statistic(), sheet.key(), row.key())
                                    .isEqualTo(expected(row));
                        }
                    }
                }
            }
            Sheet byTableCode = sheetOf(book, "membership by-table-code");
            assertThat(textBeside(byTableCode, "902 INVENTED INSURER")).as("a hidden row says so in words")
                    .isEqualTo("fewer than 5");
            assertThat(textBeside(byTableCode, "901 INVENTED BANK")).isEqualTo("withheld");
        }
    }

    @Test
    @DisplayName("🔒 MD62, MV-55: below 'all' the pack's workbook is the statistics as they answer below it -- the totals and one row")
    void belowAll() throws IOException {
        MonthEndStatistics atNames = new MonthEndStatistics(takes, facts, codes,
                new za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch("names"), environment, seedNumbers,
                gate);
        MonthEndWorkbooks below = new MonthEndWorkbooks(atNames, takes, facts, environment, seedNumbers, gate);
        takes.take(SummaryFixture.MONTH, "seed");
        List<MonthEndStatisticDto> all = atNames.all("2026-09");

        try (XSSFWorkbook book = read(below.statistics("2026-09"))) {
            for (MonthEndStatisticDto answer : all) {
                for (MonthEndStatisticDto.Sheet sheet : answer.sheets()) {
                    Sheet written = sheetOf(book, answer.statistic() + " " + sheet.key());
                    assertThat(cellsOf(written, sheet.total().label()))
                            .as("%s %s: the total, cell for cell", answer.statistic(), sheet.key())
                            .isEqualTo(expected(sheet.total()));
                    for (MonthEndStatisticDto.Row row : sheet.rows()) {
                        if (row.cells() != null) {
                            assertThat(cellsOf(written, row.label()))
                                    .as("%s %s: row %s", answer.statistic(), sheet.key(), row.key())
                                    .isEqualTo(expected(row));
                        }
                    }
                }
            }
            MonthEndStatisticDto branches = all.stream().filter(a -> a.statistic().equals("branches")).findFirst()
                    .orElseThrow();
            assertThat(branches.sheets().getFirst().rows()).as("cut by where members work, below 'all': one row")
                    .extracting(MonthEndStatisticDto.Row::key).containsExactly("~ALL");
        }
    }

    @Test
    @DisplayName("🔒 MD62, MR-SEC-19: below 'all' the month-on-month tables are the whole alone -- no core, no region")
    void belowAllTheMonthlyTables() throws IOException {
        MonthEndStatistics atNames = new MonthEndStatistics(takes, facts, codes,
                new za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch("names"), environment, seedNumbers,
                gate);
        MonthEndWorkbooks below = new MonthEndWorkbooks(atNames, takes, facts, environment, seedNumbers, gate);
        takes.take(YearMonth.of(2026, 8), "seed");
        takes.take(SummaryFixture.MONTH, "seed");

        try (XSSFWorkbook book = read(below.monthly(SummaryFixture.MONTH))) {
            Sheet one = book.getSheet("Table 1");
            assertThat(numbersIn(one)).as("Table 1 is core by region: below 'all' it holds no figure").isEmpty();
            assertThat(labelCell(one, MonthEndWorkbooks.NOT_GROUPED)).as("and says why").isNotNull();
            Sheet two = book.getSheet("Table2");
            // All 16 after August's 15, the growth to date from August, the first month taken here.
            assertThat(cellsOf(two, "September 2026")).as("the whole's columns alone").containsExactly(16.0, 15.0, 1.0,
                    1.0);
            assertThat(labelCell(two, MonthEndWorkbooks.CORE_BLANK)).isNotNull();
        }
    }

    @Test
    @DisplayName("🔒 MV-13: a label planted to run as a formula is written as the text it is -- a string, behind an apostrophe")
    void aPlantedFormulaIsText() throws IOException {
        String planted = "=HYPERLINK(\"http://example.invalid\",\"open\")";
        jdbc.update("UPDATE employers SET name = ? WHERE ordinal = 1", planted);
        try {
            takes.take(SummaryFixture.MONTH, "seed");
            try (XSSFWorkbook book = read(workbooks.statistics("2026-09"))) {
                Cell label = labelCell(sheetOf(book, "branches by-employer"), "'" + planted);
                assertThat(label).as("the employer's row, labelled as text").isNotNull();
                assertThat(label.getCellType()).isEqualTo(CellType.STRING);
            }
        } finally {
            jdbc.update("UPDATE employers SET name = 'INVENTED BANK' WHERE ordinal = 1");
        }
    }

    @Test
    @DisplayName("the month-on-month tables: core and all, by region, with their growth -- over the months taken")
    void theMonthlyTables() throws IOException {
        takes.take(YearMonth.of(2026, 6), "seed");
        takes.take(YearMonth.of(2026, 8), "seed");
        takes.take(SummaryFixture.MONTH, "seed");

        try (XSSFWorkbook book = read(workbooks.monthly(SummaryFixture.MONTH))) {
            Sheet one = book.getSheet("Table 1");
            assertThat(headerOf(one)).containsExactly("Month", "Core", "INVENTED NORTH", "INVENTED SOUTH",
                    "Growth in the month", "Growth to date");
            // June: W1 six paying (m9 had resigned), W3 four; August: six and three; September: eight and two.
            assertThat(cellsOf(one, "June 2026")).containsExactly(10.0, 6.0, 4.0);
            assertThat(cellsOf(one, "August 2026")).containsExactly(9.0, 6.0, 3.0, -1.0, -1.0);
            assertThat(cellsOf(one, "September 2026")).containsExactly(10.0, 8.0, 2.0, 1.0, 0.0);
            Sheet two = book.getSheet("Table2");
            assertThat(cellsOf(two, "September 2026")).as("core 10 after 9; all banks 16 after 15")
                    .containsExactly(10.0, 9.0, 1.0, 0.0, 16.0, 15.0, 1.0, 0.0);
        }
    }

    // ------------------------------------------------------------------------------------------------ reading back

    private static XSSFWorkbook read(byte[] bytes) throws IOException {
        assertThat(bytes).as("an .xlsx is a zip").startsWith((byte) 'P', (byte) 'K');
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static Sheet sheetOf(XSSFWorkbook book, String wanted) {
        String name = wanted.length() > MonthEndWorkbooks.SHEET_NAME_CEILING
                ? wanted.substring(0, MonthEndWorkbooks.SHEET_NAME_CEILING) : wanted;
        Sheet sheet = book.getSheet(name);
        assertThat(sheet).as("the sheet %s", name).isNotNull();
        return sheet;
    }

    private static Cell labelCell(Sheet sheet, String label) {
        for (Row row : sheet) {
            Cell first = row.getCell(0);
            if (first != null && first.getCellType() == CellType.STRING && first.getStringCellValue().equals(label)) {
                return first;
            }
        }
        return null;
    }

    /** The numbers of the row labelled {@code label}, in their columns' order, blanks left out. */
    private static List<Double> cellsOf(Sheet sheet, String label) {
        Cell first = labelCell(sheet, label);
        assertThat(first).as("a row labelled %s", label).isNotNull();
        List<Double> numbers = new ArrayList<>();
        Row row = first.getRow();
        for (int c = 1; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                numbers.add(cell.getNumericCellValue());
            }
        }
        return numbers;
    }

    /** Every number on the sheet, wherever it stands. */
    private static List<Double> numbersIn(Sheet sheet) {
        List<Double> numbers = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.NUMERIC) {
                    numbers.add(cell.getNumericCellValue());
                }
            }
        }
        return numbers;
    }

    private static String textBeside(Sheet sheet, String label) {
        Cell first = labelCell(sheet, label);
        assertThat(first).as("a row labelled %s", label).isNotNull();
        return first.getRow().getCell(1).getStringCellValue();
    }

    private static List<String> headerOf(Sheet sheet) {
        Row header = sheet.getRow(3);
        List<String> out = new ArrayList<>();
        for (Cell cell : header) {
            out.add(cell.getStringCellValue());
        }
        return out;
    }

    private static List<Double> expected(MonthEndStatisticDto.Row row) {
        List<Double> out = new ArrayList<>();
        for (java.math.BigDecimal cell : row.cells()) {
            if (cell != null) {
                out.add(cell.doubleValue());
            }
        }
        return out;
    }
}
