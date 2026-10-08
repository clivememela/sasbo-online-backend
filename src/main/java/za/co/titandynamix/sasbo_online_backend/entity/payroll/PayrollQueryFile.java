package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileProtection;

import java.time.Instant;

/**
 * An employer's query list as it left SASBO: the ENCRYPTED file, produced under a password the system generated for it
 * alone and showed once (the one rule for every file SASBO sends out, month end's bank files included). 🔒 No field
 * holds the password, and the content goes a year after the payroll file was loaded (PAY-39). V75.
 */
@Entity
@Table(name = "payroll_query_files")
@Getter
@Setter
@NoArgsConstructor
public class PayrollQueryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "query_id", nullable = false)
    private Long queryId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /** {@code XLSX} or {@code PDF}. */
    @Column(name = "format", nullable = false, length = 4)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(name = "protection", nullable = false, length = 20)
    private PayrollFileProtection protection;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** The encrypted file; {@code null} once purged. */
    @Column(name = "content", columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @Column(name = "produced_by", nullable = false, length = 100)
    private String producedBy;

    @Column(name = "downloads", nullable = false)
    private Integer downloads;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;

    @Column(name = "purged_at")
    private Instant purgedAt;
}
