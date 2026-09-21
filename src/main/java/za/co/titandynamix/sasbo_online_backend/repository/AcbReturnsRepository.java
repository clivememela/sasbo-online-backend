package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.HeldOverRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.HeldLetterRow;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

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
     * Every debit from a sent run since {@code from}, of every member with an unpaid since {@code from} --
     * the three-unpaids rule's input, in one query. 🔒 BOTH halves are bounded to the window (SD3-K): the
     * report reads the recent past, never every debit of every member who ever bounced. A re-debit is
     * read like any other debit (question 27).
     */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow("
            + "l.memberId, l.membershipNumber, l.surname, l.id, l.actionDate, l.outcome) "
            + "FROM AcbRunLine l, AcbRun r WHERE l.runId = r.id AND r.status IN :sent AND r.actionMonth >= :from "
            + "AND l.memberId IN (SELECT u.memberId FROM AcbRunLine u, AcbRun ur "
            + "                   WHERE u.runId = ur.id AND ur.status IN :sent AND ur.actionMonth >= :from "
            + "                   AND u.outcome = :unpaid)")
    List<PresentationRow> findHistoriesOfMembersWithUnpaids(@Param("sent") Collection<AcbRunStatus> sent,
                                                            @Param("unpaid") AcbLineOutcome unpaid,
                                                            @Param("from") LocalDate from);

    /** The opening arrears position, with each member's surname -- one query (D24). */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.OpeningRow("
            + "p.memberId, p.membershipNumber, m.surname, p.unpaidsStanding, p.lastUnpaidMonth) "
            + "FROM AcbOpeningPosition p, Member m WHERE m.id = p.memberId")
    List<za.co.titandynamix.sasbo_online_backend.acb.returns.OpeningRow> findOpeningPosition();

    /**
     * The re-debits a run holds over (D32): unpaids decided present-again, from sent runs of earlier
     * months, that no live run presents -- of members who ARE in this run. One query, paged.
     */
    @Query(value = "SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.HeldOverRow("
            + "u.id, u.runId, u.memberId, u.membershipNumber, u.surname, ur.actionMonth, u.amount) "
            + "FROM AcbRunLine u, AcbRun ur "
            + "WHERE u.runId = ur.id AND ur.status IN :sent AND ur.actionMonth < :month "
            + "AND u.outcome = :unpaid AND u.decision = :decision "
            + "AND NOT EXISTS (SELECT x FROM AcbRunLine x WHERE x.rePresentsLineId = u.id AND x.runReleased = false) "
            + "AND EXISTS (SELECT m FROM AcbRunLine m WHERE m.runId = :runId AND m.memberId = u.memberId "
            + "            AND m.runReleased = false) "
            + "ORDER BY u.memberId, ur.actionMonth, u.id",
            countQuery = "SELECT count(u) FROM AcbRunLine u, AcbRun ur "
            + "WHERE u.runId = ur.id AND ur.status IN :sent AND ur.actionMonth < :month "
            + "AND u.outcome = :unpaid AND u.decision = :decision "
            + "AND NOT EXISTS (SELECT x FROM AcbRunLine x WHERE x.rePresentsLineId = u.id AND x.runReleased = false) "
            + "AND EXISTS (SELECT m FROM AcbRunLine m WHERE m.runId = :runId AND m.memberId = u.memberId "
            + "            AND m.runReleased = false)")
    Page<HeldOverRow> findHeldOver(@Param("runId") Long runId, @Param("month") LocalDate month,
                                   @Param("sent") Collection<AcbRunStatus> sent,
                                   @Param("unpaid") AcbLineOutcome unpaid,
                                   @Param("decision") UnpaidDecision decision,
                                   Pageable pageable);

    /** A run's held letters, with each member's addresses as they stand now -- one query, paged (Q46). */
    @Query(value = "SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.HeldLetterRow("
            + "l.id, rl.id, rl.memberId, rl.membershipNumber, rl.surname, l.template, l.statusReason, "
            + "m.personalEmail, m.workEmail, m.preferredEmail) "
            + "FROM AcbLetter l, AcbRunLine rl, Member m "
            + "WHERE l.lineId = rl.id AND rl.memberId = m.id AND rl.runId = :runId AND l.status = :held "
            + "ORDER BY l.id",
            countQuery = "SELECT count(l) FROM AcbLetter l, AcbRunLine rl WHERE l.lineId = rl.id "
            + "AND rl.runId = :runId AND l.status = :held")
    Page<HeldLetterRow> findHeldLetters(@Param("runId") Long runId, @Param("held") LetterStatus held, Pageable pageable);

    /** What a letter needs of the member it goes to. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient("
            + "m.id, m.title, m.address, m.personalEmail, m.workEmail, m.preferredEmail, a.name) "
            + "FROM Member m LEFT JOIN m.area a WHERE m.id = :memberId")
    Optional<LetterRecipient> findRecipient(@Param("memberId") Integer memberId);
}
