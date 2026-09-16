package za.co.titandynamix.sasbo_online_backend.support;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place this repository decides that a scan actually scanned something.
 *
 * <h2>The failure it exists to prevent</h2>
 * Every guard that works by DISCOVERY -- globbing files, walking a package,
 * scanning annotations -- fails OPEN. A broken walker finds nothing, reports no
 * offenders, and passes. Its symptom is a green run, which is also what success
 * looks like, so the suite cannot detect this about itself.
 *
 * <h2>A discovery guard is TWO assertions, not one</h2>
 * <ol>
 *   <li><b>Completeness</b> -- by discovery, carrying NO number. "Every class
 *       found is classified", "every disabled test names a row". It moves with
 *       the codebase and never needs editing.
 *   <li><b>A non-vacuity canary</b> -- a number set deliberately BELOW the real
 *       count. That is this method. It is not a coverage target and must never
 *       be raised to match reality: a floor equal to the actual is fragile
 *       without being stricter, and reddens for reasons nobody caused.
 * </ol>
 * Conflating the two produces exactly that fragile floor. The plans caught it
 * happening in three places on 2026-09-04, not by reviewing the rule but by
 * tabulating every floor against its actual.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>It asserts on <b>the collection the caller already holds</b>. It never
 *       re-discovers -- re-discovering proves the discoverer works, not that
 *       THIS guard looked at anything.
 *   <li>The floor is measured <b>through the guard's own discovery</b>, never by
 *       a different tool. A {@code find} over {@code src/test} and a scanner's
 *       walker disagree by design, and a floor justified by a number the guard
 *       never produces is how a comment goes quietly wrong.
 *   <li>It is calibrated with <b>real headroom</b>. A guard that cries wolf gets
 *       its threshold lowered by whoever is unlucky enough to hit it.
 * </ul>
 *
 * <h2>Its own reddening change</h2>
 * Point a caller's discovery at a directory that does not exist. Every caller
 * must go red together. Run once per phase, reverted, and recorded.
 */
public final class NonVacuousScan {

    private NonVacuousScan() {
    }

    /**
     * Fails if a discovery found suspiciously little to look at.
     *
     * @param found the collection the calling guard is about to assert over --
     *              the same one, not a fresh scan
     * @param floor a count deliberately BELOW the real one, with headroom. Never
     *              a coverage target, never raised to match the actual
     * @param what  what was being scanned, for the failure message
     */
    public static void assertNonVacuousScan(Collection<?> found, int floor, String what) {
        assertThat(found)
                .as("NON-VACUITY CANARY for %s. This is not a coverage target -- it is the "
                        + "assertion that the scan scanned anything at all. Finding fewer than "
                        + "%d means the discovery is broken, not that the codebase shrank. Fix "
                        + "the walker; do not lower the floor", what, floor)
                .hasSizeGreaterThanOrEqualTo(floor);
    }
}
