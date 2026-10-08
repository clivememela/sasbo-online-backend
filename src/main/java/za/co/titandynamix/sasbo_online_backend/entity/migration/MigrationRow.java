package za.co.titandynamix.sasbo_online_backend.entity.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One row of a registered file, staged (V80): raw as it was read, then -- once checked -- the fields as they would be
 * written, the outcome and the problems as codes. It holds ANY value of a member under names no field guard can know,
 * so it is named to {@code PiiToStringTest}, and its {@code toString} carries the row's number and nothing it holds.
 * Written by {@code MigrationRowRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "migration_rows",
        uniqueConstraints = @UniqueConstraint(name = "migration_rows_one_a_line", columnNames = {"batch_id", "row_number"}))
@Getter
@NoArgsConstructor
public class MigrationRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "read_problems", length = 2000)
    private String readProblems;

    @Column(name = "outcome", length = 8)
    private String outcome;

    @Column(name = "error_codes", length = 2000)
    private String errorCodes;

    @Column(name = "warning_codes", length = 2000)
    private String warningCodes;

    @Column(name = "normalised_json", columnDefinition = "text")
    private String normalisedJson;

    @Column(name = "identity_key", length = 13)
    private String identityKey;

    @Column(name = "membership_key", length = 30)
    private String membershipKey;

    @Column(name = "membership_seq")
    private Long membershipSeq;

    @Column(name = "status_kind", length = 1)
    private String statusKind;

    @Column(name = "area_letter", length = 4)
    private String areaLetter;

    @Column(name = "table_code")
    private Integer tableCode;

    @Column(name = "workplace_id")
    private Long workplaceId;

    @Column(name = "suspension_code")
    private Integer suspensionCode;

    @Column(name = "deletion_code")
    private Integer deletionCode;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @Override
    public String toString() {
        return "MigrationRow[" + id + ", batch " + batchId + ", row " + rowNumber + "]";
    }
}
