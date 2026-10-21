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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The bureau file a run produced, kept as the evidence of what was sent: its bytes until the purge
 * date, and its hash and totals for ever.
 *
 * <p>{@code AcbFileServiceImpl} reads and writes this table with JDBC -- it locks the run row and
 * streams the bytes -- so this mapping is the table's DECLARATION: it is what builds it in the fast
 * lane's schema, and what {@code ddl-auto: validate} checks V44 against on PostgreSQL. Without it the
 * startup purge meets a table the fast lane never created.
 *
 * <p>🔒 {@code content} is member data -- every debited member's account number. Getters and setters
 * only; no generated {@code toString}.
 */
@Entity
@Table(name = "acb_run_files")
@Getter
@Setter
@NoArgsConstructor
public class AcbRunFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    private Long runId;

    @Column(name = "file_name", nullable = false, length = 40)
    private String fileName;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /**
     * The file's bytes; {@code null} once the purge date has passed. No {@code length}: a large one
     * makes Hibernate declare a {@code blob}, which the fast lane's H2 refuses in PostgreSQL mode --
     * silently, leaving the table out. PostgreSQL's {@code bytea} has no length to give.
     */
    @Column(name = "content")
    private byte[] content;

    @Column(name = "records", nullable = false)
    private Integer records;

    @Column(name = "debit_count", nullable = false)
    private Integer debitCount;

    @Column(name = "debit_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal debitValue;

    @Column(name = "hash_total", nullable = false, length = 12)
    private String hashTotal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "keep_until", nullable = false)
    private LocalDate keepUntil;

    @Column(name = "purged_at")
    private Instant purgedAt;

    /**
     * How many times the file has been downloaded for the bank (DOD-11, V72), each one audited. Nullable here only
     * because the fast lane builds the table from this entity; V72 makes it NOT NULL DEFAULT 0.
     */
    @Column(name = "downloads")
    private Integer downloads;

    @Column(name = "last_downloaded_by", length = 100)
    private String lastDownloadedBy;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;
}
