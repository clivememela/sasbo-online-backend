package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.AcbHolidaySource;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One public holiday a debit may not be taken on (V47, D30). The date is the key: a day is a
 * holiday once, whatever it is called.
 */
@Entity
@Table(name = "acb_holidays")
@Getter
@Setter
@NoArgsConstructor
public class AcbHoliday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AcbHolidaySource source;

    /** Who added the day; null for a statutory one. */
    @Column(name = "added_by", length = 100)
    private String addedBy;

    @Column(name = "added_at")
    private Instant addedAt;
}
