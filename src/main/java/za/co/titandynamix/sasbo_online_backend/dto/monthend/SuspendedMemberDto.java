package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * One member suspended now (Stephne, MEN-18: "name, mem number etc."). No identity, account or telephone number.
 * Dates are ISO text.
 *
 * @param memberId          what opens the record
 * @param membershipNumber  the member's number
 * @param name              initials and surname
 * @param bank              the employer through the member's workplace -- else the "Bank Where Employed" as captured
 * @param workplace         {@code R/A <number> <name>}, where the member has a workplace
 * @param area              the workplace's area letter
 * @param paymentType       how the member pays, as stored
 * @param suspensionCode    SASBO's code of the change that suspended them; {@code null} where none is on record
 * @param suspensionName    that code's words
 * @param noDeductionReason the reason for no payroll deduction chosen with it (MEN-17), or {@code null}
 * @param suspendedOn       the day the suspension began, in SASBO's zone; {@code null} where no change is on record
 * @param lapsesOn          the day it lapses, three months on; {@code null} with {@code suspendedOn}
 * @param lapsed            whether it has lapsed by the page's {@code asAt}
 */
public record SuspendedMemberDto(int memberId, String membershipNumber, String name, String bank, String workplace,
                                 String area, String paymentType, Integer suspensionCode, String suspensionName,
                                 String noDeductionReason, String suspendedOn, String lapsesOn, boolean lapsed) {
}
