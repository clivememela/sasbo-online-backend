package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The canary gets the treatment it gives everything else.
 *
 * <h2>🔴 Why this file exists</h2>
 * {@link NonVacuousScan} is the single mechanism that stops every
 * discovery-based guard in this repository from passing over nothing. Every one
 * of them calls it, and until this file <b>none of them proved it could
 * fail</b> — so the assertion that all the other assertions are real was itself
 * unasserted, and a change making this method a no-op would have turned every
 * one of them into decoration, with a completely green run.
 *
 * <p>That is the exact shape the class's own javadoc warns about, applied to the
 * class writing the warning: <i>"a broken walker finds nothing, reports no
 * offenders, and passes. Its symptom is a green run, which is also what success
 * looks like."</i>
 *
 * <h2>Why it is not enough that the guards use it</h2>
 * A guard calling {@code assertNonVacuousScan} proves the call compiles. It does
 * not prove the call can redden -- and on a healthy codebase every one of them
 * passes on every run, which means all those green results are equally
 * consistent with the method asserting nothing at all.
 *
 * <p>The suite repository already does this: {@code tests/harness/canary.spec.js}
 * proves its equivalent seven ways. This is that idea, on this side of the
 * fence. The duplication between the two is deliberate and pre-cleared -- three
 * independent repositories cannot import one module without creating the
 * sibling-path coupling both of them forbid.
 */
@DisplayName("the non-vacuity canary")
class NonVacuousScanTest {

    @Nested
    @DisplayName("passes when there is genuinely something to look at")
    class Passes {

        @Test
        @DisplayName("above the floor")
        void aboveTheFloor() {
            assertThatCode(() -> assertNonVacuousScan(List.of(1, 2, 3), 2, "things"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("at exactly the floor -- the boundary, which is inclusive")
        void atTheFloor() {
            // Stated as its own case because off-by-one here is silent in both
            // directions: exclusive, and every guard sitting exactly on its
            // floor reddens for no reason; too lenient, and the floor is one
            // lower than it reads.
            assertThatCode(() -> assertNonVacuousScan(List.of(1, 2), 2, "things"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("on a Set, not only a List")
        void onASet() {
            // The callers pass whatever their discovery produced. Several hold
            // Sets, and a canary that only understood Lists would be a compile
            // error at best and a surprise at worst.
            assertThatCode(() -> assertNonVacuousScan(Set.of("a", "b"), 2, "things"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("fails, which is the half nothing was proving")
    class Fails {

        @Test
        @DisplayName("on an empty collection -- the case it exists for")
        void onEmpty() {
            // 🔑 THE WHOLE POINT. A broken walker returns nothing; every
            // "assert an absence" guard then finds no offenders and passes.
            // This is the assertion that converts that green into a red.
            assertThatThrownBy(() -> assertNonVacuousScan(List.of(), 1, "controllers"))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("one below the floor")
        void justBelowTheFloor() {
            assertThatThrownBy(() -> assertNonVacuousScan(List.of(1), 2, "controllers"))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("naming what was being scanned, so the failure is actionable")
        void namesTheSubject() {
            // A canary that fails with "expected size to be at least 30" sends
            // the reader to the wrong place. Naming the population is what makes
            // the message point at the walker.
            assertThatThrownBy(() -> assertNonVacuousScan(List.of(), 30, "mapper classes"))
                    .hasMessageContaining("mapper classes");
        }

        @Test
        @DisplayName("saying fix the walker rather than lower the floor")
        void saysFixTheWalker() {
            // 🔑 THE MESSAGE IS LOAD-BEARING, and this asserts it verbatim. A
            // floor that reads as a coverage target is a floor somebody lowers
            // the first time it is inconvenient -- which is precisely how a
            // canary stops being one, and it happens during a hurried fix when
            // nobody is reading the class javadoc.
            assertThatThrownBy(() -> assertNonVacuousScan(List.of(), 1, "anything"))
                    .hasMessageContaining("do not lower the floor")
                    .hasMessageContaining("NON-VACUITY CANARY");
        }

        @Test
        @DisplayName("and it says the codebase did not shrink, because that is the wrong conclusion")
        void saysTheDiscoveryIsBroken() {
            // The reader's first instinct on a red floor is "we deleted some
            // classes". The message has to say otherwise or the fix is a lower
            // number.
            assertThatThrownBy(() -> assertNonVacuousScan(List.of(), 5, "anything"))
                    .hasMessageContaining("the discovery is broken");
        }
    }
}
