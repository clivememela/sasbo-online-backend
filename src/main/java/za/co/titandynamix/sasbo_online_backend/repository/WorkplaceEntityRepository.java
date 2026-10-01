package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.workplace.Workplace;

/**
 * A workplace as an ENTITY -- for the one thing that needs it: a member's association to their workplace (month end
 * 01.3), resolved from the {@code workplaceId} a save names. Every READ of the workplaces is a statement of
 * {@link WorkplaceRepository}'s, straight into its DTO; nothing here lists them.
 */
public interface WorkplaceEntityRepository extends JpaRepository<Workplace, Long> {
}
