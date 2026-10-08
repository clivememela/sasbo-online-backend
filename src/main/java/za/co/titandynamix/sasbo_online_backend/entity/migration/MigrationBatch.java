package za.co.titandynamix.sasbo_online_backend.entity.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A file registered for the data migration (V80): its name, hash, format, the mapping it is read by, its counts, its
 * status and its one job. Written by {@code MigrationBatchRepository}'s statements; this entity exists so that H2's
 * schema has the table. No {@code toString} over the mapping or the name.
 */
@Entity
@Table(name = "migration_batches")
@Getter
@NoArgsConstructor
public class MigrationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_file_name", nullable = false, length = 200)
    private String sourceFileName;

    @Column(name = "source_sha256", length = 64)
    private String sourceSha256;

    @Column(name = "source_bytes", nullable = false)
    private Long sourceBytes;

    @Column(name = "source_format", nullable = false, length = 12)
    private String sourceFormat;

    @Column(name = "mapping_key", nullable = false, length = 60)
    private String mappingKey;

    @Column(name = "mapping_snapshot", nullable = false, columnDefinition = "text")
    private String mappingSnapshot;

    @Column(name = "as_at", nullable = false)
    private LocalDate asAt;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "rows_read", nullable = false)
    private Integer rowsRead;

    @Column(name = "rows_valid", nullable = false)
    private Integer rowsValid;

    @Column(name = "rows_warned", nullable = false)
    private Integer rowsWarned;

    @Column(name = "rows_invalid", nullable = false)
    private Integer rowsInvalid;

    @Column(name = "rows_committed", nullable = false)
    private Integer rowsCommitted;

    @Column(name = "commit_cursor", nullable = false)
    private Long commitCursor;

    @Column(name = "registered_by", nullable = false, length = 100)
    private String registeredBy;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "validated_by", length = 100)
    private String validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "committed_by", length = 100)
    private String committedBy;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "rolled_back_by", length = 100)
    private String rolledBackBy;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Column(name = "rollback_reason", length = 300)
    private String rollbackReason;

    @Column(name = "job_kind", length = 12)
    private String jobKind;

    @Column(name = "job_state", length = 12)
    private String jobState;

    @Column(name = "job_done", nullable = false)
    private Integer jobDone;

    @Column(name = "job_total", nullable = false)
    private Integer jobTotal;

    @Column(name = "job_started_at")
    private Instant jobStartedAt;

    @Column(name = "job_heartbeat_at")
    private Instant jobHeartbeatAt;

    @Column(name = "job_finished_at")
    private Instant jobFinishedAt;

    @Column(name = "job_failure", length = 300)
    private String jobFailure;

    @Override
    public String toString() {
        return "MigrationBatch[" + id + ", " + status + "]";
    }
}
