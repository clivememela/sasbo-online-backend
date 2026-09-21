package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * A member as the Debits screen's search lists them. 🔒 The account number is read to be masked and
 * never leaves whole; the identity number is matched, never returned.
 */
public record MemberSearchRow(Integer id, String membershipNumber, String title, String initials, String surname,
                              String bankName, String bankAccountNumber, Integer debitDay, String status) {

    @Override
    public String toString() {
        return "MemberSearchRow[id=" + id + "]";
    }
}
