package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migrations on disk are exactly the migrations the chain says it applies.
 *
 * <h2>Why this is separate from the chain-content assertions</h2>
 * {@code MigrationChainContentTest} asserts what a database looks like after
 * the chain has run: it needs Docker, a container, and two minutes. This needs
 * a directory listing. Both compare against the same specification --
 * {@link MigrationSeed#MIGRATIONS} -- and they catch different halves of the
 * same mistake:
 *
 * <ul>
 *   <li><b>Here, in milliseconds:</b> a migration was added, renamed or deleted
 *       and the specification was not updated. The failure names the file.
 *   <li><b>There, needing a database:</b> a migration exists and did not apply,
 *       or applied and failed.
 * </ul>
 *
 * <p>🔑 Without this test the first mistake surfaces only in the integration
 * lane, as a chain-content failure listing version numbers -- which reads as
 * "the database is wrong" when the truth is "somebody added a file and forgot
 * to say so". Same fact, one lane earlier, named properly.
 *
 * <h2>🔴 The direction that matters</h2>
 * This is a CROSS-CHECK BETWEEN TWO INDEPENDENT DECLARATIONS, never a
 * derivation. If the expected list were built by listing the directory, then
 * deleting a migration would delete its own expectation and this test would
 * pass over the loss -- a green run about a chain that no longer exists. The
 * specification is written by hand in {@code MigrationSeed} for exactly that
 * reason, and the two are compared here.
 */
@DisplayName("migration inventory")
class MigrationInventoryTest {

    /**
     * Not under {@code build/}: the source tree is what a reviewer edits and
     * what git tracks, and a stale copy in an output directory would let this
     * pass against a file nobody has any more.
     */
    private static final Path MIGRATION_DIR =
            Paths.get("src", "main", "resources", "db", "migration");

    @Test
    @DisplayName("every migration on disk is declared, and every declared migration exists")
    void diskAndSpecificationAgree() throws IOException {
        List<String> onDisk;
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            onDisk = files
                    .map((p) -> p.getFileName().toString())
                    .filter((name) -> name.endsWith(".sql"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        // 🔑 NO NON-VACUITY FLOOR HERE, deliberately, and that is worth saying
        // because every other discovery guard in this repository has one.
        //
        // A floor exists for a scan whose EXPECTED side can also be empty: find
        // nothing, compare against nothing, pass. This comparison's expected
        // side is `MigrationSeed.MIGRATIONS`, a hand-written list that is never
        // empty -- so a walk returning nothing already fails below, naming
        // every missing file, and `Files.list` throws outright if the directory
        // has moved. A floor would add a second number to maintain and catch
        // nothing the assertion does not.
        //
        // Sorted on both sides rather than compared in order: this test's
        // subject is WHICH migrations exist, and Flyway decides the running
        // order from the version, not from a directory listing. The order the
        // chain actually ran in is asserted where it can be observed --
        // MigrationChainContentTest reads flyway_schema_history by
        // installed_rank.
        assertThat(onDisk)
                .as("the .sql files under %s versus MigrationSeed.MIGRATIONS. A file here and "
                        + "not there is a migration nobody declared; a name there and not here "
                        + "is a migration that has been deleted or renamed, and the chain will "
                        + "stop wherever it depended on it",
                        MIGRATION_DIR)
                .containsExactlyElementsOf(
                        MigrationSeed.MIGRATIONS.stream().sorted(Comparator.naturalOrder()).toList());
    }

    @Test
    @DisplayName("no two migrations claim the same version")
    void versionsAreUnique() {
        // 🔴 THE FAILURE THIS EXISTS FOR HAS ALREADY HAPPENED ONCE, and nothing
        // caught it until a Spring context refused to start.
        //
        // Two files -- V1__initial_schema.sql and V1__baseline_schema.sql --
        // both claimed version 1, on two branches that merged CLEANLY because
        // they are different filenames. Git has no opinion about it. Flyway
        // then refuses with "Found more than one migration with version 1",
        // which arrives as a failure to boot rather than as a failing test, so
        // every suite reddens at once and none of the reds names the cause.
        //
        // A merge is exactly how it happens, which is why this is asserted over
        // the DECLARED list: it reddens in the fast lane on the merge commit,
        // before anything is run.
        assertThat(MigrationSeed.APPLIED_VERSIONS)
                .as("Flyway versions parsed from MigrationSeed.MIGRATIONS. Two migrations at "
                        + "one version is a chain that cannot start, and it merges cleanly")
                .doesNotHaveDuplicates();
    }
}
