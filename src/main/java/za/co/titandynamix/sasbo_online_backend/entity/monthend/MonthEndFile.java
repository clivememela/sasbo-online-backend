package za.co.titandynamix.sasbo_online_backend.entity.monthend;

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
 * A bank's or a provider's monthly file, produced from a taken month and KEPT ONLY ENCRYPTED (October build; Stephne
 * MEN-05: "a requirement for POPIA"). The password is not here, nor anywhere: it is shown once to the person who
 * produced the file. Written by {@code MonthEndSentRepository}'s statements; this entity exists so that H2's schema
 * has the table.
 */
@Entity
@Table(name = "month_end_files")
@Getter
@NoArgsConstructor
public class MonthEndFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "recipient_code", nullable = false, length = 20)
    private String recipientCode;

    @Column(name = "recipient_name", nullable = false, length = 60)
    private String recipientName;

    @Column(name = "layout", nullable = false, length = 30)
    private String layout;

    @Column(name = "full_list", nullable = false)
    private Boolean fullList;

    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    @Column(name = "format", nullable = false, length = 4)
    private String format;

    @Column(name = "protection", nullable = false, length = 20)
    private String protection;

    @Column(name = "rows_written", nullable = false)
    private Integer rowsWritten;

    @Column(name = "new_rows", nullable = false)
    private Integer newRows;

    @Column(name = "deleted_rows", nullable = false)
    private Integer deletedRows;

    @Column(name = "left_out", nullable = false)
    private Integer leftOut;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "produced_by", nullable = false, length = 100)
    private String producedBy;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @Override
    public String toString() {
        return "MonthEndFile[" + id + ", " + monthStart + ", " + kind + ", " + recipientCode + "]";
    }
}
