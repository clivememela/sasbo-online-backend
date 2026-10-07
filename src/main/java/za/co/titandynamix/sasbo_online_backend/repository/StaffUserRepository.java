package za.co.titandynamix.sasbo_online_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Staff who sign in (V65). The counters a sign-in moves -- the attempts reserved, the lock, the last sign-in -- are
 * written by bulk statements, never through the entity: they are atomic under parallel guessing, and they leave
 * {@code version} alone, so a failed sign-in never makes an administrator's open edit of the user stale.
 */
public interface StaffUserRepository extends JpaRepository<StaffUser, Long>, JpaSpecificationExecutor<StaffUser> {

    /** The user who signs in as {@code username} (kept in lower case), with their role, permissions and scope. */
    @Query("""
            select u from StaffUser u
              join fetch u.role r
              left join fetch r.permissionRows
              join fetch u.position
              left join fetch u.scopeRows
            where u.username = :username""")
    Optional<StaffUser> findForSignIn(@Param("username") String username);

    /** One user with everything an answer about them names. */
    @Query("""
            select u from StaffUser u
              join fetch u.role r
              left join fetch r.permissionRows
              join fetch u.position
              left join fetch u.scopeRows
            where u.id = :id""")
    Optional<StaffUser> findWithAccess(@Param("id") long id);

    /** A page's users with everything an answer names -- one statement for the page, never one a row. */
    @Query("""
            select distinct u from StaffUser u
              join fetch u.role r
              left join fetch r.permissionRows
              join fetch u.position
              left join fetch u.scopeRows
            where u.id in :ids""")
    List<StaffUser> findAllWithAccess(@Param("ids") Collection<Long> ids);

    boolean existsByUsername(String username);

    /**
     * Whether an active holder of the fixed role (every permission) with scope ALL exists -- the one user who can manage
     * every user. None means the database was edited by hand, and the bootstrap restores one (break-glass).
     */
    @Query("""
            select case when count(u) > 0 then true else false end from StaffUser u
            where u.active = true and u.role.fixed = true and u.scopeType = 'ALL'""")
    boolean existsActiveFullAdministrator();

    /**
     * RESERVES one sign-in attempt BEFORE its password is checked -- one statement, so twenty guesses at once are counted
     * one after the other and only the first {@code limit} are ever judged (the sign-in review, 7 Oct: the count used to
     * follow the check, and every guess already past the first read was judged). Refused -- 0 rows -- while the account is
     * locked; a lock that has run out starts the count again. The attempt that reaches {@code limit} locks the account
     * until {@code until} AT ONCE, before its own password is checked, so no guess slips in beside it; a right password
     * then lifts it ({@link #signedIn}, {@link #forgiveAttempts}). A lock always ends: no row is left counted with no lock
     * to run out. The SET expressions read the row as it was, as SQL has them do.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StaffUser u set
              u.failedAttempts = (case when u.lockedUntil is null then u.failedAttempts else 0 end) + 1,
              u.lockedUntil = case when (case when u.lockedUntil is null then u.failedAttempts else 0 end) + 1 >= :limit
                                   then :until else null end
            where u.id = :id and (u.lockedUntil is null or u.lockedUntil <= :now)""")
    int reserveAttempt(@Param("id") long id, @Param("limit") int limit, @Param("now") Instant now,
                       @Param("until") Instant until);

    /** A right password: the attempts reserved before it are forgotten, and a lock its own attempt set is lifted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StaffUser u set u.failedAttempts = 0, u.lockedUntil = null where u.id = :id")
    int forgiveAttempts(@Param("id") long id);

    /** Until when the user is locked; {@code null} when they are not. */
    @Query("select u.lockedUntil from StaffUser u where u.id = :id")
    Instant lockedUntil(@Param("id") long id);

    /** A sign-in that succeeded: the failures forgotten, the lock lifted, the instant kept. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StaffUser u set u.failedAttempts = 0, u.lockedUntil = null, u.lastSignInAt = :at where u.id = :id")
    int signedIn(@Param("id") long id, @Param("at") Instant at);

    /**
     * A new password: the hash, whether it must be changed at the next sign-in, the failures forgotten and the lock
     * lifted -- an administrator's reset is also how a locked user is let back in before the lock runs out.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StaffUser u set u.passwordHash = :hash, u.mustChangePassword = :mustChange, u.failedAttempts = 0,
              u.lockedUntil = null, u.updatedAt = :at, u.updatedBy = :by
            where u.id = :id""")
    int setPassword(@Param("id") long id, @Param("hash") String hash, @Param("mustChange") boolean mustChange,
                    @Param("at") Instant at, @Param("by") String by);

    /**
     * The active users whose role holds {@code permission}, LOCKED for the rest of the transaction. The last-
     * administrator guard counts them under the lock, so two administrators deactivating each other at once are
     * served one after the other, and the second is refused. Locked in id order, so two such transactions never take
     * the same rows in opposite orders.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from StaffUser u
            where u.active = true
              and u.role.id in (select p.role.id from StaffRolePermission p where p.permission = :permission)
            order by u.id""")
    List<StaffUser> lockActiveHolders(@Param("permission") String permission);
}
