package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;

import java.time.Instant;
import java.util.Optional;

/** Sessions (V66), found by the token's hash -- the token itself is never stored. */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * The session a token's hash names, with its user, the user's role, permissions, position and scope -- ONE
     * statement on the unique index, the only read a signed-in request costs.
     */
    @Query("""
            select s from UserSession s
              join fetch s.user u
              join fetch u.role r
              left join fetch r.permissionRows
              join fetch u.position
              left join fetch u.scopeRows
            where s.tokenHash = :hash""")
    Optional<UserSession> findByTokenHash(@Param("hash") String hash);

    /** Slides the idle limit -- only where it was last moved before {@code before}, so at most once a minute. */
    @Modifying
    @Query("update UserSession s set s.lastSeenAt = :now where s.id = :id and s.lastSeenAt < :before and s.revokedAt is null")
    int touch(@Param("id") long id, @Param("now") Instant now, @Param("before") Instant before);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokedReason = :reason where s.id = :id and s.revokedAt is null")
    int revoke(@Param("id") long id, @Param("now") Instant now, @Param("reason") String reason);

    /** Every session the user still has open -- on deactivation and on a password reset. */
    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId and s.revokedAt is null""")
    int revokeAll(@Param("userId") long userId, @Param("now") Instant now, @Param("reason") String reason);

    /** Every open session of the user but {@code keep} -- on a change of password, the session that changed it stays. */
    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId and s.revokedAt is null and s.id <> :keep""")
    int revokeAllBut(@Param("userId") long userId, @Param("keep") long keep, @Param("now") Instant now,
                     @Param("reason") String reason);

    /** The user's sessions that ended before {@code before} -- cleared at their next sign-in. */
    @Modifying
    @Query("delete from UserSession s where s.user.id = :userId and s.expiresAt < :before")
    int deleteEndedBefore(@Param("userId") long userId, @Param("before") Instant before);
}
