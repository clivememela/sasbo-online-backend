package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mapping applied to a row: a column found by its name, its spelling or its position; transforms in order; a blank
 * given its default; a code through its list -- an unknown one REPORTED, never dropped silently; a date read strictly
 * into ISO, the old system's spaced dates included.
 */
@DisplayName("RowMapper")
class RowMapperTest {

    private final MappingParser parser = new MappingParser();

    private MappingDefinition mapping(String fields, String codes) {
        return parser.parse("""
                {"key": "row-mapper", "name": "Row mapper",
                 "format": {"type": "CSV"},
                 "fields": {"membershipNumber": {"column": "NO"}, "surname": {"column": "SURNAME"},
                            "initials": {"column": "INITIALS"}, "identityNumber": {"column": "ID"},
                            "joinDate": {"column": "JOINED", "transforms": ["removeSpaces"],
                                         "dateFormats": ["d/M/yyyy", "yyyy/MM/dd"]}%s},
                 "codes": {%s}}
                """.formatted(fields, codes));
    }

    private static Map<String, String> row(String... keysAndValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            row.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    @Test
    @DisplayName("finds a column by its exact name, then ignoring case and spacing, then by position")
    void findsColumns() {
        MappingDefinition mapping = mapping(", \"title\": {\"column\": \"#2\"}", "");
        MappedRow mapped = new RowMapper(mapping).map(row("no", "123", "Title Text", "Mr", "  surname ", "Example"));

        assertThat(mapped.values()).containsEntry(MemberField.MEMBERSHIP_NUMBER, "123")
                .containsEntry(MemberField.SURNAME, "Example")
                .containsEntry(MemberField.TITLE, "Mr");
    }

    @Test
    @DisplayName("a column the row does not have, and a blank value, are absent -- the rules say what absent means")
    void absent() {
        MappedRow mapped = new RowMapper(mapping("", "")).map(row("NO", "1", "SURNAME", "   "));

        assertThat(mapped.values()).containsOnlyKeys(MemberField.MEMBERSHIP_NUMBER);
        assertThat(mapped.problems()).isEmpty();
    }

    @Test
    @DisplayName("a blank is given the mapping's default")
    void defaults() {
        MappingDefinition mapping = mapping(", \"paymentType\": {\"column\": \"PAY\", \"default\": \"Payroll Deduction\"}", "");

        assertThat(new RowMapper(mapping).map(row("PAY", "")).values())
                .containsEntry(MemberField.PAYMENT_TYPE, "Payroll Deduction");
        assertThat(new RowMapper(mapping).map(row("PAY", "Debit Order")).values())
                .containsEntry(MemberField.PAYMENT_TYPE, "Debit Order");
    }

    @Test
    @DisplayName("a code through its list, ignoring case and spacing; an unknown one is CODE_UNKNOWN, and the field is not moved")
    void codes() {
        MappingDefinition mapping = mapping(", \"gender\": {\"column\": \"G\", \"codes\": \"gender\"}",
                "\"gender\": {\"M\": \"Male\", \"F\": \"Female\"}");

        assertThat(new RowMapper(mapping).map(row("G", " m ")).values()).containsEntry(MemberField.GENDER, "Male");
        MappedRow unknown = new RowMapper(mapping).map(row("G", "Q"));
        assertThat(unknown.values()).doesNotContainKey(MemberField.GENDER);
        assertThat(unknown.problems()).containsExactly(new Problem(RuleCode.CODE_UNKNOWN, "gender"));
    }

    @Test
    @DisplayName("STATUS_UNKNOWN: a status the list does not know -- an error, because no member can be moved without one")
    void statusUnknown() {
        MappingDefinition mapping = mapping(", \"status\": {\"column\": \"S\", \"codes\": \"status\"}",
                "\"status\": {\"A\": \"ACTIVE\"}");

        assertThat(new RowMapper(mapping).map(row("S", "X")).problems())
                .containsExactly(new Problem(RuleCode.STATUS_UNKNOWN, "status"));
    }

    @ParameterizedTest(name = "{0} reads as {1}")
    @CsvSource({
        "' 9/ 9/2026', 2026-09-09",
        "'15/ 3/2026', 2026-03-15",
        "'2026/08/31', 2026-08-31",
        "'01/12/1999', 1999-12-01"})
    @DisplayName("a date through the formats in order -- the old system's spaced day-first dates included -- as ISO")
    void dates(String raw, String iso) {
        assertThat(new RowMapper(mapping("", "")).map(row("JOINED", raw)).values()).containsEntry(MemberField.JOIN_DATE, iso);
    }

    @Test
    @DisplayName("DATE_UNREADABLE: a date no format reads -- 31 February included -- is reported and not moved")
    void unreadableDate() {
        for (String raw : new String[] {"31/2/2026", "31/31/2020", "yesterday"}) {
            MappedRow mapped = new RowMapper(mapping("", "")).map(row("JOINED", raw));
            assertThat(mapped.values()).as(raw).doesNotContainKey(MemberField.JOIN_DATE);
            assertThat(mapped.problems()).as(raw).containsExactly(new Problem(RuleCode.DATE_UNREADABLE, "joinDate"));
        }
    }

    @ParameterizedTest(name = "{0}: ''{1}'' becomes ''{2}''")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
        "trim|  Ann  |Ann",
        "upper|abc|ABC",
        "lower|ABC|abc",
        "title|o'BRIEN-SMITH van der MERWE|O'Brien-Smith Van Der Merwe",
        "collapseSpaces|A   B  C|A B C",
        "removeSpaces| 9/ 9/2026|9/9/2026",
        "digitsOnly|082-123 4567|0821234567",
        "stripLeadingZeros|000123|123",
        "stripLeadingZeros|0000|0",
        "stripLeadingZeros|A0012|A0012"})
    @DisplayName("each transform does one plain thing to the text")
    void transforms(String transform, String value, String expected) {
        assertThat(Transform.fromWire(transform).orElseThrow().apply(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("transforms apply in the order the mapping lists them")
    void transformOrder() {
        MappingDefinition mapping = mapping(", \"title\": {\"column\": \"T\", \"transforms\": [\"digitsOnly\", \"stripLeadingZeros\"]}",
                "");

        assertThat(new RowMapper(mapping).map(row("T", "No. 007")).values()).containsEntry(MemberField.TITLE, "7");
    }
}
