package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One month's debit-order collection -- the run, not its lines.
 *
 * <p>Its totals are DERIVED from its lines when the run is prepared and are re-derived on every
 * read; a stored total that disagrees with its lines is reported, never displayed as if it were
 * true. It carries {@code @Version} so two approvals of one run cannot both win, and it is never
 * deleted: a run prepared for the wrong month is cancelled, which releases the month.
 */
@Entity
@Table(name = "acb_runs")
@Getter
@Setter
@NoArgsConstructor
public class AcbRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The first day of the month collected. A date rather than a {@code YearMonth} so every query
     * compares it as the database does, and the migration's CHECK keeps it on the 1st.
     */
    @Column(name = "action_month", nullable = false)
    private LocalDate actionMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcbRunStatus status;

    @Column(name = "setup_date", nullable = false)
    private LocalDate setupDate;

    @Column(name = "creation_date")
    private LocalDate creationDate;

    @Column(name = "purge_date")
    private LocalDate purgeDate;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "last_due_date")
    private LocalDate lastDueDate;

    @Column(name = "rate_amount", precision = 12, scale = 2)
    private BigDecimal rateAmount;

    @Column(name = "rate_effective_from")
    private LocalDate rateEffectiveFrom;

    @Column(name = "holiday_set", nullable = false, length = 40)
    private String holidaySet;

    /**
     * The calendar as it stood for this run's month when its lines were computed -- its name and the
     * month's holidays ({@link za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar#fingerprint}).
     * Null until the lines are written.
     */
    @Column(name = "calendar_fingerprint", length = 200)
    private String calendarFingerprint;

    @Column(name = "line_count", nullable = false)
    private Integer lineCount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Why the bank declined the file, from the short list (D29, SD3-H); null unless DECLINED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "declined_reason", length = 20)
    private AcbDeclineReason declinedReason;

    /**
     * 🔒 The bank's own words, at most 300 characters, returned only with this run -- never in a log
     * line or an audit row (SD3-D): an administrator in a hurry types names and numbers.
     */
    @Column(name = "declined_words", length = 300)
    private String declinedWords;

    /** The declined run this one was prepared to replace, if any (SD3-H). */
    @Column(name = "replaces_run_id")
    private Long replacesRunId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
