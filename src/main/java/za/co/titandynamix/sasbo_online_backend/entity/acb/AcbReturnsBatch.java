package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One debit day's returns report, as an administrator recorded it (V72). "Reports are communicated as per debit days"
 * (Stephne, DOD-06): the CFO downloads one from online banking for each debit day (DOD-05), and this row says what
 * typing it in did -- how many entries, how many were new, how many letters they queued, who and when.
 */
@Entity
@Table(name = "acb_returns_batches")
@Getter
@Setter
@NoArgsConstructor
public class AcbReturnsBatch {

    /** Typed in from the report; {@code IMPORT} is kept for the day the bank's export format is known (DOD-02). */
    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** The debit day the report is for. */
    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    /** The day the bank's report is dated, when the administrator gives it. */
    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "entries", nullable = false)
    private Integer entries;

    @Column(name = "recorded", nullable = false)
    private Integer recorded;

    /** Entries already recorded with the same code: a retry, or the same report typed twice -- changed nothing. */
    @Column(name = "already_recorded", nullable = false)
    private Integer alreadyRecorded;

    @Column(name = "letters_queued", nullable = false)
    private Integer lettersQueued;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
