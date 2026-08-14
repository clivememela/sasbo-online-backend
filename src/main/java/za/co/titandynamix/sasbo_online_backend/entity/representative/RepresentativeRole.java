package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a formal Union role that a Representative may hold.
 * Examples: Branch Chair, Shop Steward, Regional Coordinator, etc.
 *
 * A RepresentativeRole may require:
 * - A specific UnionStructure (e.g., branch rep must sit on Branch Committee)
 * - An Election Date (if the role is an elected position)
 */
@Entity
@Table(name = "representative_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepresentativeRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Whether representatives in this role must sit on a Union structure.
     * If true, a UnionStructure must be specified when creating/updating a Representative.
     */
    @Builder.Default
    private Boolean requiresStructure = false;

    /**
     * Whether representatives in this role must have an election date.
     * If true, an electionDate must be specified when creating/updating a Representative.
     */
    @Builder.Default
    private Boolean requiresElectionDate = false;

    /**
     * Description or notes about this role (optional).
     */
    @Column(length = 500)
    private String description;
}
