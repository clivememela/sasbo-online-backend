package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "union_structures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnionStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Parent structure for hierarchical positioning (e.g. "BEC Executive" → "NFC/SJC").
     * Null means this is a root-level structure/position.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private UnionStructure parent;

    /**
     * Whether a member sitting on this structure must provide an election date.
     * E.g. Shop Steward is an elected position.
     */
    @Column(name = "requires_election_date")
    @Builder.Default
    private Boolean requiresElectionDate = false;

    /**
     * Representatives who sit on this union structure.
     * Inverse side of the Representative.structures many-to-many.
     */
    @ManyToMany(mappedBy = "structures", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Representative> representatives = new HashSet<>();
}
