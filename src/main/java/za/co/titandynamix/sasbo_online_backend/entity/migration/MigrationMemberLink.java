package za.co.titandynamix.sasbo_online_backend.entity.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A member a data-migration move created, tied to its batch, the row it came from and the status change it was given
 * (V80) -- what an undo deletes, and how a member is traced to its line in the file. Written by
 * {@code MigrationCommitRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "migration_member_links", uniqueConstraints = {
        @UniqueConstraint(name = "migration_member_links_member_once", columnNames = "member_id"),
        @UniqueConstraint(name = "migration_member_links_row_once", columnNames = "row_id")})
@Getter
@NoArgsConstructor
public class MigrationMemberLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_id", nullable = false)
    private Long rowId;

    @Column(name = "status_change_id")
    private Long statusChangeId;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Override
    public String toString() {
        return "MigrationMemberLink[" + id + ", batch " + batchId + ", row " + rowId + "]";
    }
}
