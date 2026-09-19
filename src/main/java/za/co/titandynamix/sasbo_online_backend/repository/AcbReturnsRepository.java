package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The reads the returns loop makes: a run's unpaids and their counts, a member's history of debits,
 * and what a letter needs to know about the member it goes to.
 */
public interface AcbReturnsRepository extends Repository<AcbRunLine, Long> {

    /** A run's unpaid lines, a page at a time. */
    @Query(value = "SELECT l FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid ORDER BY l.id",
            countQuery = "SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid")
    Page<AcbRunLine> findUnpaids(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid,
                                 Pageable page);

    /**
     * A run's unpaid lines of one reason -- with {@code INVALID_ACCOUNT_NUMBER}, the legacy "Invalid
     * Accounts" list, which SASBO confirmed is what the bank returns and nothing checked beforehand.
     * Its own query rather than a nullable parameter: PostgreSQL cannot type a parameter it only
     * ever sees compared with NULL.
     */
    @Query(value = "SELECT l FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid "
            + "AND l.unpaidReason = :reason ORDER BY l.id",
            countQuery = "SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid "
                    + "AND l.unpaidReason = :reason")
    Page<AcbRunLine> findUnpaidsFor(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid,
                                    @Param("reason") String reason, Pageable page);

    /** {@code [outcome, count]} for a run's lines. */
    @Query("SELECT l.outcome, count(l) FROM AcbRunLine l WHERE l.runId = :runId GROUP BY l.outcome")
    List<Object[]> countByOutcome(@Param("runId") Long runId);

    /** {@code [reason, count]} for a run's unpaids. */
    @Query("SELECT l.unpaidReason, count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid "
            + "GROUP BY l.unpaidReason")
    List<Object[]> countUnpaidByReason(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid);

    /**
     * Every debit from a sent run, of every member who has an unpaid in one -- the three-month rule's
     * input, in one query.
     */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow("
            + "l.memberId, l.membershipNumber, l.surname, l.id, l.actionDate, l.rePresentsLineId, l.outcome) "
            + "FROM AcbRunLine l, AcbRun r WHERE l.runId = r.id AND r.status IN :sent "
            + "AND l.memberId IN (SELECT u.memberId FROM AcbRunLine u, AcbRun ur "
            + "                   WHERE u.runId = ur.id AND ur.status IN :sent AND u.outcome = :unpaid)")
    List<PresentationRow> findHistoriesOfMembersWithUnpaids(@Param("sent") Collection<AcbRunStatus> sent,
                                                            @Param("unpaid") AcbLineOutcome unpaid);

    /** What a letter needs of the member it goes to. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient("
            + "m.id, m.title, m.address, m.personalEmail, m.workEmail, m.preferredEmail, a.name) "
            + "FROM Member m LEFT JOIN m.area a WHERE m.id = :memberId")
    Optional<LetterRecipient> findRecipient(@Param("memberId") Integer memberId);
}
