package za.co.titandynamix.sasbo_online_backend.acb.returns;

import java.time.LocalDate;

/** A member's opening arrears position as the termination report reads it (D24). */
public record OpeningRow(Integer memberId, String membershipNumber, String surname, Integer unpaidsStanding,
                         LocalDate lastUnpaidMonth) {

    @Override
    public String toString() {
        return "OpeningRow[memberId=" + memberId + "]";
    }
}
