package za.co.titandynamix.sasbo_online_backend.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every layout of SASBO's employer files, read from the invented samples the fixtures stream made in each layout
 * ({@code src/test/resources/payroll/}, from {@code C:/tmp/sasbo-work/oct/fixtures/payroll/samples/}), and checked line by
 * line against each sample's manifest: the heading row found by its words, every line read, each line's staff number,
 * surname, amount -- and its ID number and initials where the layout prints them.
 */
@DisplayName("The payroll parser, layout by layout, on the invented samples")
class PayrollParserTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 9);
    private static final Set<String> PRINTS_INITIALS = Set.of("access-bank", "african-bank", "bankserv", "national-housing",
            "nedbank", "sbv", "standard-bank");
    private static final Set<String> PRINTS_ID = Set.of("access-bank", "fnb-wesbank", "metropolitan-health",
            "national-housing");
    private static final Set<String> PRINTS_PERIOD = Set.of("absa", "nedbank");

    @ParameterizedTest(name = "{0}: headings on row {1}, rows below the table not read {2}")
    @CsvSource({
            "absa, 1, true", "access-bank, 1, false", "african-bank, 7, false", "bankserv, 2, false",
            "capitec, 1, true", "fnb-wesbank, 1, false", "landbank, 1, false", "metropolitan-health, 2, false",
            "national-housing, 1, false", "nedbank, 1, false", "old-mutual, 1, false", "sbv, 3, false",
            "standard-bank, 1, false", "sarb, 1, false"})
    @DisplayName("each layout: the heading row by its words, every line, and each line as the manifest says")
    void eachLayout(String slug, int headingRow, boolean secondTable) throws IOException {
        byte[] bytes = resource(slug + (slug.equals("sarb") ? ".csv" : ".xlsx"));
        JsonNode manifest = new ObjectMapper().readTree(resource(slug + ".manifest.json"));
        PayrollLayout layout = PayrollLayouts.byKey(slug).orElseThrow();

        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.sniff(bytes), bytes), layout,
                Map.of(), PERIOD);

        assertThat(parsed.headingRow()).as("the heading row, as a person counts rows").isEqualTo(headingRow);
        assertThat(parsed.lines()).as("lines read").hasSize(manifest.path("totals").path("rowsOnFile").asInt());
        assertThat(parsed.lines()).allSatisfy(line -> assertThat(line.valid()).as("line %d", line.lineNumber()).isTrue());
        assertThat(parsed.rowsNotRead() > 0).as("Absa's withdrawals and Capitec's deleted members are counted, not "
                + "compared").isEqualTo(secondTable);

        Map<String, JsonNode> people = new HashMap<>();
        manifest.path("people").forEach(p -> people.put(p.path("key").asText(), p));
        List<JsonNode> rows = new java.util.ArrayList<>();
        manifest.path("rows").forEach(rows::add);
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            JsonNode person = people.get(row.path("key").asText());
            PayrollParser.Line line = row.path("excelRow").isNull() ? parsed.lines().get(i)
                    : parsed.lines().stream().filter(l -> l.lineNumber() == row.path("excelRow").asInt()).findFirst()
                    .orElseThrow(() -> new AssertionError(slug + ": no line " + row.path("excelRow").asInt()));
            String what = slug + " line " + line.lineNumber();
            assertThat(line.staffKey()).as(what + " staff number").isEqualTo(
                    PayrollKeys.staffKey(row.path("staffNumberOnFile").isNull() ? null
                            : row.path("staffNumberOnFile").asText()));
            assertThat(line.amount()).as(what + " amount").isEqualByComparingTo(new BigDecimal(row.path("amount")
                    .asText()));
            assertThat(line.name().readings()).as(what + " surname")
                    .anyMatch(r -> r.surnameKey().equals(PayrollNames.surnameKey(person.path("surname").asText())));
            if (PRINTS_INITIALS.contains(slug)) {
                assertThat(line.name().initials()).as(what + " initials")
                        .isEqualTo(PayrollNames.initialsKey(row.path("initialsOnFile").asText()));
            }
            if (PRINTS_ID.contains(slug)) {
                assertThat(line.idKey()).as(what + " ID number").isEqualTo(person.path("identityNumber").asText());
            }
            if (PRINTS_PERIOD.contains(slug)) {
                YearMonth expected = row.path("forPeriod").isNull() ? PERIOD
                        : PayrollKeys.forPeriod(row.path("forPeriod").asText());
                assertThat(line.forPeriod()).as(what + " For-period").isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("Bankserv prints two months: the amount is the column named for the file's month")
    void bankservTakesTheFilesMonth() throws IOException {
        byte[] bytes = resource("bankserv.xlsx");
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.XLSX, bytes),
                PayrollLayouts.byKey("bankserv").orElseThrow(), Map.of(), PERIOD);
        assertThat(parsed.headings().get("amount")).containsIgnoringCase("September");
    }

    @Test
    @DisplayName("every heading on the heading row is given back as printed, and the heading taken for each field")
    void headingsAreGivenBack() throws IOException {
        byte[] bytes = resource("absa.xlsx");
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.XLSX, bytes),
                PayrollLayouts.byKey("absa").orElseThrow(), Map.of(), PERIOD);
        assertThat(parsed.headingCells()).contains("Pers.No.", "Amount", "For-period");
        assertThat(parsed.headings()).containsEntry("staffNumber", "Pers.No.").containsEntry("amount", "Amount");
    }

    @Test
    @DisplayName("an administrator's column mapping reads a file no layout knows, by the headings as printed")
    void aColumnMappingReadsAnyFile() {
        byte[] csv = ("Report of deductions\nStaff,Who,Paid\n0101,\"TESTERSON, BK\",174.00\n0102,\"LEDGERWOOD, R\",174.00\n")
                .getBytes(StandardCharsets.UTF_8);
        PayrollLayout general = PayrollLayouts.byKey("general").orElseThrow();
        assertThatThrownBy(() -> PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, csv), general, Map.of(),
                PERIOD)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.HEADING_ROW_NOT_FOUND));
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, csv), general,
                Map.of("staffNumber", "Staff", "fullName", "Who", "amount", "Paid"), PERIOD);
        assertThat(parsed.headingRow()).isEqualTo(2);
        assertThat(parsed.lines()).hasSize(2);
        assertThat(parsed.lines().get(0).staffKey()).isEqualTo("101");
    }

    @Test
    @DisplayName("a mapping naming a heading the file lacks, or leaving out the amount, is refused by name")
    void aBadMappingIsRefused() {
        byte[] csv = "Staff,Who,Paid\n0101,TESTERSON,174.00\n".getBytes(StandardCharsets.UTF_8);
        PayrollLayout general = PayrollLayouts.byKey("general").orElseThrow();
        SheetGrid grid = GridReader.read(FileSniffer.Kind.CSV, csv);
        assertThatThrownBy(() -> PayrollParser.parse(grid, general, Map.of("staffNumber", "Staff", "amount", "Nope"),
                PERIOD)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.COLUMN_MAPPING_INVALID));
        assertThatThrownBy(() -> PayrollParser.parse(grid, general, Map.of("staffNumber", "Staff"), PERIOD))
                .isInstanceOfSatisfying(PayrollRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(PayrollRefusalCode.COLUMN_MAPPING_INVALID);
                    assertThat(r.details()).containsEntry("field", "amount");
                });
        assertThatThrownBy(() -> PayrollParser.parse(grid, general, Map.of("bogus", "Staff"), PERIOD))
                .isInstanceOfSatisfying(PayrollRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.COLUMN_MAPPING_INVALID));
    }

    @Test
    @DisplayName("a line that cannot be read says why; a TOTAL row is the file's stated total, not a line")
    void unreadableLinesAndTotals() {
        byte[] csv = ("staffNumber,surname,initials,amount\n1,Testerson,BK,174.00\n2,Ledgerwood,R,n/a\n"
                + "3,Tallyburn,T,\n,,,99.00\nTOTAL,,,348.00\n").getBytes(StandardCharsets.UTF_8);
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, csv),
                PayrollLayouts.typed(), Map.of(), PERIOD);
        assertThat(parsed.lines()).extracting(PayrollParser.Line::valid).containsExactly(true, false, false, false);
        assertThat(parsed.lines().get(1).problem()).isEqualTo("The amount could not be read.");
        assertThat(parsed.lines().get(2).problem()).isEqualTo("The line has no amount.");
        assertThat(parsed.lines().get(3).problem()).contains("no staff number, ID number or name");
        assertThat(parsed.statedTotal()).isEqualByComparingTo("348.00");
    }

    @Test
    @DisplayName("a blank row inside the list -- a page or a branch break -- does not end it: the lines under it are read")
    void aBlankRowInsideTheList() {
        byte[] csv = ("staffNumber,surname,initials,amount\n1,Testerson,BK,174.00\n2,Ledgerwood,R,174.00\n\n"
                + "3,Tallyburn,T,174.00\n4,Draftmore,D,174.00\n").getBytes(StandardCharsets.UTF_8);
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, csv),
                PayrollLayouts.typed(), Map.of(), PERIOD);
        assertThat(parsed.lines()).as("every line, the break or not").extracting(PayrollParser.Line::lineNumber)
                .containsExactly(2, 3, 5, 6);
        assertThat(parsed.rowsNotRead()).isZero();
    }

    @Test
    @DisplayName("must-pass: a blank row, then a caption or the headings again, ends the list -- the second list (Absa's "
            + "withdrawn, Capitec's deleted members) counted, not compared")
    void aSecondListUnderTheFirst() {
        byte[] captioned = ("staffNumber,surname,initials,amount\n1,Testerson,BK,174.00\n2,Ledgerwood,R,174.00\n\n"
                + ",Withdrawn members,,\n3,Tallyburn,T,174.00\n").getBytes(StandardCharsets.UTF_8);
        PayrollParser.Parsed underACaption = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, captioned),
                PayrollLayouts.typed(), Map.of(), PERIOD);
        assertThat(underACaption.lines()).extracting(PayrollParser.Line::lineNumber).containsExactly(2, 3);
        assertThat(underACaption.rowsNotRead()).as("the caption and the line under it").isEqualTo(2);

        byte[] headed = ("staffNumber,surname,initials,amount\n1,Testerson,BK,174.00\n\n"
                + "staffNumber,surname,initials,amount\n3,Tallyburn,T,174.00\n").getBytes(StandardCharsets.UTF_8);
        PayrollParser.Parsed underHeadings = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, headed),
                PayrollLayouts.typed(), Map.of(), PERIOD);
        assertThat(underHeadings.lines()).extracting(PayrollParser.Line::lineNumber).containsExactly(2);
        assertThat(underHeadings.rowsNotRead()).isEqualTo(2);
    }

    @Test
    @DisplayName("headings with no lines under them are NO_ROWS")
    void noRows() {
        byte[] csv = "staffNumber,surname,amount\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, csv),
                PayrollLayouts.typed(), Map.of(), PERIOD)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.NO_ROWS));
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/payroll/" + name)) {
            if (stream == null) {
                throw new IllegalStateException(name + " is not among the test resources");
            }
            return stream.readAllBytes();
        }
    }
}
