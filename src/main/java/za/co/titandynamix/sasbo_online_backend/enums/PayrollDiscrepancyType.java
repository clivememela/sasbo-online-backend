package za.co.titandynamix.sasbo_online_backend.enums;

import java.util.List;

import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.ARREARS_RECORDED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.BALANCE_RECEIVED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.CATCH_UP_CONFIRMED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.CORRECTED_BY_EMPLOYER;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.CORRECTED_FILE_RECEIVED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.CREDIT_RECORDED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.DEDUCTION_RESTARTED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.DEDUCTION_STOPPED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.EMPLOYER_ASKED_TO_CORRECT;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.EXPLAINED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.IGNORED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.JOINED_AND_CAPTURED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.MEMBER_DELETED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.MEMBER_LEFT_EMPLOYER;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.MEMBER_REINSTATED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.MEMBER_SUSPENDED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.NOT_THE_SAME_PERSON;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.ON_LEAVE;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.OTHER;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.RECORD_UPDATED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.REFUNDED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.SAME_PERSON_CONFIRMED;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.STAYS_ON_DEBIT_ORDER;
import static za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome.SWITCHED_TO_PAYROLL;

/**
 * Every kind of payroll discrepancy, each with the rule of Stephne's it comes from, what SASBO does about it, what goes
 * on the employer's query list, and the outcomes an administrator may record (PAY.md, 6 Oct). The sentences are hers
 * where she wrote one, in plain words where she ticked a box.
 */
public enum PayrollDiscrepancyType {

    NOT_A_MEMBER("Not a member", "PAY-12", Subject.LINE, PayrollQueryAction.DELETE,
            "On the file but not a member: query it with the employer.",
            List.of(JOINED_AND_CAPTURED, DEDUCTION_STOPPED, OTHER)),
    MEMBERSHIP_ENDED("Membership ended", "PAY-13", Subject.MEMBER, PayrollQueryAction.DELETE,
            "Membership has ended but the member is still deducted: ask the employer to stop, without a refund.",
            List.of(DEDUCTION_STOPPED, MEMBER_REINSTATED, OTHER)),
    MISSING_FROM_FILE("Missing from the file", "PAY-14, PAY-15", Subject.MEMBER, PayrollQueryAction.ADD,
            "A payroll member is missing from the file: query the unpaid with the employer. Members are usually "
                    + "suspended while it is followed up (PAY-20), and deleted after three months without a "
                    + "deduction (PAY-15).",
            List.of(DEDUCTION_RESTARTED, MEMBER_SUSPENDED, MEMBER_LEFT_EMPLOYER, ON_LEAVE, MEMBER_DELETED, OTHER)),
    AMOUNT_DIFFERS("Amount differs", "PAY-16", Subject.MEMBER, PayrollQueryAction.CORRECT,
            "The amount differs from the subscription: ask the employer to correct it, and record the difference "
                    + "as arrears or a credit.",
            List.of(CORRECTED_BY_EMPLOYER, ARREARS_RECORDED, CREDIT_RECORDED, OTHER)),
    DEDUCTED_TWICE("Deducted twice", "PAY-18", Subject.MEMBER, PayrollQueryAction.CORRECT,
            "Deducted twice in the same month, with no missed month on record: refund the member directly.",
            List.of(REFUNDED, CATCH_UP_CONFIRMED, OTHER)),
    POSSIBLE_CATCH_UP("Possible catch-up", "PAY-26, PAY-25", Subject.MEMBER, null,
            "Listed twice, and the member has missed months on record: confirm it is the employer catching up, "
                    + "not a double deduction.",
            List.of(CATCH_UP_CONFIRMED, REFUNDED, OTHER)),
    EMPLOYEE_NUMBER_DIFFERS("Different employee number", "PAY-19", Subject.MEMBER, null,
            "A different employee number from the one on record: check with the member, then update the record "
                    + "(PAY-17).",
            List.of(RECORD_UPDATED, EMPLOYER_ASKED_TO_CORRECT, OTHER)),
    NAME_DIFFERS("Name does not match", "PAY-19", Subject.MEMBER, null,
            "The name does not match the ID number or the employee number on record: check with the member "
                    + "(PAY-17).",
            List.of(RECORD_UPDATED, SAME_PERSON_CONFIRMED, NOT_THE_SAME_PERSON, OTHER)),
    DETAILS_DIFFER("Other details differ", "PAY-17", Subject.MEMBER, null,
            "Other details on the file differ from the record (the ID number, or the employer on record): check "
                    + "with the member, then update the record.",
            List.of(RECORD_UPDATED, OTHER)),
    DEBIT_ORDER_MEMBER("Pays by debit order", "PAY-29", Subject.MEMBER, null,
            "A member who pays by debit order is on the payroll file: query it with the member, then switch them "
                    + "to payroll and stop the debit order if they agree.",
            List.of(SWITCHED_TO_PAYROLL, STAYS_ON_DEBIT_ORDER, OTHER)),
    UNREADABLE_ROW("Line could not be read", "", Subject.LINE, null,
            "A line of the file could not be read (the reason is given): check it with the employer.",
            List.of(CORRECTED_BY_EMPLOYER, IGNORED, OTHER)),
    FILE_TOTAL_DIFFERS("File total differs", "PAY-09", Subject.FILE, null,
            "The total printed on the file is not the sum of its lines: ask the employer.",
            List.of(CORRECTED_FILE_RECEIVED, EXPLAINED, OTHER)),
    PAYMENT_DIFFERS("Payment differs", "PAY-28, PAY-27", Subject.FILE, null,
            "The money paid in differs from the file's total less the employer's fee: follow it up with the "
                    + "employer.",
            List.of(BALANCE_RECEIVED, EXPLAINED, OTHER));

    /** What a discrepancy is about -- and so how a re-run finds it again. */
    public enum Subject {
        /** A member on record, matched or missing. */
        MEMBER,
        /** A line of the file that no member fits, or that cannot be read. */
        LINE,
        /** The file as a whole. */
        FILE
    }

    private final String label;
    private final String rule;
    private final Subject subject;
    private final PayrollQueryAction queryAction;
    private final String whatToDo;
    private final List<PayrollOutcome> outcomes;

    PayrollDiscrepancyType(String label, String rule, Subject subject, PayrollQueryAction queryAction,
                           String whatToDo, List<PayrollOutcome> outcomes) {
        this.label = label;
        this.rule = rule;
        this.subject = subject;
        this.queryAction = queryAction;
        this.whatToDo = whatToDo;
        this.outcomes = outcomes;
    }

    public String label() {
        return label;
    }

    /** Stephne's question number(s) the rule comes from; empty where the rule is the system's own. */
    public String rule() {
        return rule;
    }

    public Subject subject() {
        return subject;
    }

    /** What the employer's query list asks for this kind; {@code null} where the employer is not asked. */
    public PayrollQueryAction queryAction() {
        return queryAction;
    }

    public String whatToDo() {
        return whatToDo;
    }

    /** The outcomes a person may record for this kind, in the order a screen offers them. */
    public List<PayrollOutcome> outcomes() {
        return outcomes;
    }
}
