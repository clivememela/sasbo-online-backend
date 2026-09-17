package za.co.titandynamix.sasbo_online_backend.entity.member;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.enums.BargainingUnit;
import za.co.titandynamix.sasbo_online_backend.enums.GradingSystem;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "job_grades")
public class JobGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank; // Relationship link

    @Column(name = "grade")
    private String grade;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "description")
    private String description;

    /**
     * Whether the grade is inside the bargaining unit (main, 74fdd09). DERIVED from
     * {@link #bargainingUnit} by {@code JobGradeSeeder} and backfilled by {@code V27}:
     * BU true, NBU false, CONDITIONAL or unknown null. Kept for existing readers; the
     * enum is the source of truth.
     */
    @Column(name = "is_bargaining_council")
    private Boolean isBargainingCouncil;

    /**
     * FALSE for a grade an administrator typed that the master list did not
     * contain (02.5). Captured grades are stored and reviewable, but are NOT
     * offered in the drop-down -- otherwise one typo becomes everyone's option
     * and the field loses the consistency that is its whole purpose.
     *
     * <p>Seeded master-list rows are TRUE; see V20 for the backfill and why the
     * column defaults to FALSE rather than TRUE.
     */
    @Column(name = "verified", nullable = false)
    private Boolean verified = Boolean.FALSE;

    /**
     * Whether this grade places a member inside the bargaining unit (02.6, V21).
     *
     * <p>NULLABLE ON PURPOSE -- null means the union has not told us, which is the
     * true state for nine OM Bank grades and Standard Bank's three manager rows.
     * Inferring a value from the Paterson C-D pattern would be a guess wearing a
     * fact's clothing.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bargaining_unit", length = 16)
    private BargainingUnit bargainingUnit;

    /**
     * What else has to be true, in words, when {@code bargainingUnit} is
     * CONDITIONAL -- "Only Assistant Branch Manager", "subject to the guaranteed
     * package threshold". Shown beside the grade: a rule nobody can read is a rule
     * nobody applies.
     */
    @Column(name = "bargaining_unit_condition", length = 500)
    private String bargainingUnitCondition;

    /** Which scheme this grade belongs to. SBV alone uses two. */
    @Enumerated(EnumType.STRING)
    @Column(name = "grading_system", length = 32)
    private GradingSystem gradingSystem;

    /**
     * The Paterson band letter, STORED rather than parsed from {@code grade}.
     *
     * <p>{@code grade} has at least three notations across the banks on file --
     * "C", "C3", "D Upper" -- so slicing the first character is wrong for two of
     * the three. Null for a bank-specific scheme that has no band.
     */
    @Column(name = "band", length = 8)
    private String band;

    /**
     * Allows clients (and the seed data) to send the grade as a plain string,
     * e.g. {@code "jobGrade": "BA1"}. The resulting instance is transient and is
     * resolved to the persisted, bank-specific row in the service layer before saving.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public JobGrade(String grade) {
        this.grade = grade;
    }
}
