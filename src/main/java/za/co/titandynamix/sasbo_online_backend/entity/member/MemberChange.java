package za.co.titandynamix.sasbo_online_backend.entity.member;

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

/**
 * One field a save of a member changed (month end 03.5): what it was, what it became, who changed it and when -- and
 * the id of the save, one for every row of one save (ME-22). A capture is one row, {@code captured}.
 *
 * <p>🔒 {@code oldValue} and {@code newValue} can be anything the member record holds, so the service masks identity,
 * passport and account numbers to their last four before they are written -- and this type is NAMED to
 * {@code PiiToStringTest}, which finds PII by the names of fields and knows neither of these (BR31). No
 * {@code toString()} is generated over them: the one below names the row and the field, never a value.
 *
 * <p>Append-only in the database: a trigger refuses an update or a delete of a row (MD46).
 */
@Entity
@Table(name = "member_changes")
@Getter
@Setter
@NoArgsConstructor
public class MemberChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Column(name = "save_id", nullable = false, length = 36)
    private String saveId;

    @Column(name = "field", nullable = false, length = 60)
    private String field;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public MemberChange(Integer memberId, String saveId, String field, String oldValue, String newValue,
                        String changedBy, Instant changedAt) {
        this.memberId = memberId;
        this.saveId = saveId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "MemberChange[" + id + ", member " + memberId + ", " + field + "]";
    }
}
