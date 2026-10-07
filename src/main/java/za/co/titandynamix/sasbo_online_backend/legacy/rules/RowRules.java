package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import za.co.titandynamix.sasbo_online_backend.enums.AccountType;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappedRow;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MembershipNumberRule;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField.*;

/**
 * Every rule a migrated row is put through, in one pass, each producing its {@link RuleCode} -- the cleaning (a
 * telephone number written {@code +27}, a membership number in our form, the date of birth and gender read from the
 * identity number) and the checks (a valid identity number, the fields every member needs, SASBO's codes and dates for a
 * deleted or suspended member, a workplace on record). The rows of one file are checked against one another and against
 * the members on record -- duplicates -- by the check's own statements, not here.
 *
 * <p>What each rule decides is written beside it. Three are SASBO's decisions and are therefore data:
 * <ul>
 *   <li>which leaving status a deletion code gives -- the mapping's {@code deletionStatus} list, by default 1 Retired,
 *       2 Resigned and every other code Terminated, as {@code MemberStatuses} reads them (MM49);</li>
 *   <li>whether a bad identity number or an unknown workplace holds a row back (MIG-21, MIG-22) -- the mapping's
 *       {@code severity};</li>
 *   <li>what the old system's statuses and codes are called -- the mapping's lists.</li>
 * </ul>
 *
 * <p>The day is handed in -- {@code today} for "not in the future", and the date the export represents for a
 * suspension with no date -- never read from the wall clock.
 */
public final class RowRules {

    /** The leaving status a deletion code gives when the mapping's list does not say (MemberStatuses, MM49). */
    static final Map<String, String> DEFAULT_DELETION_STATUS = Map.of(
            "1", MemberStatuses.RETIRED,
            "2", MemberStatuses.RESIGNED,
            "*", MemberStatuses.TERMINATED);

    private static final String ID_NUMBER = "I.D. Number";
    private static final String PASSPORT = "Passport Number";

    private final MappingDefinition mapping;
    private final ReferenceIndex reference;
    private final LocalDate today;
    private final LocalDate asAt;

    public RowRules(MappingDefinition mapping, ReferenceIndex reference, LocalDate today, LocalDate asAt) {
        this.mapping = mapping;
        this.reference = reference;
        this.today = today;
        this.asAt = asAt;
    }

    public CleanRow check(List<Problem> readProblems, MappedRow mapped) {
        Findings found = new Findings();
        readProblems.forEach(found::add);
        mapped.problems().forEach(found::add);
        Map<MemberField, String> in = mapped.values();
        Map<MemberField, String> out = new EnumMap<>(MemberField.class);
        Facts facts = new Facts();

        names(in, out, found);
        identity(in, out, found, facts);
        membershipNumber(in, out, found, facts);
        joinDate(in, out, found, facts);
        status(in, found, facts);
        workplace(in, found, facts);
        contact(in, out, found);
        payment(in, out, found);
        for (Map.Entry<MemberField, String> value : out.entrySet()) {
            if (value.getValue().length() > value.getKey().maxLength()) {
                found.add(RuleCode.FIELD_TOO_LONG, value.getKey().wire());
            }
        }

        List<Problem> errors = new ArrayList<>();
        List<Problem> warnings = new ArrayList<>();
        for (Problem problem : found.problems) {
            (mapping.severityOf(problem.code()) == Severity.ERROR ? errors : warnings).add(problem);
        }
        Outcome outcome = !errors.isEmpty() ? Outcome.ERROR : !warnings.isEmpty() ? Outcome.WARNING : Outcome.VALID;
        return new CleanRow(outcome, errors, warnings, out, facts.memberStatus, facts.statusKind, facts.identityType,
                facts.identityKey, facts.membershipKey, facts.membershipSeq, facts.workplaceId, facts.areaLetter,
                facts.tableCode, facts.deletionCode, facts.deletionCodeId, facts.suspensionCode, facts.suspensionCodeId,
                facts.joinDate, facts.leftOn, facts.statusChangedOn);
    }

    // ------------------------------------------------------------------------------------------------ who

    private static void names(Map<MemberField, String> in, Map<MemberField, String> out, Findings found) {
        for (MemberField field : List.of(TITLE, INITIALS, FIRST_NAMES, SURNAME, RACE, EMPLOYEE_NUMBER, BANK_NAME, ADDRESS,
                REMARKS)) {
            if (in.containsKey(field)) {
                out.put(field, in.get(field));
            }
        }
        if (!in.containsKey(SURNAME)) {
            found.add(RuleCode.REQUIRED_MISSING, SURNAME.wire());
        }
        if (!in.containsKey(INITIALS) && !in.containsKey(FIRST_NAMES)) {
            found.add(RuleCode.REQUIRED_MISSING, INITIALS.wire());
        }
    }

