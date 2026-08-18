package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "representatives")
public class Representative {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @Column(name = "first_names")
    private String firstNames;

    @Column(name = "surname")
    private String surname;

    private String personalEmail;
    private String workEmail;
    private String preferredEmail;
    private String phone;

    /** The SASBO member who holds this representative role. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** Area this representative belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    /** The representative's union role (e.g. Branch Chair, Shop Steward). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RepresentativeRole role;

    /**
     * The union structures/positions the representative sits on.
     * A representative can hold multiple positions across different structures (§4.3).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "representative_structures",
            joinColumns = @JoinColumn(name = "representative_id"),
            inverseJoinColumns = @JoinColumn(name = "structure_id")
    )
    private Set<UnionStructure> structures = new HashSet<>();

    @Column(name = "is_elected_rep")
    private Boolean isElectedRep;

    /**
     * Election date — only populated when the role is an elected position (§4.3).
     */
    @Column(name = "election_date")
    private LocalDate electionDate;

    /**
     * Members recruited by this representative.
     * Inverse relationship: Member.recruitmentRepresentative → Representative.members
     */
    @OneToMany(mappedBy = "recruitmentRepresentative", fetch = FetchType.LAZY, cascade = CascadeType.DETACH)
    private Set<Member> members = new HashSet<>();

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Add a union structure/position to this representative.
     * Maintains bidirectional relationship consistency.
     */
    public void addStructure(UnionStructure structure) {
        if (this.structures == null) {
            this.structures = new HashSet<>();
        }
        this.structures.add(structure);
        structure.getRepresentatives().add(this);
    }

    /**
     * Remove a union structure/position from this representative.
     * Maintains bidirectional relationship consistency.
     */
    public void removeStructure(UnionStructure structure) {
        if (this.structures != null) {
            this.structures.remove(structure);
            structure.getRepresentatives().remove(this);
        }
    }

    /**
     * Add a recruited member to this representative's list.
     * Maintains bidirectional relationship consistency.
     */
    public void addMember(Member member) {
        if (this.members == null) {
            this.members = new HashSet<>();
        }
        this.members.add(member);
        member.setRecruitmentRepresentative(this);
    }

    /**
     * Remove a recruited member from this representative's list.
     * Maintains bidirectional relationship consistency.
     */
    public void removeMember(Member member) {
        if (this.members != null) {
            this.members.remove(member);
            member.setRecruitmentRepresentative(null);
        }
    }
}
