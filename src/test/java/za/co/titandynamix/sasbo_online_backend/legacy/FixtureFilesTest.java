package za.co.titandynamix.sasbo_online_backend.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.RowMapper;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReaderSettings;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceReader;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceRow;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.CleanRow;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RowRules;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.AS_AT;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.CSV_FIXTURE;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.CSV_MAPPING;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.FIXED_WIDTH_FIXTURE;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.FIXED_WIDTH_MAPPING;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.TODAY;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.index;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.limits;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.mapping;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.read;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.resource;

/**
 * The invented legacy files under {@code src/test/resources/migration/} -- a Windows-1252 CSV with CRLF line breaks as
 * an old system exports one, and a fixed-width file with a header and a trailer record -- read through the built-in
 * mappings and every rule, line by line: each line's problems are exactly the ones it was written to have, and between
 * them the files carry a fault of EVERY rule a row can break on its own. (The four that are about two rows -- a number
 * twice in the file, or already on record -- are the check's own statements, and the integration lane proves them on
 * the same file.) Every person in them is invented; every identity number was made with a computed check digit.
 */
@DisplayName("The invented legacy files: a fault of every rule")
class FixtureFilesTest {

    /** What a line was written to have: exactly these problems, or (where one fault drags others along) at least them. */
    private record Expected(boolean exact, List<String> tokens) {
    }

    /** The rules about two rows, which the check's statements find and the integration lane proves. */
    private static final Set<RuleCode> ACROSS_ROWS = EnumSet.of(RuleCode.ID_NUMBER_DUPLICATE_IN_BATCH,
            RuleCode.ID_NUMBER_ALREADY_MEMBER, RuleCode.MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH,
            RuleCode.MEMBERSHIP_NUMBER_ALREADY_USED);

