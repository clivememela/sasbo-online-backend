package za.co.titandynamix.sasbo_online_backend.entity.signin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** One region code or area letter a REGION or AREA user may see (V65). */
@Entity
@Table(name = "staff_user_scope_codes", uniqueConstraints = @UniqueConstraint(
        name = "staff_user_scope_codes_once", columnNames = {"user_id", "code"}))
@Getter
@NoArgsConstructor
public class StaffUserScopeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private StaffUser user;

    @Column(name = "code", nullable = false, length = 8)
    private String code;

    StaffUserScopeCode(StaffUser user, String code) {
        this.user = user;
        this.code = code;
    }

    /** Within one user a code is held once: equal by code, which is all a user's set compares. */
    @Override
    public boolean equals(Object other) {
        return other instanceof StaffUserScopeCode row && Objects.equals(code, row.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "StaffUserScopeCode[" + code + "]";
    }
}
