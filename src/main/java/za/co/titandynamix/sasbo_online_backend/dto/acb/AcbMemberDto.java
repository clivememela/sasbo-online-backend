package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * A member found by the Debits screen's search (04.1). 🔒 The account shows its last four digits
 * only, and the identity number is not here at all: the search ships what a list needs, never
 * every member's banking fields (R-SEC-B). The full record is loaded for ONE member, when chosen.
 */
public record AcbMemberDto(
        Integer id,
        String membershipNumber,
        String title,
        String initials,
        String surname,
        String bankName,
        String accountLastFour,
        Integer debitDay,
        String status) {
}