    private static final Map<Integer, Expected> CSV_EXPECTED = Map.ofEntries(
            Map.entry(2, new Expected(true, List.of())),
            Map.entry(3, new Expected(true, List.of())),
            Map.entry(4, new Expected(true, List.of())),
            Map.entry(5, new Expected(true, List.of())),
            Map.entry(6, new Expected(true, List.of("ID_NUMBER_CHECK_DIGIT@identityNumber"))),
            Map.entry(7, new Expected(true, List.of("ID_NUMBER_DATE@identityNumber"))),
            Map.entry(8, new Expected(true, List.of("ID_NUMBER_FORMAT@identityNumber"))),
            Map.entry(9, new Expected(true, List.of("REQUIRED_MISSING@surname"))),
            Map.entry(10, new Expected(true, List.of("REQUIRED_MISSING@initials"))),
            Map.entry(11, new Expected(true, List.of("REQUIRED_MISSING@joinDate"))),
            Map.entry(12, new Expected(true, List.of("REQUIRED_MISSING@membershipNumber"))),
            Map.entry(13, new Expected(true, List.of("MEMBERSHIP_NUMBER_FORMAT@membershipNumber"))),
            Map.entry(14, new Expected(true, List.of("DATE_OF_BIRTH_MISMATCH@dateOfBirth"))),
            Map.entry(15, new Expected(true, List.of("GENDER_MISMATCH@gender"))),
            Map.entry(16, new Expected(true, List.of("PHONE_INVALID@cellTel"))),
            Map.entry(17, new Expected(true, List.of("EMAIL_INVALID@personalEmail"))),
            Map.entry(18, new Expected(true, List.of("JOIN_DATE_IN_FUTURE@joinDate"))),
            Map.entry(19, new Expected(true, List.of("DELETED_WITHOUT_CODE@deletionCode"))),
            Map.entry(20, new Expected(true, List.of("DELETED_WITHOUT_DATE@deletedOn"))),
            Map.entry(21, new Expected(true, List.of("SUSPENDED_WITHOUT_CODE@suspensionCode"))),
            Map.entry(22, new Expected(true, List.of("STATUS_UNKNOWN@status"))),
            Map.entry(23, new Expected(true, List.of("DELETION_CODE_UNKNOWN@deletionCode"))),
            Map.entry(24, new Expected(true, List.of("SUSPENSION_CODE_UNKNOWN@suspensionCode"))),
            Map.entry(25, new Expected(true, List.of("STATUS_DATE_BEFORE_JOINED@deletedOn"))),
            Map.entry(26, new Expected(true, List.of("STATUS_DATE_IN_FUTURE@deletedOn"))),
            Map.entry(27, new Expected(true, List.of("WORKPLACE_UNKNOWN@workplaceRaNumber"))),
            Map.entry(28, new Expected(true, List.of("WORKPLACE_MISSING@workplaceRaNumber"))),
            Map.entry(29, new Expected(true, List.of("TABLE_CODE_UNKNOWN@tableCode"))),
            Map.entry(30, new Expected(true, List.of("TABLE_CODE_DISAGREES@tableCode"))),
            Map.entry(31, new Expected(true, List.of("AREA_UNKNOWN@areaLetter"))),
            Map.entry(32, new Expected(true, List.of("AREA_DISAGREES@areaLetter"))),
            Map.entry(33, new Expected(true, List.of("DEBIT_ORDER_WITHOUT_ACCOUNT@bankAccountNumber"))),
            Map.entry(34, new Expected(true, List.of("ACCOUNT_NUMBER_INVALID@bankAccountNumber"))),
            Map.entry(35, new Expected(true, List.of("DEBIT_DAY_INVALID@debitDay"))),
            Map.entry(36, new Expected(true, List.of("AMOUNT_INVALID@monthlySubscription"))),
            Map.entry(37, new Expected(true, List.of("CODE_UNKNOWN@gender"))),
            Map.entry(38, new Expected(true, List.of("DATE_OF_BIRTH_REQUIRED@dateOfBirth"))),
            Map.entry(39, new Expected(true, List.of("IDENTITY_MISSING@identityNumber"))),
            Map.entry(40, new Expected(true, List.of("DATE_UNREADABLE@joinDate"))),
            Map.entry(41, new Expected(true, List.of("DATE_OF_BIRTH_UNREADABLE@dateOfBirth"))),
            Map.entry(42, new Expected(true, List.of("SUSPENSION_DATE_MISSING@suspendedOn"))),
            Map.entry(43, new Expected(true, List.of("CODE_ON_ACTIVE_MEMBER@deletionCode"))),
            Map.entry(44, new Expected(true, List.of("FIELD_TOO_LONG@address"))),
            Map.entry(45, new Expected(false, List.of("ROW_MALFORMED"))),
            Map.entry(46, new Expected(true, List.of("ROW_SHORT"))),
            Map.entry(47, new Expected(true, List.of())),
            Map.entry(48, new Expected(true, List.of())),
            Map.entry(49, new Expected(true, List.of())),
            Map.entry(50, new Expected(true, List.of())),
            Map.entry(51, new Expected(true, List.of())),
            Map.entry(52, new Expected(true, List.of())),
            Map.entry(53, new Expected(false, List.of("FIELD_TOO_LONG@ADDRESS", "FIELD_TOO_LONG@address"))),
            Map.entry(54, new Expected(false, List.of("ROW_MALFORMED"))));

    private static final Map<Integer, Expected> FIXED_WIDTH_EXPECTED = Map.ofEntries(
            Map.entry(2, new Expected(true, List.of())),
            Map.entry(3, new Expected(true, List.of())),
            Map.entry(4, new Expected(true, List.of("ID_NUMBER_CHECK_DIGIT@identityNumber"))),
            Map.entry(5, new Expected(true, List.of("DATE_UNREADABLE@joinDate"))),
            Map.entry(6, new Expected(true, List.of("STATUS_UNKNOWN@status"))),
            Map.entry(7, new Expected(true, List.of("ROW_SHORT", "REQUIRED_MISSING@status",
                    "WORKPLACE_MISSING@workplaceRaNumber"))),
            Map.entry(8, new Expected(true, List.of())),
            Map.entry(9, new Expected(true, List.of())));

    /** Each line's row and its checked result, in the file's order. */
    private static Map<Integer, CleanRow> check(String fixture, MappingDefinition mapping, ReaderSettings settings)
            throws IOException {
        List<SourceRow> rows = read(SourceReader.of(settings.format()), resource(fixture), settings, limits());
        Map<Integer, CleanRow> checked = new TreeMap<>();
        RowMapper mapper = new RowMapper(mapping);
        RowRules rules = new RowRules(mapping, index(), TODAY, AS_AT);
        for (SourceRow row : rows) {
            checked.put(row.rowNumber(), rules.check(row.problems(), mapper.map(row.values())));
        }
        return checked;
    }

