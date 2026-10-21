package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A region's pack, produced from a taken month (October build; Stephne MEN-02, MEN-05, MEN-08): the set it was made
 * for as the set stood, and the pack as a PDF and as a workbook. Written by {@code MonthEndSentRepository}'s statements;
 * this entity exists so that H2's schema has the table.
 *
 * <p>The files are declared {@code bytea} by name, as the statistics pack's are, and no {@code toString} goes near
 * them: a pack names members.
 */
@Entity
@Table(name = "month_end_region_packs", uniqueConstraints = @UniqueConstraint(
        name = "month_end_region_packs_one_a_generation", columnNames = {"month_id", "set_code", "generation"}))
@Getter
@NoArgsConstructor
public class MonthEndRegionPack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "set_code", nullable = false, length = 8)
    private String setCode;

    /** 1 for a take's first pack for the set, one more for each asked for again. */
    @Column(name = "generation", nullable = false)
    private Integer generation;

    @Column(name = "set_label", nullable = false, length = 60)
    private String setLabel;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    @Column(name = "area_letters", length = 60)
    private String areaLetters;

    @Column(name = "position", nullable = false, length = 60)
    private String position;

    @Column(name = "joined", nullable = false)
    private Integer joined;

    @Column(name = "left_count", nullable = false)
    private Integer leftCount;

    @Column(name = "suspended", nullable = false)
    private Integer suspended;

    @Column(name = "pdf_name", nullable = false, length = 100)
    private String pdfName;

    @Column(name = "pdf_file", nullable = false, columnDefinition = "bytea")
    private byte[] pdfFile;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "xlsx_name", nullable = false, length = 100)
    private String xlsxName;

    @Column(name = "xlsx_file", nullable = false, columnDefinition = "bytea")
    private byte[] xlsxFile;

    @Column(name = "xlsx_sha256", nullable = false, length = 64)
    private String xlsxSha256;

    @Column(name = "produced_by", nullable = false, length = 100)
    private String producedBy;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @Override
    public String toString() {
        return "MonthEndRegionPack[" + id + ", " + monthStart + ", " + setCode + "]";
    }
}
