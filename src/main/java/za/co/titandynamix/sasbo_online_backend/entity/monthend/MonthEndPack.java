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
 * A statistics pack asked for (month end 04.10): a job -- PREPARING, then READY with its two workbooks, or FAILED
 * saying why. Written by {@code MonthEndPackRepository}'s statements; this entity exists so that H2's schema has the
 * table.
 *
 * <p>The files are declared {@code bytea} by name: without a length Hibernate sizes a {@code byte[]} at 255, and with a
 * large one it declares a {@code blob}, which H2 refuses in PostgreSQL mode (see {@code AcbRunFile}). {@code bytea}
 * is H2's own name for a binary of any length, and PostgreSQL's type. No {@code toString} over the bytes.
 */
@Entity
@Table(name = "month_end_packs")
@Getter
@NoArgsConstructor
public class MonthEndPack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "asked_by", nullable = false, length = 100)
    private String askedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure", length = 300)
    private String failure;

    @Column(name = "statistics_file", columnDefinition = "bytea")
    private byte[] statisticsFile;

    @Column(name = "monthly_file", columnDefinition = "bytea")
    private byte[] monthlyFile;

    @Override
    public String toString() {
        return "MonthEndPack[" + id + ", " + monthStart + ", " + status + "]";
    }
}
