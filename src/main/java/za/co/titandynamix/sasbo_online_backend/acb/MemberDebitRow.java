package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * The ten member fields a run reads, and nothing else.
 *
 * <p>A projection rather than the {@code Member} entity: the entity's repository loads eight
 * associations with every row, and a run walks every member on record. Reading ten columns in one
 * paged query is what keeps a preparation over sixty thousand members to a handful of statements.
 *
 * <p>🔒 It carries an account number, so its {@code toString()} is written by hand and names the
 * member's id only. A record's generated one prints every component, and a record is one log format
 * argument away from the log file.
 */
public record MemberDebitRow(
        Integer id,
        String membershipNumber,
        String surname,
        String initials,
        String status,
        String paymentType,
        String bankName,
        String bankAccountNumber,
        Integer debitDay,
        String accountType) {

    @Override
    public String toString() {
        return "MemberDebitRow[id=" + id + "]";
    }
}
