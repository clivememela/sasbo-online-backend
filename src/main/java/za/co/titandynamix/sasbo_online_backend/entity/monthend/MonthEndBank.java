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
 * What one bank is sent every month (October build; Stephne MEN-32, MEN-33, MEN-34): the full list or the month's new
 * and deleted members, in which layout. The table codes it is made from are {@link MonthEndBankTableCode}. Written by
 * {@code MonthEndSendSettingsRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "month_end_banks")
@Getter
@NoArgsConstructor
public class MonthEndBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "full_list", nullable = false)
    private Boolean fullList;

    @Column(name = "layout", nullable = false, length = 20)
    private String layout;

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
        return "MonthEndBank[" + code + "]";
    }
}
