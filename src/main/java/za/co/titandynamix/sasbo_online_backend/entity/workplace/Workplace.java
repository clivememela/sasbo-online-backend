package za.co.titandynamix.sasbo_online_backend.entity.workplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.entity.reference.Committee;
import za.co.titandynamix.sasbo_online_backend.entity.reference.SasboArea;
import za.co.titandynamix.sasbo_online_backend.entity.reference.TableCode;

import java.time.LocalDate;

/**
 * A workplace -- a branch or a department of an employer -- as SASBO's branch list of 31 August 2026 prints it
 * (01.2). Known by its R/A number, the list's {@code ACCNO}. An organisation: it names no person and counts no member.
 *
 * <p>Its table code is the one the files prove, and {@code tableCodeBy} says how -- {@code NAME} or {@code SUMS} --
 * or it has none (MD42). {@code committeeCode} is the three characters printed on it, kept as printed: six codes
 * have no committee on the province report, and their workplaces keep the code and no committee.
 * A workplace is never deleted; it is closed, and says since when (MR-ROB-24).
 */
@Entity
@Table(name = "workplaces")
@Getter
@Setter
@NoArgsConstructor
public class Workplace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ra_number", nullable = false, unique = true)
    private Integer raNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** The employer's name as the branch list prints it -- cut at twenty-five characters by the legacy report. */
    @Column(name = "employer_printed", nullable = false, length = 60)
    private String employerPrinted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_code_id")
    private TableCode tableCode;

    @Column(name = "table_code_by", length = 10)
    private String tableCodeBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private SasboArea area;

    @Column(name = "committee_code", nullable = false, length = 3)
    private String committeeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id")
    private Committee committee;

    @Column(name = "staff_complement", nullable = false)
    private Integer staffComplement;

    @Column(name = "cl_code", nullable = false, length = 12)
    private String clCode;

    @Column(name = "closed_on")
    private LocalDate closedOn;
}
