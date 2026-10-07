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
 * An organiser's visit before it is SASBO's record (V69_1): saved as a draft, changed until it is submitted, and then
 * read-only, keeping the id of the visit its submission appended to {@code workplace_visits}. Written and read by
 * {@code WorkplaceVisitDraftRepository} through JDBC, one statement each; the entity is the table's shape for the fast
 * lane, which builds its schema from the entities, and for the integration lane's schema validation.
 *
 * <p>{@code version} is the optimistic lock, compared and moved by the repository's own statements -- so it is a plain
 * column here, not JPA's {@code @Version}, which nothing writes through.
 */
@Entity
@Table(name = "workplace_visit_drafts")
@Getter
@Setter
@NoArgsConstructor
public class WorkplaceVisitDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workplace_id", nullable = false)
    private Long workplaceId;

    /** {@code DRAFT} or {@code SUBMITTED}. */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "visited_on")
    private LocalDate visitedOn;

    @Column(name = "organiser", length = 120)
    private String organiser;

    @Column(name = "time_in")
    private LocalTime timeIn;

    @Column(name = "time_out")
    private LocalTime timeOut;

    @Column(name = "complement_found")
    private Integer complementFound;

    @Column(name = "membership_found")
    private Integer membershipFound;

    @Column(name = "non_members")
    private Integer nonMembers;

    @Column(name = "recruited")
    private Integer recruited;

    @Column(name = "non_members_interviewed")
    private Integer nonMembersInterviewed;

    /** The username of whoever saved it: "my drafts". */
    @Column(name = "owner", nullable = false, length = 100)
    private String owner;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "visit_id")
    private Long visitId;

    @Column(name = "version", nullable = false)
    private int version;
}
