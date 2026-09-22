package za.co.titandynamix.sasbo_online_backend.acb.opening;

import java.math.BigDecimal;

/** What the opening position needs of a member on record: who they are, how they pay, their stated arrears. */
public record OpeningMemberRow(Integer id, String membershipNumber, String paymentType, BigDecimal arrearsBalance) {
}
