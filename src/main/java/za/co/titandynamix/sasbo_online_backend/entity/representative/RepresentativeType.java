package za.co.titandynamix.sasbo_online_backend.entity.representative;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "representative_types")
public class RepresentativeType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name; // name, matches representative.representative_types

}
