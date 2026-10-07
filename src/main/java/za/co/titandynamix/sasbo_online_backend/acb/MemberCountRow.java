package za.co.titandynamix.sasbo_online_backend.acb;

import java.time.LocalDate;

/** What the unpaid count reads of a member: number, surname, status and join date (DOD-27). No account, no identity. */
public record MemberCountRow(Integer id, String membershipNumber, String surname, String status, LocalDate joinDate) {
}
