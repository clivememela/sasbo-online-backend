package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.LineTotals;
import za.co.titandynamix.sasbo_online_backend.acb.RePresentationCandidate;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AcbRunLineRepository extends JpaRepository<AcbRunLine, Long> {

    /** A run's lines, a page at a time, in the order the file will group them. */
    @Query(value = "SELECT l FROM AcbRunLine l WHERE l.runId = :runId "
            + "ORDER BY l.actionDate, l.membershipNumber, l.id",
            countQuery = "SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId")
    Page<AcbRunLine> findPage(@Param("runId") Long runId, Pageable page);

    /** A run's control totals, derived from its lines on every read. */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.LineTotals(count(l), sum(l.amount)) "
            + "FROM AcbRunLine l WHERE l.runId = :runId")
    LineTotals totalsFor(@Param("runId") Long runId);

    /**
     * The control totals of several runs in ONE query -- {@code [runId, count, sum]} rows, and no
     * row for a run with no lines. The runs list reads a page of runs; one query per run would be
     * the N+1 this module promises not to have.
     */
    @Query("SELECT l.runId, count(l), sum(l.amount) FROM AcbRunLine l "
            + "WHERE l.runId IN :runIds GROUP BY l.runId")
    List<Object[]> totalsForRuns(@Param("runIds") Collection<Long> runIds);

    /**
     * How many of a run's lines debit a member whose number is one of {@code numbers} -- the seed's
     * EXACT list, never a numeric range (SD4-E). One query, whatever the run's size.
     */
    @Query("SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.membershipNumber IN :numbers")
    long countMembershipNumbersIn(@Param("runId") Long runId, @Param("numbers") Collection<String> numbers);

    /**
     * The unpaids the union decided to present again, from runs that were sent, for months before
     * {@code month}, that no live run has presented yet.
     *
     * <p>"No live run": a line in a cancelled run is released, so the unpaid it re-presented is
     * offered again when the month is prepared again. The partial unique index on
     * {@code re_presents_line_id} counts only unreleased lines, for the same reason.
     */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.RePresentationCandidate("
            + "l.id, l.memberId, l.amount, r.actionMonth) "
            + "FROM AcbRunLine l, AcbRun r "
            + "WHERE l.runId = r.id "
            + "AND l.outcome = :unpaid AND l.decision = :decision "
            + "AND r.status IN :sent AND r.actionMonth < :month "
            + "AND NOT EXISTS (SELECT x FROM AcbRunLine x "
            + "                WHERE x.rePresentsLineId = l.id AND x.runReleased = false)")
    List<RePresentationCandidate> findRePresentationCandidates(
            @Param("month") LocalDate month,
            @Param("unpaid") AcbLineOutcome unpaid,
            @Param("decision") UnpaidDecision decision,
            @Param("sent") Collection<AcbRunStatus> sent);

    /**
     * Releases a cancelled run's claims on the unpaids its lines re-present.
     *
     * <p>{@code @Transactional} of its own, like the heartbeat: a repository's query methods run
     * read-only by default, and an UPDATE in a read-only transaction is refused by PostgreSQL. Called
     * inside the cancel's transaction, it simply joins it.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AcbRunLine l SET l.runReleased = true WHERE l.runId = :runId")
    int releaseRun(@Param("runId") Long runId);

    /**
     * Marks every line of a run still {@code pending} as {@code paid} -- the bank's returns report
     * lists only the debits that came back, so a debit it does not list was collected. Joins the
     * transition's transaction, like {@link #releaseRun}.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AcbRunLine l SET l.outcome = :paid WHERE l.runId = :runId AND l.outcome = :pending")
    int markPending(@Param("runId") Long runId, @Param("pending") AcbLineOutcome pending,
                    @Param("paid") AcbLineOutcome paid);

    /** Lines the bank has answered for -- paid or unpaid. A run with any cannot be recorded as declined (F45). */
    long countByRunIdAndOutcomeNot(Long runId, AcbLineOutcome outcome);

    /** Unpaids nobody has decided about yet -- counted on the run, never guessed at; closing waits for none. */
    @Query("SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid AND l.decision IS NULL")
    long countUndecided(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid);
}
