package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.*;

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
}
