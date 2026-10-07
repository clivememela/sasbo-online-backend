package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingParser;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.RowMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.AS_AT;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.CSV_MAPPING;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.TODAY;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.index;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.mapping;

/**
 * Every rule a row is put through, each by its code, through the built-in CSV mapping and SASBO's lists as the fixtures
 * have them: a member who passes everything, then that member with one thing wrong at a time. A rule that cleans is
 * shown cleaning; a rule SASBO decides is shown deciding by the mapping.
 */
@DisplayName("RowRules: every rule by its code")
class RowRulesTest {

    /** Synthetic: born 27 April 1994, male, its check digit right (the old fixture number's, corrected -- MemberFixtureTest). */
    private static final String VALID_ID = "9404275800089";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Map<String, String> member() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("MEMBERSHIP NO", "700002");
        row.put("TITLE", "mr");
        row.put("INITIALS", "b e");
        row.put("FIRST NAMES", "ben");
        row.put("SURNAME", "EXAMPLE");
        row.put("ID NUMBER", VALID_ID);
        row.put("DATE JOINED", " 1/ 3/2015");
        row.put("STATUS", "A");
        row.put("R/A NO", "990001");
        row.put("PAYMENT METHOD", "QPAC");
        row.put("SUBSCRIPTION", "174.00");
        row.put("CELL NO", "082 000 0101");
        row.put("E-MAIL", "ben@example.org");
        return row;
    }

    private static Map<String, String> member(String... changes) {
        Map<String, String> row = member();
        for (int i = 0; i + 1 < changes.length; i += 2) {
            row.put(changes[i], changes[i + 1]);
        }
        return row;
    }

    private static CleanRow check(Map<String, String> raw) {
        return check(raw, mapping(CSV_MAPPING));
    }

    private static CleanRow check(Map<String, String> raw, MappingDefinition mapping) {
        return new RowRules(mapping, index(), TODAY, AS_AT).check(List.of(), new RowMapper(mapping).map(raw));
    }

    private static List<String> tokens(CleanRow row) {
        List<String> tokens = new ArrayList<>();
        row.errors().forEach(problem -> tokens.add(problem.token()));
        row.warnings().forEach(problem -> tokens.add(problem.token()));
        return tokens;
    }

    /** The built-in CSV mapping with one change. */
    private static MappingDefinition variant(Consumer<ObjectNode> change) {
        try {
            ObjectNode node = (ObjectNode) JSON.readTree(mapping(CSV_MAPPING).json());
            change.accept(node);
            return new MappingParser().parse(JSON.writeValueAsString(node));
        } catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static void assertOnly(CleanRow row, Outcome outcome, String... tokens) {
        assertThat(tokens(row)).containsExactly(tokens);
        assertThat(row.outcome()).isEqualTo(outcome);
    }

    // ------------------------------------------------------------------------------------------------ a clean member

    @Test
    @DisplayName("a member who passes every rule is VALID -- cleaned: names, number, date of birth and gender from the identity number, +27")
    void aCleanMember() {
        CleanRow row = check(member());

        assertOnly(row, Outcome.VALID);
        assertThat(row.values())
                .containsEntry(MemberField.MEMBERSHIP_NUMBER, "M-700002")
                .containsEntry(MemberField.TITLE, "Mr")
                .containsEntry(MemberField.INITIALS, "BE")
                .containsEntry(MemberField.FIRST_NAMES, "Ben")
                .containsEntry(MemberField.SURNAME, "Example")
                .containsEntry(MemberField.IDENTITY_NUMBER, VALID_ID)
                .containsEntry(MemberField.DATE_OF_BIRTH, "1994-04-27")
                .containsEntry(MemberField.GENDER, "Male")
                .containsEntry(MemberField.JOIN_DATE, "2015-03-01")
                .containsEntry(MemberField.CELL_TEL, "+27820000101")
                .containsEntry(MemberField.PAYMENT_TYPE, "Payroll Deduction")
                .containsEntry(MemberField.MONTHLY_SUBSCRIPTION, "174.00");
        assertThat(row.memberStatus()).isEqualTo("Active");
        assertThat(row.statusKind()).isEqualTo("N");
        assertThat(row.identityType()).isEqualTo("I.D. Number");
        assertThat(row.identityKey()).isEqualTo(VALID_ID);
        assertThat(row.membershipKey()).isEqualTo("M-700002");
        assertThat(row.membershipSeq()).isEqualTo(700002L);
        assertThat(row.workplaceId()).isEqualTo(1L);
        assertThat(row.areaLetter()).isEqualTo("D");
        assertThat(row.tableCode()).isEqualTo(13);
        assertThat(row.joinDate()).isEqualTo(LocalDate.of(2015, 3, 1));
    }

    // ------------------------------------------------------------------------------------------------ who

    @Test
    @DisplayName("REQUIRED_MISSING: a surname, initials or first names, a date joined, a membership number, a status")
    void requiredMissing() {
        assertOnly(check(member("SURNAME", "")), Outcome.ERROR, "REQUIRED_MISSING@surname");
        assertOnly(check(member("INITIALS", "", "FIRST NAMES", "")), Outcome.ERROR, "REQUIRED_MISSING@initials");
        assertOnly(check(member("INITIALS", "")), Outcome.VALID);
        assertOnly(check(member("DATE JOINED", "")), Outcome.ERROR, "REQUIRED_MISSING@joinDate");
        assertOnly(check(member("MEMBERSHIP NO", "")), Outcome.ERROR, "REQUIRED_MISSING@membershipNumber");
        assertOnly(check(member("STATUS", "")), Outcome.ERROR, "REQUIRED_MISSING@status");
    }

    @Test
    @DisplayName("IDENTITY_MISSING: neither an identity number nor a passport number")
    void identityMissing() {
        assertOnly(check(member("ID NUMBER", "")), Outcome.ERROR, "IDENTITY_MISSING@identityNumber");
    }

    @Test
    @DisplayName("ID_NUMBER_FORMAT, ID_NUMBER_DATE, ID_NUMBER_CHECK_DIGIT: the first flaw of the number, an error")
    void idNumberFlaws() {
        assertOnly(check(member("ID NUMBER", "940427580008")), Outcome.ERROR, "ID_NUMBER_FORMAT@identityNumber");
        assertOnly(check(member("ID NUMBER", "9413275800086")), Outcome.ERROR, "ID_NUMBER_DATE@identityNumber");
        assertOnly(check(member("ID NUMBER", "9404275800087")), Outcome.ERROR, "ID_NUMBER_CHECK_DIGIT@identityNumber");
    }

    @Test
    @DisplayName("MIG-21: where SASBO moves such members and lists them, a bad identity number is a WARNING -- and is not written")
    void idNumberDowngraded() {
        MappingDefinition moveAndList = variant(node -> node.putObject("severity").put("ID_NUMBER_CHECK_DIGIT", "WARNING"));

        CleanRow row = check(member("ID NUMBER", "9404275800087"), moveAndList);

        assertOnly(row, Outcome.WARNING, "ID_NUMBER_CHECK_DIGIT@identityNumber");
        assertThat(row.values()).doesNotContainKey(MemberField.IDENTITY_NUMBER);
        assertThat(row.identityKey()).isNull();
    }

    @Test
    @DisplayName("DATE_OF_BIRTH_MISMATCH and GENDER_MISMATCH: the identity number's are used, and the difference listed")
    void identityMismatches() {
        CleanRow dob = check(member("DATE OF BIRTH", "1/1/1990"));
        assertOnly(dob, Outcome.WARNING, "DATE_OF_BIRTH_MISMATCH@dateOfBirth");
        assertThat(dob.values()).containsEntry(MemberField.DATE_OF_BIRTH, "1994-04-27");

        CleanRow gender = check(member("GENDER", "F"));
        assertOnly(gender, Outcome.WARNING, "GENDER_MISMATCH@gender");
        assertThat(gender.values()).containsEntry(MemberField.GENDER, "Male");

        assertOnly(check(member("DATE OF BIRTH", "27/4/1994", "GENDER", "M")), Outcome.VALID);
    }

    @Test
    @DisplayName("DATE_OF_BIRTH_UNREADABLE: with a valid identity number an unreadable date of birth is only a warning")
    void dateOfBirthUnreadable() {
        assertOnly(check(member("DATE OF BIRTH", "abc")), Outcome.WARNING, "DATE_OF_BIRTH_UNREADABLE@dateOfBirth");
        assertOnly(check(member("ID NUMBER", "", "PASSPORT NO", "A1234567", "DATE OF BIRTH", "abc")), Outcome.ERROR,
                "DATE_UNREADABLE@dateOfBirth");
    }

    @Test
    @DisplayName("DATE_OF_BIRTH_REQUIRED: a passport holder needs a date of birth; with one, they move with the passport number")
    void passportHolders() {
        assertOnly(check(member("ID NUMBER", "", "PASSPORT NO", "a1234567")), Outcome.ERROR,
                "DATE_OF_BIRTH_REQUIRED@dateOfBirth");

        CleanRow row = check(member("ID NUMBER", "", "PASSPORT NO", "a1234567", "DATE OF BIRTH", "2/2/1980", "GENDER", "F"));
        assertOnly(row, Outcome.VALID);
        assertThat(row.identityType()).isEqualTo("Passport Number");
        assertThat(row.values()).containsEntry(MemberField.PASSPORT_NUMBER, "A1234567")
                .containsEntry(MemberField.DATE_OF_BIRTH, "1980-02-02").containsEntry(MemberField.GENDER, "Female");
    }

    @Test
    @DisplayName("CODE_UNKNOWN: a gender the list does not know is listed and not moved")
    void codeUnknown() {
        CleanRow row = check(member("GENDER", "Q"));
        assertOnly(row, Outcome.WARNING, "CODE_UNKNOWN@gender");
        assertThat(row.values()).containsEntry(MemberField.GENDER, "Male");
    }

    // ------------------------------------------------------------------------------------------------ the membership

    @Test
    @DisplayName("MEMBERSHIP_NUMBER_FORMAT: not digits; a number already in our form is read without its prefix, noughts dropped")
    void membershipNumbers() {
        assertOnly(check(member("MEMBERSHIP NO", "AB12")), Outcome.ERROR, "MEMBERSHIP_NUMBER_FORMAT@membershipNumber");
        assertOnly(check(member("MEMBERSHIP NO", "1234567890")), Outcome.ERROR, "MEMBERSHIP_NUMBER_FORMAT@membershipNumber");
        CleanRow row = check(member("MEMBERSHIP NO", "m-000700002"));
        assertThat(row.membershipKey()).isEqualTo("M-700002");
        assertThat(row.membershipSeq()).isEqualTo(700002L);
    }

    @Test
    @DisplayName("a file with no membership numbers, where the mapping allocates ours, needs none")
    void allocated() {
        MappingDefinition allocating = variant(node -> {
            ((ObjectNode) node.get("fields")).remove("membershipNumber");
            node.putObject("membershipNumber").put("allocate", true);
        });

        CleanRow row = check(member(), allocating);
        assertOnly(row, Outcome.VALID);
        assertThat(row.membershipKey()).isNull();
    }

    @Test
    @DisplayName("JOIN_DATE_IN_FUTURE: a date joined after today")
    void joinDateInFuture() {
        assertOnly(check(member("DATE JOINED", "16/9/2026")), Outcome.ERROR, "JOIN_DATE_IN_FUTURE@joinDate");
        assertOnly(check(member("DATE JOINED", "15/9/2026")), Outcome.VALID);
    }

    // ------------------------------------------------------------------------------------------------ status

    @Test
    @DisplayName("a deleted member: SASBO's code and date, a leaving status by the mapping's list -- 1 Retired, 2 Resigned, the rest Terminated")
    void deletedMembers() {
        CleanRow retired = check(member("STATUS", "D", "DELETION CODE", "1", "DATE DELETED", "30/6/2026"));
        assertOnly(retired, Outcome.VALID);
        assertThat(retired.memberStatus()).isEqualTo("Retired");
        assertThat(retired.statusKind()).isEqualTo("L");
        assertThat(retired.deletionCode()).isEqualTo(1);
        assertThat(retired.deletionCodeId()).isEqualTo(101L);
        assertThat(retired.leftOn()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(retired.statusChangedOn()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(check(member("STATUS", "D", "DELETION CODE", "2", "DATE DELETED", "30/6/2026")).memberStatus())
                .isEqualTo("Resigned");
        assertThat(check(member("STATUS", "D", "DELETION CODE", "4", "DATE DELETED", "30/6/2026")).memberStatus())
                .isEqualTo("Terminated");
    }

    @Test
    @DisplayName("DELETED_WITHOUT_CODE, DELETED_WITHOUT_DATE, DELETION_CODE_UNKNOWN")
    void deletedFaults() {
        assertOnly(check(member("STATUS", "D", "DATE DELETED", "30/6/2026")), Outcome.ERROR,
                "DELETED_WITHOUT_CODE@deletionCode");
        assertOnly(check(member("STATUS", "D", "DELETION CODE", "1")), Outcome.ERROR, "DELETED_WITHOUT_DATE@deletedOn");
        assertOnly(check(member("STATUS", "D", "DELETION CODE", "77", "DATE DELETED", "30/6/2026")), Outcome.ERROR,
                "DELETION_CODE_UNKNOWN@deletionCode");
        assertOnly(check(member("STATUS", "D", "DELETION CODE", "one", "DATE DELETED", "30/6/2026")), Outcome.ERROR,
                "DELETION_CODE_UNKNOWN@deletionCode");
    }

    @Test
    @DisplayName("STATUS_DATE_IN_FUTURE and STATUS_DATE_BEFORE_JOINED: a history that reads forwards, for a deletion and a suspension")
    void statusDates() {
        assertOnly(check(member("STATUS", "D", "DELETION CODE", "1", "DATE DELETED", "1/1/2027")), Outcome.ERROR,
                "STATUS_DATE_IN_FUTURE@deletedOn");
        assertOnly(check(member("STATUS", "D", "DELETION CODE", "1", "DATE DELETED", "1/1/2014")), Outcome.ERROR,
                "STATUS_DATE_BEFORE_JOINED@deletedOn");
        assertOnly(check(member("STATUS", "S", "SUSPENSION CODE", "11", "DATE SUSPENDED", "1/1/2027")), Outcome.ERROR,
                "STATUS_DATE_IN_FUTURE@suspendedOn");
        assertOnly(check(member("STATUS", "S", "SUSPENSION CODE", "11", "DATE SUSPENDED", "1/1/2014")), Outcome.ERROR,
                "STATUS_DATE_BEFORE_JOINED@suspendedOn");
    }

    @Test
    @DisplayName("a suspended member: SASBO's code, Suspended; SUSPENDED_WITHOUT_CODE, SUSPENSION_CODE_UNKNOWN")
    void suspendedMembers() {
        CleanRow row = check(member("STATUS", "S", "SUSPENSION CODE", "11", "DATE SUSPENDED", "1/6/2026"));
        assertOnly(row, Outcome.VALID);
        assertThat(row.memberStatus()).isEqualTo("Suspended");
        assertThat(row.statusKind()).isEqualTo("S");
        assertThat(row.suspensionCodeId()).isEqualTo(211L);
        assertThat(row.statusChangedOn()).isEqualTo(LocalDate.of(2026, 6, 1));

        assertOnly(check(member("STATUS", "S", "DATE SUSPENDED", "1/6/2026")), Outcome.ERROR,
                "SUSPENDED_WITHOUT_CODE@suspensionCode");
        assertOnly(check(member("STATUS", "S", "SUSPENSION CODE", "99", "DATE SUSPENDED", "1/6/2026")), Outcome.ERROR,
                "SUSPENSION_CODE_UNKNOWN@suspensionCode");
    }

    @Test
    @DisplayName("SUSPENSION_DATE_MISSING: suspended with no date -- the date the export represents is used, and it is listed")
    void suspensionDateMissing() {
        CleanRow row = check(member("STATUS", "S", "SUSPENSION CODE", "13"));
        assertOnly(row, Outcome.WARNING, "SUSPENSION_DATE_MISSING@suspendedOn");
        assertThat(row.statusChangedOn()).isEqualTo(AS_AT);
    }

    @Test
    @DisplayName("STATUS_UNKNOWN: a status the list does not know holds the row back")
    void statusUnknown() {
        assertOnly(check(member("STATUS", "X")), Outcome.ERROR, "STATUS_UNKNOWN@status");
    }

    @Test
    @DisplayName("CODE_ON_ACTIVE_MEMBER: an active member with a deletion or a suspension code -- the code is ignored")
    void codeOnActiveMember() {
        CleanRow row = check(member("DELETION CODE", "4"));
        assertOnly(row, Outcome.WARNING, "CODE_ON_ACTIVE_MEMBER@deletionCode");
        assertThat(row.memberStatus()).isEqualTo("Active");
        assertOnly(check(member("SUSPENSION CODE", "11")), Outcome.WARNING, "CODE_ON_ACTIVE_MEMBER@suspensionCode");
    }

    @Test
    @DisplayName("with no status column, SASBO's codes decide: a deletion code is a deleted member, a suspension code a suspended one")
    void statusFromTheCodes() {
        MappingDefinition noStatus = variant(node -> ((ObjectNode) node.get("fields")).remove("status"));

        assertThat(check(member("STATUS", "", "DELETION CODE", "2", "DATE DELETED", "1/5/2026"), noStatus).memberStatus())
                .isEqualTo("Resigned");
        assertThat(check(member("STATUS", "", "SUSPENSION CODE", "19", "DATE SUSPENDED", "1/5/2026"), noStatus)
                .memberStatus()).isEqualTo("Suspended");
        assertThat(check(member("STATUS", ""), noStatus).memberStatus()).isEqualTo("Active");
    }

    // ------------------------------------------------------------------------------------------------ where

    @Test
    @DisplayName("WORKPLACE_UNKNOWN: an R/A number not on record -- an error, or a warning where SASBO moves them (MIG-22)")
    void workplaceUnknown() {
        assertOnly(check(member("R/A NO", "999999")), Outcome.ERROR, "WORKPLACE_UNKNOWN@workplaceRaNumber");
        MappingDefinition holding = variant(node -> node.putObject("severity").put("WORKPLACE_UNKNOWN", "WARNING"));
        CleanRow row = check(member("R/A NO", "999999"), holding);
        assertOnly(row, Outcome.WARNING, "WORKPLACE_UNKNOWN@workplaceRaNumber");
        assertThat(row.workplaceId()).isNull();
    }

    @Test
    @DisplayName("WORKPLACE_MISSING: no R/A number -- moved with no workplace, and the file's table code and area are kept")
    void workplaceMissing() {
        CleanRow row = check(member("R/A NO", "", "T/C", "24", "AREA", "f"));
        assertOnly(row, Outcome.WARNING, "WORKPLACE_MISSING@workplaceRaNumber");
        assertThat(row.tableCode()).isEqualTo(24);
        assertThat(row.areaLetter()).isEqualTo("F");
    }

    @Test
    @DisplayName("TABLE_CODE_UNKNOWN and TABLE_CODE_DISAGREES: the workplace's table code wins")
    void tableCodes() {
        assertOnly(check(member("T/C", "999")), Outcome.WARNING, "TABLE_CODE_UNKNOWN@tableCode");
        CleanRow row = check(member("T/C", "24"));
        assertOnly(row, Outcome.WARNING, "TABLE_CODE_DISAGREES@tableCode");
        assertThat(row.tableCode()).isEqualTo(13);
        assertOnly(check(member("T/C", "013")), Outcome.VALID);
    }

    @Test
    @DisplayName("AREA_UNKNOWN and AREA_DISAGREES: the workplace's area wins")
    void areas() {
        assertOnly(check(member("AREA", "QQ")), Outcome.WARNING, "AREA_UNKNOWN@areaLetter");
        CleanRow row = check(member("AREA", "B"));
        assertOnly(row, Outcome.WARNING, "AREA_DISAGREES@areaLetter");
        assertThat(row.areaLetter()).isEqualTo("D");
        assertOnly(check(member("AREA", "d")), Outcome.VALID);
    }

    // ------------------------------------------------------------------------------------------------ contact and payment

    @Test
    @DisplayName("PHONE_INVALID and EMAIL_INVALID: listed, and not moved")
    void contact() {
        CleanRow phone = check(member("CELL NO", "12345", "WORK TEL", "011 000 0101"));
        assertOnly(phone, Outcome.WARNING, "PHONE_INVALID@cellTel");
        assertThat(phone.values()).doesNotContainKey(MemberField.CELL_TEL).containsEntry(MemberField.WORK_TEL, "+27110000101");

        CleanRow email = check(member("E-MAIL", "not-an-email"));
        assertOnly(email, Outcome.WARNING, "EMAIL_INVALID@personalEmail");
        assertThat(email.values()).doesNotContainKey(MemberField.PERSONAL_EMAIL);
    }

    @Test
    @DisplayName("DEBIT_ORDER_WITHOUT_ACCOUNT: capture's rule -- a debit-order member needs an account; with one, moved as typed")
    void debitOrders() {
        assertOnly(check(member("PAYMENT METHOD", "ACB")), Outcome.ERROR, "DEBIT_ORDER_WITHOUT_ACCOUNT@bankAccountNumber");
        CleanRow row = check(member("PAYMENT METHOD", "ACB", "ACCOUNT NO", "6200 0000 011", "ACCOUNT TYPE", "s",
                "DEBIT DAY", "25"));
        assertOnly(row, Outcome.VALID);
        assertThat(row.values()).containsEntry(MemberField.PAYMENT_TYPE, "Debit Order")
                .containsEntry(MemberField.BANK_ACCOUNT_NUMBER, "62000000011")
                .containsEntry(MemberField.ACCOUNT_TYPE, "Savings")
                .containsEntry(MemberField.DEBIT_DAY, "25");
    }

    @Test
    @DisplayName("ACCOUNT_NUMBER_INVALID, DEBIT_DAY_INVALID, AMOUNT_INVALID: listed, and not moved")
    void paymentFaults() {
        assertOnly(check(member("ACCOUNT NO", "12AB")), Outcome.WARNING, "ACCOUNT_NUMBER_INVALID@bankAccountNumber");
        assertOnly(check(member("DEBIT DAY", "45")), Outcome.WARNING, "DEBIT_DAY_INVALID@debitDay");
        assertOnly(check(member("DEBIT DAY", "0")), Outcome.WARNING, "DEBIT_DAY_INVALID@debitDay");
        assertOnly(check(member("SUBSCRIPTION", "abc")), Outcome.WARNING, "AMOUNT_INVALID@monthlySubscription");
        assertThat(check(member("SUBSCRIPTION", ".00")).values()).containsEntry(MemberField.MONTHLY_SUBSCRIPTION, "0.00");
        assertThat(check(member("SUBSCRIPTION", "R174,5")).values()).containsEntry(MemberField.MONTHLY_SUBSCRIPTION, "174.50");
    }

    @Test
    @DisplayName("FIELD_TOO_LONG: a value longer than the member record's column holds the row back -- never cut to fit")
    void fieldTooLong() {
        assertOnly(check(member("ADDRESS", "x".repeat(256))), Outcome.ERROR, "FIELD_TOO_LONG@address");
        assertOnly(check(member("ADDRESS", "x".repeat(255))), Outcome.VALID);
    }

    // ------------------------------------------------------------------------------------------------ the mapping decides

    @Test
    @DisplayName("a warning SASBO makes an error holds the row back")
    void upgraded() {
        MappingDefinition strict = variant(node -> node.putObject("severity").put("PHONE_INVALID", "ERROR"));
        assertOnly(check(member("CELL NO", "12345"), strict), Outcome.ERROR, "PHONE_INVALID@cellTel");
    }

    @Test
    @DisplayName("what went wrong reading the row comes first, with its own severity: a short row is a warning")
    void readProblemsFirst() {
        MappingDefinition mapping = mapping(CSV_MAPPING);
        CleanRow row = new RowRules(mapping, index(), TODAY, AS_AT).check(
                List.of(new Problem(RuleCode.ROW_SHORT, null)), new RowMapper(mapping).map(member()));

        assertOnly(row, Outcome.WARNING, "ROW_SHORT");
    }

    @Test
    @DisplayName("every problem once: a code met twice for one field is listed once")
    void eachOnce() {
        MappingDefinition mapping = mapping(CSV_MAPPING);
        CleanRow row = new RowRules(mapping, index(), TODAY, AS_AT).check(
                List.of(new Problem(RuleCode.ROW_SHORT, null), new Problem(RuleCode.ROW_SHORT, null)),
                new RowMapper(mapping).map(member()));

        assertThat(tokens(row)).containsExactly("ROW_SHORT");
    }
}
