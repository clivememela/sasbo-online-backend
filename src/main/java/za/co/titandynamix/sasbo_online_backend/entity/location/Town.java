package za.co.titandynamix.sasbo_online_backend.entity.location;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "towns")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Town {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    @ToString.Exclude
    private City city;

    @OneToMany(mappedBy = "town", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Suburb> suburbs;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Town town = (Town) o;
        return getId() != null && Objects.equals(getId(), town.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Hibernate.getClass(this), getId());
    }
}
