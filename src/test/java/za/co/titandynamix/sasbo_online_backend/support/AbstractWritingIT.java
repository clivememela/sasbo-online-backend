package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for every integration test that <b>writes</b>. Extend this instead
 * of {@link AbstractPostgresIT} the moment a test inserts a row.
 *
 * <h2>The flake it exists to prevent, which was real</h2>
 * Resetting in {@code @BeforeEach} alone is the obvious pattern and it is not
 * enough. It guarantees a class never <i>inherits</i> another's rows -- and says
 * nothing about what it <i>leaves behind</i>. After the last test in a writing
 * class, its rows are still there, committed, for whatever runs next.
 *
 * <p>Observed on 2026-09-04: the harness self-test asserts that the members
 * table is empty, because {@code DataSeeder} must be neutralised. It began
 * failing with {@code expected: 0 but was: 1} -- roughly one run in five, and
 * only under load. The cause was not {@code DataSeeder}. It was the new
 * {@code MemberRepositoryIT} finishing its last test and leaving one member
 * behind, which the self-test then counted.
 *
 * <p>A resource lock does not help with this: locks stop two classes running at
 * the same time, and this is a class reading state a previous class committed
 * and never cleaned up. <b>The lock orders them; it does not clean up after
 * them.</b>
 *
 * <h2>So the reset runs on both sides</h2>
 * Before, so nothing is inherited; after, so nothing is left. Together they make
 * "every test seeds and cleans its own state" a property of the harness rather
 * than something each author has to remember -- and remembering is exactly what
 * failed here, in a suite whose own base class already said in its comment that
 * every subclass "must leave it as it found it".
 *
 * <p>The extra {@code TRUNCATE} is cheap: the tables are small, it is one
 * statement for all of them, and the container has {@code fsync=off}.
 *
 * <h2>It carries the lock, too</h2>
 * {@code @ResourceLock} is inherited, so a subclass gets it without saying so.
 * A reset is a {@code TRUNCATE} across every table, and while one runs, a
 * sibling reading a count sees zero.
 */
@ResourceLock(value = DatabaseReset.SHARED_DATABASE, mode = ResourceAccessMode.READ_WRITE)
public abstract class AbstractWritingIT extends AbstractPostgresIT {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetBeforeSoNothingIsInherited() {
        DatabaseReset.reset(this.jdbc);
    }

    @AfterEach
    void resetAfterSoNothingIsLeftBehind() {
        DatabaseReset.reset(this.jdbc);
    }
}
