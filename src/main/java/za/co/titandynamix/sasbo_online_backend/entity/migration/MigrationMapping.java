package za.co.titandynamix.sasbo_online_backend.entity.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A mapping definition defined here (V80): from a file's columns to member fields. The built-in ones are resources and
 * never rows. Written by {@code MigrationMappingRepository}'s statements; this entity exists so that H2's schema has the
 * table.
 */
@Entity
@Table(name = "migration_mappings")
@Getter
@NoArgsConstructor
public class MigrationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mapping_key", nullable = false, unique = true, length = 60)
    private String mappingKey;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "definition", nullable = false, columnDefinition = "text")
    private String definition;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "MigrationMapping[" + mappingKey + ", v" + version + "]";
    }
}
