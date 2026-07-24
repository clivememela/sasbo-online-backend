package za.co.titandynamix.sasbo_online_backend.entity.codes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

@Entity
@Table(name = "suspension_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuspensionCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SuspensionCode suspensionCode = (SuspensionCode) o;
        return getId() != null && Objects.equals(getId(), suspensionCode.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(Hibernate.getClass(this), getId());
    }
}
