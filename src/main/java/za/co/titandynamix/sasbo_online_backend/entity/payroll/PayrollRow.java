package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line read from a payroll file. V74.
 *
 * <p>🔒 The only table holding the people on a file -- staff numbers, ID numbers, names, and the whole line as printed
 * in {@code raw}. Written in batches by {@code PayrollStore}; deleted with the file's content after a year. Getters and
 * setters only: no generated {@code toString} over an ID number.
 */
@Entity
@Table(name = "payroll_rows", uniqueConstraints = @UniqueConstraint(
        name = "payroll_rows_one_per_line", columnNames = {"file_id", "line_number"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /** The line's row on the sheet, counted from 1 as a person counts them. */
    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "staff_number", length = 40)
    private String staffNumber;

    @Column(name = "staff_key", length = 40)
    private String staffKey;

    @Column(name = "id_number", length = 20)
    private String idNumber;

    @Column(name = "initials", length = 20)
    private String initials;

    @Column(name = "surname", length = 100)
    private String surname;

    /** Every reading of the name as initials and a surname key, joined -- the name styles differ by employer. */
    @Column(name = "name_key", length = 400)
    private String nameKey;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /** The month the line says it deducts for, where the layout prints one; earlier than the file's is a catch-up. */
    @Column(name = "for_period")
    private LocalDate forPeriod;

    @Column(name = "valid", nullable = false)
    private Boolean valid;

    @Column(name = "problem", length = 200)
    private String problem;

    @Column(name = "raw", length = 4000)
    private String raw;
}
