package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A benefit provider's monthly list (October build; Stephne MEN-35): Old Mutual's funeral benefit and Law Call go out
 * monthly, Law Call onto the provider's SFTP server; the others are suspended, kept and switched off. Written by
 * {@code MonthEndSendSettingsRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "month_end_providers")
@Getter
@NoArgsConstructor
public class MonthEndProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "layout", nullable = false, length = 30)
    private String layout;

    @Column(name = "delivery", nullable = false, length = 10)
    private String delivery;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "MonthEndProvider[" + code + "]";
    }
}
