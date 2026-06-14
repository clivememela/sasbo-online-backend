package za.co.titandynamix.sasbo_online_backend.entity.location;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "provinces")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Province {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String code; // e.g., "GP", "WC"

    @OneToMany(mappedBy = "province", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<City> cities;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Province province = (Province) o;
        return getId() != null && Objects.equals(getId(), province.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Hibernate.getClass(this), getId());
    }
}

