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
     * Representatives who sit on this union structure.
     * Inverse side of the Representative.structures many-to-many.
     */
    @ManyToMany(mappedBy = "structures", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Representative> representatives = new HashSet<>();
}
