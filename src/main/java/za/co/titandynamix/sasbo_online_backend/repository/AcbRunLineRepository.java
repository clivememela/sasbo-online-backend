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
     *
     * <p>Never one withdrawn because the member came back or rejoined (DOD-26, DOD-27, V72), and never one
     * whose bank code does not allow a re-debit now -- 4 and 30, 31, 32, 34 never, an unknown code not
     * until it is set (Stephne, DOD-03, DOD-04): the code table is the authority at every preparation, so
     * a code corrected after a decision is honoured. A line with no code at all predates the codes.
     */
    @Query("SELECT new za.co.titandynamix.sasbo_online_backend.acb.RePresentationCandidate("
            + "l.id, l.memberId, l.amount, r.actionMonth, "
            + "CASE WHEN l.permissionNote IS NULL THEN false ELSE true END) "
            + "FROM AcbRunLine l, AcbRun r "
            + "WHERE l.runId = r.id "
            + "AND l.outcome = :unpaid AND l.decision = :decision "
            + "AND r.status IN :sent AND r.actionMonth < :month "
            + "AND l.reDebitWithdrawn IS NULL "
            + "AND (l.unpaidCode IS NULL OR EXISTS (SELECT c FROM AcbUnpaidCode c "
            + "                                     WHERE c.code = l.unpaidCode AND c.reDebitAllowed = true)) "
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

    /** {@code [id, actionDate]} of a list of lines -- one query, for the re-debits a preparation weighs (DOD-26). */
    @Query("SELECT l.id, l.actionDate FROM AcbRunLine l WHERE l.id IN :ids")
    List<Object[]> findActionDates(@Param("ids") Collection<Long> ids);

    /** A run's lines by the file's sequence numbers -- how the bank's returns report names them (DOD-06). */
    List<AcbRunLine> findByRunIdAndFileSequenceIn(Long runId, Collection<Integer> fileSequences);

    /** {@code [actionDate, lines, unpaid]} for a run -- its debit days, one returns report each (DOD-06). */
    @Query("SELECT l.actionDate, count(l), sum(CASE WHEN l.outcome = :unpaid THEN 1 ELSE 0 END) "
            + "FROM AcbRunLine l WHERE l.runId = :runId GROUP BY l.actionDate ORDER BY l.actionDate")
    List<Object[]> countByActionDate(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid);

    /** Lines the bank has answered for -- paid or unpaid. A run with any cannot be recorded as declined (F45). */
    long countByRunIdAndOutcomeNot(Long runId, AcbLineOutcome outcome);

    /** Unpaids nobody has decided about yet -- counted on the run, never guessed at; closing waits for none. */
    @Query("SELECT count(l) FROM AcbRunLine l WHERE l.runId = :runId AND l.outcome = :unpaid AND l.decision IS NULL")
    long countUndecided(@Param("runId") Long runId, @Param("unpaid") AcbLineOutcome unpaid);
}