    /**
     * A valid identity number gives the date of birth and the gender, whatever the file says beside it (a difference is
     * a warning); a flawed one is an error -- or, where SASBO chose to move such members and list them (MIG-21), a
     * warning, and the number is then not written. A passport holder needs a date of birth (capture's rule, P3).
     */
    private void identity(Map<MemberField, String> in, Map<MemberField, String> out, Findings found, Facts facts) {
        String id = in.get(IDENTITY_NUMBER);
        String passport = in.get(PASSPORT_NUMBER);
        boolean dobUnreadable = found.has(RuleCode.DATE_UNREADABLE, DATE_OF_BIRTH.wire());
        if (id != null) {
            String digits = id.replaceAll("\\s", "");
            Optional<SaIdNumber.Flaw> flaw = SaIdNumber.flaw(digits, today);
            if (flaw.isPresent()) {
                found.add(switch (flaw.get()) {
                    case FORMAT -> RuleCode.ID_NUMBER_FORMAT;
                    case DATE -> RuleCode.ID_NUMBER_DATE;
                    case CHECK_DIGIT -> RuleCode.ID_NUMBER_CHECK_DIGIT;
                }, IDENTITY_NUMBER.wire());
            } else {
                facts.identityKey = digits;
                facts.identityType = ID_NUMBER;
                out.put(IDENTITY_NUMBER, digits);
                LocalDate born = SaIdNumber.dateOfBirth(digits, today).orElseThrow();
                String given = in.get(DATE_OF_BIRTH);
                if (given != null && !given.equals(born.toString())) {
                    found.add(RuleCode.DATE_OF_BIRTH_MISMATCH, DATE_OF_BIRTH.wire());
                }
                if (dobUnreadable) {
                    found.remove(RuleCode.DATE_UNREADABLE, DATE_OF_BIRTH.wire());
                    found.add(RuleCode.DATE_OF_BIRTH_UNREADABLE, DATE_OF_BIRTH.wire());
                }
                out.put(DATE_OF_BIRTH, born.toString());
                String gender = SaIdNumber.gender(digits);
                String givenGender = gender(in.get(GENDER));
                if (givenGender != null && !givenGender.equals(gender)) {
                    found.add(RuleCode.GENDER_MISMATCH, GENDER.wire());
                }
                out.put(GENDER, gender);
                return;
            }
        }
        if (passport != null) {
            facts.identityType = PASSPORT;
            out.put(PASSPORT_NUMBER, passport);
            if (!in.containsKey(DATE_OF_BIRTH) && !dobUnreadable) {
                found.add(RuleCode.DATE_OF_BIRTH_REQUIRED, DATE_OF_BIRTH.wire());
            }
        } else if (id == null) {
            found.add(RuleCode.IDENTITY_MISSING, IDENTITY_NUMBER.wire());
        }
        if (in.containsKey(DATE_OF_BIRTH)) {
            out.put(DATE_OF_BIRTH, in.get(DATE_OF_BIRTH));
        }
        if (in.containsKey(GENDER)) {
            String gender = gender(in.get(GENDER));
            if (gender == null) {
                found.add(RuleCode.CODE_UNKNOWN, GENDER.wire());
            } else {
                out.put(GENDER, gender);
            }
        }
    }

