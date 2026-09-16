package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.Floors;
import za.co.titandynamix.sasbo_online_backend.support.CommittedSecrets;
import za.co.titandynamix.sasbo_online_backend.support.CommittedSecrets.Exclusion;
import za.co.titandynamix.sasbo_online_backend.support.CommittedSecrets.Finding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * No secret gets committed to this repository.
 *
 * <h2>Why this is P4's, and why it runs in all three repositories</h2>
 * P4 is already the phase that scans committed source on both sides of the
 * contract, and it already owns the comment-blanking machinery a source scan
 * needs. A credential guard that exists in one repository and not the others
 * protects the repository that happened to get it.
 *
 * <p>The browser's half of this found a live Google API key in
 * {@code client/.env} and carries it on a named allowlist. This repository's
 * allowlist is <b>empty, and asserted empty</b> -- which is a claim about the
 * repository, not an absence of one.
 *
 * <p>Fast lane: no Docker, no database, no Spring context. It shells out to
 * {@code git ls-files} and reads text.
 */
@DisplayName("Committed secrets")
class CommittedSecretsTest {

    // Held by CommittedSecrets itself, which reads once per JVM. These were
    // instance fields, and JUnit's default lifecycle builds a fresh instance
    // per test method -- so six methods meant six repository scans and twelve
    // `git ls-files` spawns, in the lane that runs on every commit.
    private static final List<String> tracked = CommittedSecrets.trackedFiles();
    private static final List<Finding> findings = CommittedSecrets.unallowedFindings();

    @Test
    @DisplayName("the scan asked git for the tracked files and got a repository back")
    void theScanFoundFilesToLookAt() {
        // What this catches is `git ls-files` returning nothing -- in a
        // detached checkout, or with git absent from the PATH. That reports no
        // secrets and passes, which is the failure mode of every discovery
        // guard: green because it looked at nothing.
        assertNonVacuousScan(tracked, Floors.TRACKED_FILES, "files tracked by git in this repository");
    }

    @Test
    @DisplayName("it scans source files rather than excluding everything")
    void theExclusionsDoNotSwallowTheRepository() {
        List<String> scanned = tracked.stream().filter(path -> !CommittedSecrets.isExcluded(path))
                .toList();

        // The mirror image of the exclusion list. An over-broad exclusion
        // silences this guard exactly as effectively as a raised threshold, and
        // the finding count alone would never show it.
        assertNonVacuousScan(scanned, Floors.TRACKED_FILES, "files actually scanned after path exclusions");
        assertThat(scanned).anyMatch(path -> path.endsWith(".java"));
        assertThat(scanned).anyMatch(path -> path.endsWith(".yaml") || path.endsWith(".yml"));
    }

    @Test
    @DisplayName("every path exclusion carries its justification")
    void everyExclusionIsJustified() {
        // An exclusion nobody can see is where the real key hides next time.
        assertThat(CommittedSecrets.PATH_EXCLUSIONS).isNotEmpty();
        for (Exclusion exclusion : CommittedSecrets.PATH_EXCLUSIONS) {
            assertThat(exclusion.why())
                    .as("%s has no justification", exclusion.match())
                    .isNotBlank()
                    .hasSizeGreaterThan(30);
        }
    }

    @Test
    @DisplayName("the lockfile and build output are excluded BEFORE anything is scanned")
    void theOrderOfExclusionIsTheDesign() {
        // 🔴 Scanning first and filtering after would produce a first run with
        // hundreds of findings, and a run like that does not get triaged -- it
        // gets silenced, usually by loosening the very heuristic that found the
        // real one. Scope the input; never raise the threshold.
        assertThat(CommittedSecrets.isExcluded("gradle.lockfile")).isTrue();
        assertThat(CommittedSecrets.isExcluded("build/libs/app.jar")).isTrue();
        assertThat(CommittedSecrets.isExcluded("gradle/wrapper/gradle-wrapper.properties")).isTrue();
        assertThat(CommittedSecrets.isExcluded(
                "src/main/java/za/co/titandynamix/sasbo_online_backend/config/SecurityConfig.java"))
                .isFalse();
    }

    @Test
    @DisplayName("the allowlist is empty, and that is asserted rather than assumed")
    void theAllowlistIsEmpty() {
        // An entry appearing here later is a decision about a real credential in
        // a real repository. Asserting the list is empty turns that into a
        // review conversation instead of a quiet edit.
        assertThat(CommittedSecrets.ALLOWLIST)
                .as("Someone has allowlisted a file in this repository. That needs a register "
                        + "row and a reviewer, not a commit. The browser's repository has "
                        + "exactly one entry (client/.env, defect B2) and it is named there.")
                .isEmpty();
    }

    @Test
    @DisplayName("no secret is committed anywhere in this repository")
    void noSecretIsCommitted() {
        List<String> unexpected = findings.stream()
                .map(finding -> finding.at() + "  [" + finding.signature() + "]")
                .toList();

        assertThat(unexpected)
                .as("A secret has been COMMITTED. It is now on every clone, every fork and "
                        + "every backup of this repository, and deleting the file does not "
                        + "remove it from history. Rotate the credential first -- that is the "
                        + "only step that actually revokes it -- then remove it from the tree. "
                        + "(Findings on an allowlisted path are excluded here; the allowlist "
                        + "is separately asserted empty for this repository.)")
                .isEmpty();
    }
}
