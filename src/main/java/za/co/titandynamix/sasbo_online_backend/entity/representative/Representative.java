package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.Area;
import za.co.titandynamix.sasbo_online_backend.entity.Member;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "representatives")
public class Representative {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    private String email;
    private String phone;

    /** The SASBO member who holds this representative role. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** Area this representative belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    /** The representative's union role (e.g. Branch Chair, Shop Steward). */
    @Column(name = "rep_role")
    private String repRole;

    /**
     * The union structure the representative sits on.
     * Only applicable when the role requires it (§4.3).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private UnionStructure structure;

    /**
     * Election date — only populated when the role is an elected position (§4.3).
     */
    @Column(name = "election_date")
    private LocalDate electionDate;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
