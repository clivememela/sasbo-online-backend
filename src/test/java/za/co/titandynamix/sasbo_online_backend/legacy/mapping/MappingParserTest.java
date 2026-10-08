package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceFormat;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Severity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A mapping definition is read strictly: every problem is listed with its path, a misspelt key is a problem and never
 * ignored, and SASBO's choices -- the severities, the deletion statuses, the code lists -- are held to what they may
 * be. The built-in mappings are the first thing it reads.
 */
@DisplayName("MappingParser")
class MappingParserTest {

    private final MappingParser parser = new MappingParser();

    /** A mapping with every field a mapping must have, and nothing else. */
    private static String minimal(String extraFormat, String extraFields, String extraRoot) {
        return """
                {"key": "minimal-csv", "name": "Minimal",
                 "format": {"type": "CSV"%s},
                 "fields": {"membershipNumber": {"column": "NO"}, "surname": {"column": "SURNAME"},
                            "initials": {"column": "INITIALS"}, "identityNumber": {"column": "ID"},
                            "joinDate": {"column": "JOINED", "dateFormats": ["d/M/yyyy"]}%s}%s}
                """.formatted(extraFormat, extraFields, extraRoot);
    }

    private List<String> problems(String definition) {
        try {
            parser.parse(definition);
        } catch (MappingInvalid invalid) {
            return invalid.problems();
        }
        throw new AssertionError("the definition parsed");
    }

    @Test
    @DisplayName("both built-in mappings parse -- the CSV and the fixed-width provisional layouts")
    void builtInsParse() {
        Map<String, MappingDefinition> builtIn = BuiltInMappings.load(parser);

        assertThat(builtIn).containsOnlyKeys("legacy-member-export-csv", "legacy-member-export-fixed-width");
        MappingDefinition csv = builtIn.get("legacy-member-export-csv");
        assertThat(csv.format().format()).isEqualTo(SourceFormat.CSV);
        assertThat(csv.format().charset().name()).isEqualTo("windows-1252");
        assertThat(csv.fields()).containsKeys(MemberField.SURNAME, MemberField.JOIN_DATE, MemberField.STATUS);
        assertThat(builtIn.get("legacy-member-export-fixed-width").format().layout()).hasSize(14);
    }

