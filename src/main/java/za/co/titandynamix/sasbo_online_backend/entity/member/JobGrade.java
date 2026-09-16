package za.co.titandynamix.sasbo_online_backend.entity.member;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

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

    @Column(name = "is_bargaining_council")
    private Boolean isBargainingCouncil;

    /**
     * Allows clients (and the seed data) to send the grade as a plain string,
     * e.g. { @grade} "jobGrade": "BA1"} . The resulting instance is transient and is
     * resolved to the persisted, bank-specific row in the service layer before saving.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public JobGrade(String grade) {
        this.grade = grade;
    }
}
