package za.co.titandynamix.sasbo_online_backend.entity.workplace;

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
import java.time.LocalTime;

/**
 * A visit to a workplace (month end A.1, V62): the day, who visited, and what the organiser found. Written and read by
 * {@code WorkplaceVisitRepository} through JDBC; the entity is the table's shape for the fast lane, which builds its
 * schema from the entities, and for the guard that holds every table of the arc to one (BR29).
 *
 * <p>Append-only on PostgreSQL by trigger. A seeded row is SASBO's own history, a month the branch list prints: its
 * day is not known, and it carries nothing but the month.
 */
@Entity
@Table(name = "workplace_visits")
@Getter
@Setter
@NoArgsConstructor
public class WorkplaceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workplace_id", nullable = false)
    private Long workplaceId;

    @Column(name = "visited_on", nullable = false)
    private LocalDate visitedOn;

    @Column(name = "day_known", nullable = false)
    private boolean dayKnown;

    @Column(name = "organiser", length = 120)
    private String organiser;

    @Column(name = "time_in")
    private LocalTime timeIn;

    @Column(name = "time_out")
    private LocalTime timeOut;

    @Column(name = "complement_found")
    private Integer complementFound;

    @Column(name = "complement_before")
    private Integer complementBefore;

    @Column(name = "membership_found")
    private Integer membershipFound;

    @Column(name = "non_members")
    private Integer nonMembers;

    @Column(name = "recruited")
    private Integer recruited;

    @Column(name = "non_members_interviewed")
    private Integer nonMembersInterviewed;

    @Column(name = "seeded", nullable = false)
    private boolean seeded;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;
}