    @Test
    @DisplayName("defaults: UTF-8, a comma, a double quote, a header row, membership numbers kept as M- and digits")
    void defaults() {
        MappingDefinition mapping = parser.parse(minimal("", "", ""));

        assertThat(mapping.format().charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(mapping.format().delimiter()).isEqualTo(',');
        assertThat(mapping.format().quote()).isEqualTo('"');
        assertThat(mapping.format().header()).isTrue();
        assertThat(mapping.membershipNumber().prefix()).isEqualTo("M-");
        assertThat(mapping.membershipNumber().allocate()).isFalse();
        assertThat(mapping.severityOf(RuleCode.PHONE_INVALID)).isEqualTo(Severity.WARNING);
        assertThat(mapping.json()).contains("\"minimal-csv\"");
    }

    @Test
    @DisplayName("a setting nobody knows is a problem, named by its path -- never ignored, as the API's own reader would")
    void unknownSettings() {
        assertThat(problems(minimal(", \"delimeter\": \";\"", "", ", \"colour\": \"blue\"")))
                .contains("format.delimeter: not a setting a mapping has here", "colour: not a setting a mapping has here");
    }

    @Test
    @DisplayName("every problem is listed, not only the first: an unknown field, transform, and a date format with no year")
    void everyProblemListed() {
        List<String> found = problems(minimal("", """
                , "favouriteColour": {"column": "X"},
                  "title": {"column": "TITLE", "transforms": ["trim", "shout"]},
                  "dateOfBirth": {"column": "DOB", "dateFormats": ["d/M/yy"]}""", ""));

        assertThat(found).contains("fields.favouriteColour: not a member field a migration writes");
        assertThat(found).anyMatch(problem -> problem.startsWith("fields.title.transforms[1]: not a transform"));
        assertThat(found).anyMatch(problem -> problem.startsWith("fields.dateOfBirth.dateFormats[0]: a date format with a four-digit year"));
    }

    @Test
    @DisplayName("date formats only on a date field; a list named that is not under codes; a status list outside ACTIVE, SUSPENDED, DELETED")
    void fieldRules() {
        List<String> found = problems(minimal("", """
                , "employeeNumber": {"column": "EMP", "dateFormats": ["d/M/yyyy"]},
                  "gender": {"column": "G", "codes": "genders"},
                  "status": {"column": "S", "codes": "status"}""",
                ", \"codes\": {\"status\": {\"A\": \"ACTIVE\", \"L\": \"LEFT\"}}"));

        assertThat(found).contains("fields.employeeNumber.dateFormats: only a date field has date formats",
                "fields.gender.codes: no list of that name under codes");
        assertThat(found).anyMatch(problem -> problem.startsWith("fields.status.codes: the list's values must each be one of"));
    }

    @Test
    @DisplayName("the deletion statuses: a deletion code's number or *, to a status a member leaves with")
    void deletionStatus() {
        List<String> found = problems(minimal("", "",
                ", \"codes\": {\"deletionStatus\": {\"1\": \"Retired\", \"x\": \"Resigned\", \"2\": \"Active\"}}"));

        assertThat(found).contains("codes.deletionStatus.X: a deletion code's number, or * for every other code");
        assertThat(found).anyMatch(problem -> problem.startsWith("codes.deletionStatus.2: one of the statuses a member leaves with"));
    }

    @Test
    @DisplayName("severity: an identity-number check may be made a warning (MIG-21); a duplicate may not; any warning may be an error")
    void severities() {
        MappingDefinition mapping = parser.parse(minimal("", "",
                ", \"severity\": {\"ID_NUMBER_CHECK_DIGIT\": \"WARNING\", \"PHONE_INVALID\": \"ERROR\"}"));
        assertThat(mapping.severityOf(RuleCode.ID_NUMBER_CHECK_DIGIT)).isEqualTo(Severity.WARNING);
        assertThat(mapping.severityOf(RuleCode.PHONE_INVALID)).isEqualTo(Severity.ERROR);

        assertThat(problems(minimal("", "",
                ", \"severity\": {\"ID_NUMBER_DUPLICATE_IN_BATCH\": \"WARNING\", \"NO_SUCH_RULE\": \"ERROR\", \"PHONE_INVALID\": \"LOUD\"}")))
                .contains("severity.ID_NUMBER_DUPLICATE_IN_BATCH: cannot be made a warning: a row with it cannot be moved safely",
                        "severity.NO_SUCH_RULE: not one of the rules' codes", "severity.PHONE_INVALID: ERROR or WARNING");
    }

    @Test
    @DisplayName("fixed width: a layout is required; fields may not overlap or repeat a name; a column must be a field of it")
    void fixedWidth() {
        assertThat(problems("""
                {"key": "fw", "name": "Fixed", "format": {"type": "FIXED_WIDTH"},
                 "fields": {"surname": {"column": "SURNAME"}}}"""))
                .contains("format.layout: required for a fixed-width file: a list of 1 to 200 fields");
        List<String> found = problems("""
                {"key": "fw", "name": "Fixed", "format": {"type": "FIXED_WIDTH", "header": true, "layout": [
                   {"name": "MEMNO", "start": 1, "length": 9}, {"name": "SURNAME", "start": 5, "length": 10},
                   {"name": "memno", "start": 20, "length": 2}]},
                 "fields": {"membershipNumber": {"column": "MEMNO"}, "surname": {"column": "NAME"},
                            "initials": {"column": "MEMNO"}, "identityNumber": {"column": "MEMNO"},
                            "joinDate": {"column": "MEMNO", "dateFormats": ["d/M/yyyy"]}}}""");
        assertThat(found).contains("format.header: a fixed-width file has no header row: use skipRows",
                "format.layout[2].name: the same name twice", "format.layout: fields overlap: MEMNO and SURNAME",
                "fields.surname.column: not a field of the layout");
    }

    @Test
    @DisplayName("a CSV with no header row names its columns by position; an encoding must be one the server knows")
    void positionsAndEncodings() {
        assertThat(problems(minimal(", \"header\": false, \"encoding\": \"klingon\"", "", "")))
                .contains("fields.surname.column: a file with no header row names a column by its position: #1, #2 ...",
                        "format.encoding: not an encoding this server knows (try windows-1252 or UTF-8)");
        assertThat(problems("""
                {"key": "x-xlsx", "name": "Book", "format": {"type": "XLSX", "encoding": "UTF-8", "delimiter": ";"},
                 "fields": {}}"""))
                .contains("format.encoding: a workbook carries its own encoding", "format.delimiter: only a CSV file has a delimiter");
    }

    @Test
    @DisplayName("what every mapping needs: a surname, a date joined, a membership number, an identity, initials or names")
    void requiredFields() {
        assertThat(problems("{\"key\": \"empty\", \"name\": \"Empty\", \"format\": {\"type\": \"CSV\"}, \"fields\": {}}"))
                .contains("fields: needs surname", "fields: needs joinDate",
                        "fields: needs membershipNumber, or membershipNumber.allocate",
                        "fields: needs identityNumber or passportNumber", "fields: needs initials or firstNames");
    }

    @Test
    @DisplayName("a membership number allocated here needs no column; a prefix and pattern are checked")
    void membershipNumbers() {
        String allocated = """
                {"key": "allocated", "name": "Allocated", "format": {"type": "CSV"},
                 "membershipNumber": {"allocate": true, "prefix": "M-", "pattern": "[0-9]{1,7}"},
                 "fields": {"surname": {"column": "S"}, "initials": {"column": "I"}, "passportNumber": {"column": "P"},
                            "joinDate": {"column": "J"}}}""";
        MappingDefinition mapping = parser.parse(allocated);
        assertThat(mapping.membershipNumber().allocate()).isTrue();
        assertThat(mapping.fields().get(MemberField.JOIN_DATE).datePatterns()).containsExactly("yyyy-MM-dd");

        assertThat(problems(minimal("", "", ", \"membershipNumber\": {\"prefix\": \"m_\", \"pattern\": \"[\"}")))
                .contains("membershipNumber.prefix: up to 5 capital letters, digits and hyphens",
                        "membershipNumber.pattern: not a pattern this server can read");
    }

    @Test
    @DisplayName("a key's shape, a setting named twice, a definition that is not JSON, and one too long")
    void wholeDefinition() {
        assertThat(problems(minimal("", "", "").replace("minimal-csv", "Has Spaces")))
                .anyMatch(problem -> problem.startsWith("key: lower-case letters, digits and hyphens"));
        assertThat(problems("{\"key\": \"a\", \"key\": \"b\"}")).containsExactly("the definition is not JSON, or names a setting twice");
        assertThat(problems("not json")).containsExactly("the definition is not JSON, or names a setting twice");
        assertThat(problems(" ")).containsExactly("the definition is empty");
        assertThat(problems("[]")).containsExactly("the definition must be a JSON object");
        assertThat(problems("{\"description\": \"" + "x".repeat(MappingParser.MAX_DEFINITION_CHARS) + "\"}"))
                .containsExactly("the definition is longer than 60000 characters");
    }

    @Test
    @DisplayName("a code list is looked up without regard to case or spacing; the same old value twice is a problem")
    void codeLists() {
        MappingDefinition mapping = parser.parse(minimal("", ", \"status\": {\"column\": \"S\", \"codes\": \"status\"}",
                ", \"codes\": {\"status\": {\"Active Member\": \"ACTIVE\", \"D\": \"DELETED\"}}"));
        assertThat(mapping.lookUp("status", "  active   MEMBER ")).isEqualTo("ACTIVE");
        assertThat(mapping.lookUp("status", "X")).isNull();
        assertThat(mapping.lookUp("absent", "D")).isNull();

        assertThat(problems(minimal("", "", ", \"codes\": {\"status\": {\"a\": \"ACTIVE\", \"A\": \"ACTIVE\"}}")))
                .contains("codes.status.A: the same old value twice, ignoring case and spacing");
    }

    @Test
    @DisplayName("a date format is strict: 31 February is not 3 March")
    void strictDates() {
        assertThatThrownBy(() -> java.time.LocalDate.parse("31/2/2026", MappingParser.dateFormat("d/M/yyyy")))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
        assertThat(java.time.LocalDate.parse("9/9/2026", MappingParser.dateFormat("d/M/yyyy")))
                .isEqualTo(java.time.LocalDate.of(2026, 9, 9));
    }
}