    private static List<String> tokens(CleanRow row) {
        List<String> tokens = new ArrayList<>();
        row.errors().forEach(problem -> tokens.add(problem.token()));
        row.warnings().forEach(problem -> tokens.add(problem.token()));
        return tokens;
    }

    private static void assertLines(Map<Integer, CleanRow> checked, Map<Integer, Expected> expected) {
        assertThat(checked.keySet()).as("the lines read as rows").containsExactlyInAnyOrderElementsOf(expected.keySet());
        Map<Integer, List<String>> wrong = new LinkedHashMap<>();
        expected.forEach((line, wanted) -> {
            List<String> found = tokens(checked.get(line));
            boolean ok = wanted.exact()
                    ? found.size() == wanted.tokens().size() && found.containsAll(wanted.tokens())
                    : found.containsAll(wanted.tokens());
            if (!ok) {
                wrong.put(line, found);
            }
        });
        assertThat(wrong).as("lines whose problems are not the ones they were written to have (line -> found)").isEmpty();
    }

    @Test
    @DisplayName("the CSV: each of its 53 lines has exactly the problems it was written to have")
    void theCsv() throws IOException {
        MappingDefinition mapping = mapping(CSV_MAPPING);

        assertLines(check(CSV_FIXTURE, mapping, mapping.format()), CSV_EXPECTED);
    }

    @Test
    @DisplayName("the fixed-width file: its header, trailer and blank line pass, and each record has the problems it was written to have")
    void theFixedWidthFile() throws IOException {
        MappingDefinition mapping = mapping(FIXED_WIDTH_MAPPING);

        assertLines(check(FIXED_WIDTH_FIXTURE, mapping, mapping.format()), FIXED_WIDTH_EXPECTED);
    }

    @Test
    @DisplayName("Windows-1252 decoded as Windows-1252: Zoë on the CSV, Müller-Example on the fixed-width file")
    void windows1252() throws IOException {
        MappingDefinition csv = mapping(CSV_MAPPING);
        MappingDefinition fixed = mapping(FIXED_WIDTH_MAPPING);

        assertThat(check(CSV_FIXTURE, csv, csv.format()).get(3).values())
                .containsEntry(za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField.FIRST_NAMES, "Zoë");
        assertThat(check(FIXED_WIDTH_FIXTURE, fixed, fixed.format()).get(9).values())
                .containsEntry(za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField.SURNAME, "Müller-Example");
    }

    @Test
    @DisplayName("ENCODING_SUSPECT: the same CSV read as UTF-8 says so on the line with the accented name")
    void readAsUtf8() throws IOException {
        MappingDefinition csv = mapping(CSV_MAPPING);
        ReaderSettings utf8 = new ReaderSettings(csv.format().format(), StandardCharsets.UTF_8, csv.format().delimiter(),
                csv.format().quote(), csv.format().header(), 0, null, List.of(), List.of());

        assertThat(tokens(check(CSV_FIXTURE, csv, utf8).get(3))).contains("ENCODING_SUSPECT");
    }

    @Test
    @DisplayName("between them the files break every rule a row can break on its own -- none is untested by a file")
    void everyRuleIsBroken() throws IOException {
        MappingDefinition csv = mapping(CSV_MAPPING);
        MappingDefinition fixed = mapping(FIXED_WIDTH_MAPPING);
        ReaderSettings utf8 = new ReaderSettings(csv.format().format(), StandardCharsets.UTF_8, csv.format().delimiter(),
                csv.format().quote(), csv.format().header(), 0, null, List.of(), List.of());
        Set<RuleCode> broken = EnumSet.noneOf(RuleCode.class);
        for (Map<Integer, CleanRow> file : List.of(check(CSV_FIXTURE, csv, csv.format()),
                check(FIXED_WIDTH_FIXTURE, fixed, fixed.format()), check(CSV_FIXTURE, csv, utf8))) {
            for (CleanRow row : file.values()) {
                row.errors().stream().map(Problem::code).forEach(broken::add);
                row.warnings().stream().map(Problem::code).forEach(broken::add);
            }
        }

        Set<RuleCode> unbroken = EnumSet.complementOf(EnumSet.copyOf(broken));
        unbroken.removeAll(ACROSS_ROWS);
        assertThat(unbroken).as("rules no fixture line breaks").isEmpty();
    }
}
