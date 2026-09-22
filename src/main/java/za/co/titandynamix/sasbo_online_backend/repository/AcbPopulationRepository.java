package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.acb.BankRow;
import za.co.titandynamix.sasbo_online_backend.acb.MemberDebitRow;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;

import java.util.List;

/**
 * The three reads a run is prepared from: the members, the banks, and which banks SASBO collects
 * from. Read-only, and ACB's own, so the member's repository -- the capture arc's -- is untouched.
 *
 * <h2>Why projections, and why keyset paging</h2>
 * {@code MemberRepository.findAll()} loads eight associations with every member. A run needs ten
 * columns, so it asks for ten columns. It reads them a page at a time, keyed on the last id seen
 * rather than on an offset: an offset page is re-scanned from the start every time, and a member
 * captured mid-run would shift every later page by one and silently skip a member.
 */
public interface AcbPopulationRepository extends Repository<Member, Integer> {

    /** The next page of members after {@code afterId}, in id order. Pass 0 for the first page. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.MemberDebitRow("
            + "m.id, m.membershipNumber, m.surname, m.initials, m.status, m.paymentType, "
            + "m.bankName, m.bankAccountNumber, m.debitDay, m.accountType) "
            + "FROM Member m WHERE m.id > :afterId ORDER BY m.id")
    List<MemberDebitRow> findPageAfter(@Param("afterId") Integer afterId, Pageable page);

    /** The statuses of the members a termination report lists (03.5) -- one query for the whole list. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.MemberStatusRow(m.id, m.status) "
            + "FROM Member m WHERE m.id IN :ids")
    List<za.co.titandynamix.sasbo_online_backend.acb.MemberStatusRow> findStatuses(
            @Param("ids") java.util.Collection<Integer> ids);

    /** The members an opening-position sheet names -- one query, whatever the sheet's length (D24). */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningMemberRow("
            + "m.id, m.membershipNumber, m.paymentType, m.arrearsBalance) "
            + "FROM Member m WHERE m.membershipNumber IN :numbers")
    List<za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningMemberRow> findByMembershipNumbers(
            @Param("numbers") java.util.Collection<String> numbers);

    /**
     * The Debits screen's member search (04.1): server-side and paged, so the screen never loads every
     * member (R-SEC-B, R-PERF-D). A blank criterion is {@code null} and matches everyone; the two
     * patterns arrive escaped with {@code !}, so a {@code %} or {@code _} typed is only itself.
     */
    @Query(value = "SELECT new za.co.titandynamix.sasbo_online_backend.acb.MemberSearchRow("
            + "m.id, m.membershipNumber, m.title, m.initials, m.surname, m.bankName, m.bankAccountNumber, m.debitDay, "
            + "m.status) FROM Member m "
            + "WHERE (:surname IS NULL OR lower(m.surname) LIKE :surname ESCAPE '!') "
            + "AND (:number IS NULL OR lower(m.membershipNumber) LIKE :number ESCAPE '!') "
            + "AND (:identity IS NULL OR m.identityNumber = :identity) "
            + "ORDER BY m.surname, m.id",
            countQuery = "SELECT count(m) FROM Member m "
            + "WHERE (:surname IS NULL OR lower(m.surname) LIKE :surname ESCAPE '!') "
            + "AND (:number IS NULL OR lower(m.membershipNumber) LIKE :number ESCAPE '!') "
            + "AND (:identity IS NULL OR m.identityNumber = :identity)")
    org.springframework.data.domain.Page<za.co.titandynamix.sasbo_online_backend.acb.MemberSearchRow> searchMembers(
            @Param("surname") String surname, @Param("number") String number, @Param("identity") String identity,
            Pageable page);

    /** Every bank on record, as the three columns a run matches and prints. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.BankRow("
            + "b.id, b.name, b.universalBranchCode) FROM Bank b")
    List<BankRow> findBanks();

    /**
     * The bank of every subscription row in {@code status}, one entry per ROW -- so a bank with two
     * ACTIVE rows is returned twice, which is how the run finds the contradiction and refuses.
     */
    @Query("SELECT s.bank.id FROM Subscriptions s WHERE s.status = :status AND s.bank IS NOT NULL")
    List<Integer> findBankIdsOfSubscriptionRows(@Param("status") SubscriptionsStatus status);
}
