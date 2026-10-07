package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEntry;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An employer's payroll file for a month, as received, with how it was read and what its last comparison found. V74.
 *
 * <p>🔒 {@code content} is member data: getters and setters only, no generated {@code toString}. It is {@code null}
 * once the file is {@link PayrollFileStatus#PURGED}, a year after it was loaded (Stephne, PAY-39).
 */
@Entity
@Table(name = "payroll_files", uniqueConstraints = @UniqueConstraint(
        name = "payroll_files_same_bytes_once", columnNames = {"payroll_employer_id", "period", "sha256"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payroll_employer_id", nullable = false)
    private Long payrollEmployerId;

    /** The month deducted, as its first day. */
    @Column(name = "period", nullable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry", nullable = false, length = 10)
    private PayrollEntry entry;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private PayrollFormat format;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** No {@code length}: a large one makes Hibernate declare a blob, which the fast lane's H2 refuses. */
    @Column(name = "content")
    private byte[] content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private PayrollFileStatus status;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "retain_until", nullable = false)
    private LocalDate retainUntil;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "sheet_name", length = 100)
    private String sheetName;

    @Column(name = "heading_row")
    private Integer headingRow;

    /** Field to heading as printed, as JSON. */
    @Column(name = "headings", length = 2000)
    private String headings;

    /** Every heading on the heading row as printed, in sheet order, as a JSON array. */
    @Column(name = "heading_cells", length = 4000)
    private String headingCells;

    @Column(name = "column_mapping", length = 2000)
    private String columnMapping;

    @Column(name = "stated_total", precision = 14, scale = 2)
    private BigDecimal statedTotal;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "unreadable_count", nullable = false)
    private Integer unreadableCount;

    @Column(name = "rows_not_read", nullable = false)
    private Integer rowsNotRead;

    /** The comparison's own tally, as JSON -- kept when the lines are deleted after a year. */
    @Column(name = "counts", length = 1000)
    private String counts;

    @Column(name = "matched_count", nullable = false)
    private Integer matchedCount;

    @Column(name = "discrepancy_count", nullable = false)
    private Integer discrepancyCount;

    @Column(name = "open_count", nullable = false)
    private Integer openCount;

    @Column(name = "file_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal fileTotal;

    @Column(name = "rate_amount", precision = 12, scale = 2)
    private BigDecimal rateAmount;

    @Column(name = "rate_effective_from")
    private LocalDate rateEffectiveFrom;

    @Column(name = "expected_total", precision = 14, scale = 2)
    private BigDecimal expectedTotal;

    @Column(name = "admin_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal adminFeePercent;

    @Column(name = "runs", nullable = false)
    private Integer runs;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_by", length = 100)
    private String lastRunBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