    /** Male or Female, as the record spells them -- from a list, or from the plain words and their initials. */
    private static String gender(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "MALE", "M" -> "Male";
            case "FEMALE", "F" -> "Female";
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------------ the membership

    /** Kept, in our form ({@code M-} and the digits, leading noughts dropped); or given one of ours where SASBO has none. */
    private void membershipNumber(Map<MemberField, String> in, Map<MemberField, String> out, Findings found,
                                  Facts facts) {
        MembershipNumberRule rule = mapping.membershipNumber();
        String number = in.get(MEMBERSHIP_NUMBER);
        if (number == null) {
            if (!rule.allocate()) {
                found.add(RuleCode.REQUIRED_MISSING, MEMBERSHIP_NUMBER.wire());
            }
            return;
        }
        String bare = number.strip();
        if (!rule.prefix().isEmpty() && bare.regionMatches(true, 0, rule.prefix(), 0, rule.prefix().length())) {
            bare = bare.substring(rule.prefix().length());
        }
        if (!rule.pattern().matcher(bare).matches()) {
            found.add(RuleCode.MEMBERSHIP_NUMBER_FORMAT, MEMBERSHIP_NUMBER.wire());
            return;
        }
        String kept = bare.matches("[0-9]+") ? bare.replaceFirst("^0+(?=.)", "") : bare;
        facts.membershipKey = rule.prefix() + kept;
        facts.membershipSeq = kept.matches("[0-9]{1,18}") ? Long.valueOf(kept) : null;
        out.put(MEMBERSHIP_NUMBER, facts.membershipKey);
    }

    private void joinDate(Map<MemberField, String> in, Map<MemberField, String> out, Findings found, Facts facts) {
        String joined = in.get(JOIN_DATE);
        if (joined == null) {
            if (!found.has(RuleCode.DATE_UNREADABLE, JOIN_DATE.wire())) {
                found.add(RuleCode.REQUIRED_MISSING, JOIN_DATE.wire());
            }
            return;
        }
        facts.joinDate = LocalDate.parse(joined);
        if (facts.joinDate.isAfter(today)) {
            found.add(RuleCode.JOIN_DATE_IN_FUTURE, JOIN_DATE.wire());
        }
        out.put(JOIN_DATE, joined);
    }

    // ------------------------------------------------------------------------------------------------ status

    /**
     * SASBO knows a member as active, SUSPENDED WITH A CODE or DELETED WITH A CODE (prt_del_summ, mem_count). Where the
     * mapping has no status column, the codes decide: a deletion code is a deleted member, a suspension code a suspended
     * one, neither an active one.
     */
    private void status(Map<MemberField, String> in, Findings found, Facts facts) {
        String deletionRaw = in.get(DELETION_CODE);
        String suspensionRaw = in.get(SUSPENSION_CODE);
        String legacy;
        if (mapping.fields().containsKey(STATUS)) {
            legacy = in.get(STATUS) == null ? null : in.get(STATUS).toUpperCase(Locale.ROOT);
            if (legacy == null) {
                if (!found.has(RuleCode.STATUS_UNKNOWN, STATUS.wire())) {
                    found.add(RuleCode.REQUIRED_MISSING, STATUS.wire());
                }
                return;
            }
            if (!List.of("ACTIVE", "SUSPENDED", "DELETED").contains(legacy)) {
                found.add(RuleCode.STATUS_UNKNOWN, STATUS.wire());
                return;
            }
        } else {
            legacy = deletionRaw != null ? "DELETED" : suspensionRaw != null ? "SUSPENDED" : "ACTIVE";
        }
        switch (legacy) {
            case "DELETED" -> deleted(in, deletionRaw, found, facts);
            case "SUSPENDED" -> suspended(in, suspensionRaw, found, facts);
            default -> {
                if (deletionRaw != null || suspensionRaw != null) {
                    found.add(RuleCode.CODE_ON_ACTIVE_MEMBER,
                            deletionRaw != null ? DELETION_CODE.wire() : SUSPENSION_CODE.wire());
                }
                facts.memberStatus = MemberStatuses.ACTIVE;
                facts.statusKind = "N";
            }
        }
    }

    private void deleted(Map<MemberField, String> in, String deletionRaw, Findings found, Facts facts) {
        Integer code = number(deletionRaw);
        if (deletionRaw == null) {
            found.add(RuleCode.DELETED_WITHOUT_CODE, DELETION_CODE.wire());
        } else if (code == null || !reference.deletionCodes().containsKey(code)) {
            found.add(RuleCode.DELETION_CODE_UNKNOWN, DELETION_CODE.wire());
        } else {
            facts.deletionCode = code;
            facts.deletionCodeId = reference.deletionCodes().get(code);
        }
        String deleted = in.get(DELETED_ON);
        if (deleted == null) {
            if (!found.has(RuleCode.DATE_UNREADABLE, DELETED_ON.wire())) {
                found.add(RuleCode.DELETED_WITHOUT_DATE, DELETED_ON.wire());
            }
        } else {
            facts.leftOn = LocalDate.parse(deleted);
            statusDate(facts.leftOn, DELETED_ON, found, facts);
            facts.statusChangedOn = facts.leftOn;
        }
        facts.memberStatus = leavingStatus(facts.deletionCode);
        facts.statusKind = "L";
    }

    private void suspended(Map<MemberField, String> in, String suspensionRaw, Findings found, Facts facts) {
        Integer code = number(suspensionRaw);
        if (suspensionRaw == null) {
            found.add(RuleCode.SUSPENDED_WITHOUT_CODE, SUSPENSION_CODE.wire());
        } else if (code == null || !reference.suspensionCodes().containsKey(code)) {
            found.add(RuleCode.SUSPENSION_CODE_UNKNOWN, SUSPENSION_CODE.wire());
        } else {
            facts.suspensionCode = code;
            facts.suspensionCodeId = reference.suspensionCodes().get(code);
        }
        String since = in.get(SUSPENDED_ON);
        if (since == null) {
            if (!found.has(RuleCode.DATE_UNREADABLE, SUSPENDED_ON.wire())) {
                found.add(RuleCode.SUSPENSION_DATE_MISSING, SUSPENDED_ON.wire());
            }
            facts.statusChangedOn = asAt;
        } else {
            facts.statusChangedOn = LocalDate.parse(since);
            statusDate(facts.statusChangedOn, SUSPENDED_ON, found, facts);
        }
        facts.memberStatus = MemberStatuses.SUSPENDED;
        facts.statusKind = "S";
    }

    /** A status's date is not after today, and not before the member joined -- or the history reads backwards. */
    private void statusDate(LocalDate date, MemberField field, Findings found, Facts facts) {
        if (date.isAfter(today)) {
            found.add(RuleCode.STATUS_DATE_IN_FUTURE, field.wire());
        } else if (facts.joinDate != null && date.isBefore(facts.joinDate)) {
            found.add(RuleCode.STATUS_DATE_BEFORE_JOINED, field.wire());
        }
    }

    /** The leaving status for a deletion code: the mapping's list, its {@code *}, then the defaults. */
    private String leavingStatus(Integer code) {
        String key = code == null ? "*" : String.valueOf(code);
        String chosen = mapping.lookUp(MappingDefinition.DELETION_STATUS_LIST, key);
        if (chosen == null) {
            chosen = mapping.lookUp(MappingDefinition.DELETION_STATUS_LIST, "*");
        }
        return chosen != null ? chosen : DEFAULT_DELETION_STATUS.getOrDefault(key, DEFAULT_DELETION_STATUS.get("*"));
    }

    // ------------------------------------------------------------------------------------------------ where

    /**
     * The workplace by its R/A number. Every figure SASBO reads is grouped through it (rule G), so where it is on record
     * its area and table code are the member's, and a table code or area in the file that says otherwise is a warning.
     * Without a workplace, the file's own table code and area are kept for the reconciliation.
     */
    private void workplace(Map<MemberField, String> in, Findings found, Facts facts) {
        ReferenceIndex.Workplace workplace = null;
        String ra = in.get(WORKPLACE_RA_NUMBER);
        if (ra == null) {
            found.add(RuleCode.WORKPLACE_MISSING, WORKPLACE_RA_NUMBER.wire());
        } else {
            Integer number = number(ra);
            workplace = number == null ? null : reference.workplaces().get(number);
            if (workplace == null) {
                found.add(RuleCode.WORKPLACE_UNKNOWN, WORKPLACE_RA_NUMBER.wire());
            } else {
                facts.workplaceId = workplace.id();
                facts.areaLetter = workplace.areaLetter();
                facts.tableCode = workplace.tableCode();
            }
        }
        String tableCode = in.get(TABLE_CODE);
        if (tableCode != null) {
            Integer code = number(tableCode);
            if (code == null || !reference.tableCodes().contains(code)) {
                found.add(RuleCode.TABLE_CODE_UNKNOWN, TABLE_CODE.wire());
            } else if (workplace != null && workplace.tableCode() != null && !workplace.tableCode().equals(code)) {
                found.add(RuleCode.TABLE_CODE_DISAGREES, TABLE_CODE.wire());
            } else if (facts.tableCode == null) {
                facts.tableCode = code;
            }
        }
        String area = in.get(AREA_LETTER) == null ? null : in.get(AREA_LETTER).strip().toUpperCase(Locale.ROOT);
        if (area != null) {
            if (!reference.areaLetters().contains(area)) {
                found.add(RuleCode.AREA_UNKNOWN, AREA_LETTER.wire());
            } else if (workplace != null && !area.equals(workplace.areaLetter())) {
                found.add(RuleCode.AREA_DISAGREES, AREA_LETTER.wire());
            } else if (facts.areaLetter == null) {
                facts.areaLetter = area;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ contact

    private static void contact(Map<MemberField, String> in, Map<MemberField, String> out, Findings found) {
        for (MemberField field : List.of(CELL_TEL, WORK_TEL, HOME_TEL)) {
            String value = in.get(field);
            if (value != null) {
                Optional<String> number = PhoneNumbers.normalise(value);
                if (number.isPresent()) {
                    out.put(field, number.get());
                } else {
                    found.add(RuleCode.PHONE_INVALID, field.wire());
                }
            }
        }
        for (MemberField field : List.of(PERSONAL_EMAIL, WORK_EMAIL)) {
            String value = in.get(field);
            if (value != null) {
                if (EmailAddresses.valid(value)) {
                    out.put(field, value);
                } else {
                    found.add(RuleCode.EMAIL_INVALID, field.wire());
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ payment

    /**
     * How the member pays, as capture's vocabulary spells it; a debit-order member needs an account number (capture's
     * rule, {@code MemberValidator.validatePaymentType}). The account is digits alone; the debit day a day of a month;
     * the subscription an amount in rand -- each not moved where it is not.
     */
    private static void payment(Map<MemberField, String> in, Map<MemberField, String> out, Findings found) {
        String paymentType = null;
        if (in.containsKey(PAYMENT_TYPE)) {
            try {
                PaymentType type = PaymentType.fromString(in.get(PAYMENT_TYPE));
                paymentType = type == null ? null : type.getDisplayName();
            } catch (IllegalArgumentException unknown) {
                found.add(RuleCode.CODE_UNKNOWN, PAYMENT_TYPE.wire());
            }
            if (paymentType != null) {
                out.put(PAYMENT_TYPE, paymentType);
            }
        }
        if (in.containsKey(ACCOUNT_TYPE)) {
            try {
                AccountType type = AccountType.fromString(in.get(ACCOUNT_TYPE));
                if (type != null) {
                    out.put(ACCOUNT_TYPE, type.getDisplayName());
                }
            } catch (IllegalArgumentException unknown) {
                found.add(RuleCode.CODE_UNKNOWN, ACCOUNT_TYPE.wire());
            }
        }
        String account = in.get(BANK_ACCOUNT_NUMBER);
        if (account != null) {
            String digits = account.replaceAll("[\\s-]", "");
            if (digits.matches("[0-9]{6,16}")) {
                out.put(BANK_ACCOUNT_NUMBER, digits);
            } else {
                found.add(RuleCode.ACCOUNT_NUMBER_INVALID, BANK_ACCOUNT_NUMBER.wire());
            }
        }
        if (PaymentType.DEBIT_ORDER.getDisplayName().equals(paymentType) && !out.containsKey(BANK_ACCOUNT_NUMBER)) {
            found.add(RuleCode.DEBIT_ORDER_WITHOUT_ACCOUNT, BANK_ACCOUNT_NUMBER.wire());
        }
        String day = in.get(DEBIT_DAY);
        if (day != null) {
            Integer number = number(day);
            if (number == null || number < 1 || number > 31) {
                found.add(RuleCode.DEBIT_DAY_INVALID, DEBIT_DAY.wire());
            } else {
                out.put(DEBIT_DAY, String.valueOf(number));
            }
        }
        String amount = in.get(MONTHLY_SUBSCRIPTION);
        if (amount != null) {
            String plain = amount.replaceAll("\\s", "").replaceFirst("^R", "");
            if (plain.matches("[0-9]{0,7}([.,][0-9]{1,2})?") && plain.matches(".*[0-9].*")) {
                out.put(MONTHLY_SUBSCRIPTION,
                        new BigDecimal(plain.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).toPlainString());
            } else {
                found.add(RuleCode.AMOUNT_INVALID, MONTHLY_SUBSCRIPTION.wire());
            }
        }
    }

    /** A whole number of up to nine digits, leading noughts allowed; {@code null} for anything else. */
    private static Integer number(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.strip();
        return digits.matches("[0-9]{1,9}") ? Integer.valueOf(digits) : null;
    }

    /** What the rules learnt about the member, beside the fields. */
    private static final class Facts {
        private String memberStatus;
        private String statusKind;
        private String identityType;
        private String identityKey;
        private String membershipKey;
        private Long membershipSeq;
        private Long workplaceId;
        private String areaLetter;
        private Integer tableCode;
        private Integer deletionCode;
        private Long deletionCodeId;
        private Integer suspensionCode;
        private Long suspensionCodeId;
        private LocalDate joinDate;
        private LocalDate leftOn;
        private LocalDate statusChangedOn;
    }

    /** The problems found, each once. */
    private static final class Findings {
        private final List<Problem> problems = new ArrayList<>();

        void add(Problem problem) {
            if (!problems.contains(problem)) {
                problems.add(problem);
            }
        }

        void add(RuleCode code, String field) {
            add(new Problem(code, field));
        }

        boolean has(RuleCode code, String field) {
            return problems.contains(new Problem(code, field));
        }

        void remove(RuleCode code, String field) {
            problems.remove(new Problem(code, field));
        }
    }
}
