package za.co.titandynamix.sasbo_online_backend.entity.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * For each of SASBO's lists, and for the workplaces: the report it was read from, the day that report was printed,
 * the day SASBO confirmed it -- empty until she has (MD28) -- and what the seeder did with it (ME-18). An outcome is
 * {@code SEEDED} with how many rows, or {@code FAILED} with one of four words; never an exception's name and never a
 * row's content (MR-SEC-21).
 *
 * <p>From the day {@code confirmedOn} is set the database owns the list, and the seeder leaves it alone (MR-ROB-20).
 */
@Entity
@Table(name = "reference_lists")
@Getter
@Setter
@NoArgsConstructor
public class ReferenceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_key", nullable = false, unique = true, length = 40)
    private String listKey;

    @Column(name = "report", nullable = false, length = 200)
    private String report;

    /** Empty only where the resource itself could not be read, so the day it was printed is not known. */
    @Column(name = "printed_on")
    private LocalDate printedOn;

    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;

    @Column(name = "outcome", length = 20)
    private String outcome;

    @Column(name = "failure", length = 20)
    private String failure;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "recorded_at")
    private Instant recordedAt;
}
