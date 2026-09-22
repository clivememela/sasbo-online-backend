package za.co.titandynamix.sasbo_online_backend.entity.acb;

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
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One debit in one run: a SNAPSHOT of the member's banking at preparation time, and what became
 * of it.
 *
 * <p>The foreign keys are plain columns rather than associations on purpose. A run holds thousands
 * of lines, and an association to {@code Member} is one lazy load waiting to become a query per
 * line; nothing a line does needs the member entity, because everything the file and the letters
 * print was copied onto the line when it was made.
 *
 * <p>🔒 It carries a member's account number. So it declares {@code @Getter} and {@code @Setter},
 * never a generated {@code toString()}, and nothing logs a line -- only its id.
 */
@Entity
@Table(name = "acb_run_lines")
@Getter
@Setter
@NoArgsConstructor
public class AcbRunLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    /** The earlier unpaid line this presents again; {@code null} for the month's own debit. */
    @Column(name = "re_presents_line_id")
    private Long rePresentsLineId;

    /** True once the run is cancelled: the line no longer claims the unpaid it re-presents. */
    @Column(name = "run_released", nullable = false)
    private Boolean runReleased;

    @Column(name = "file_sequence")
    private Integer fileSequence;

    @Column(name = "membership_number", nullable = false, length = 20)
    private String membershipNumber;

    @Column(name = "surname", nullable = false, length = 100)
    private String surname;

    @Column(name = "initials", length = 10)
    private String initials;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "branch_code", nullable = false, length = 6)
    private String branchCode;

    @Column(name = "account_number", nullable = false, length = 11)
    private String accountNumber;

    @Column(name = "account_type")
    private Integer accountType;

    @Column(name = "debit_day", nullable = false)
    private Integer debitDay;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AcbLineOutcome outcome;

    @Column(name = "unpaid_code", length = 10)
    private String unpaidCode;

    @Column(name = "unpaid_reason", length = 40)
    private String unpaidReason;

    @Column(name = "unpaid_noted_on")
    private LocalDate unpaidNotedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private UnpaidDecision decision;

    /** The member's permission for a further re-debit in one run (D32) -- never logged, never audited (SD3-D). */
    @Column(name = "permission_note", length = 300)
    private String permissionNote;
}
