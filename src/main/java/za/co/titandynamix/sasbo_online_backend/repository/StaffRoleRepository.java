package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;

import java.util.List;
import java.util.Optional;

/** Roles and the permissions they hold (V65). */
public interface StaffRoleRepository extends JpaRepository<StaffRole, Long> {

    @Query("select r from StaffRole r left join fetch r.permissionRows where r.code = :code")
    Optional<StaffRole> findByCodeWithPermissions(@Param("code") String code);

    boolean existsByCode(String code);

    /** Every role with its permissions, by name -- a set an administrator keeps, never a growing one. */
    @Query("select distinct r from StaffRole r left join fetch r.permissionRows order by r.name, r.code")
    List<StaffRole> findAllWithPermissions();

    /** The one fixed role: the System Administrator's. */
    @Query("select r from StaffRole r left join fetch r.permissionRows where r.fixed = true")
    Optional<StaffRole> findFixed();
}
