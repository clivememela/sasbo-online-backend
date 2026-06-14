package za.co.titandynamix.sasbo_online_backend.entity.location;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Table(name = "suburbs")
public class Suburb {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String postalCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "town_id", nullable = false)
    @ToString.Exclude
    private Town town;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Suburb suburb = (Suburb) o;
        return getId() != null && Objects.equals(getId(), suburb.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Hibernate.getClass(this), getId());
    }
}
