package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * The holiday calendar's one row (V47): its name, which every run stores, and the last date it is
 * complete for. A month ending after {@code knownUntil} is not prepared (HOLIDAYS_NOT_KNOWN).
 */
@Entity
@Table(name = "acb_calendar")
@Getter
@Setter
@NoArgsConstructor
public class AcbCalendar {

    /** The only row there is. */
    public static final int THE_ROW = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "known_until", nullable = false)
    private LocalDate knownUntil;

    /**
     * Whether a day the bank is closed moves the debits on it, as a public holiday does -- DOD-17's open choice
     * (Friday item 14), TRUE by default (V70). Nullable here only because the fast lane builds the table from this
     * entity.
     */
    @Column(name = "bank_closures_count")
    private Boolean bankClosuresCount;

    /** The setting, reading the fast lane's missing value as V70's default. */
    public boolean bankClosuresCountOrDefault() {
        return !Boolean.FALSE.equals(bankClosuresCount);
    }
}
