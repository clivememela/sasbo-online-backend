import { expect } from 'vitest'

import { blankingDestroyedCode, unreadableDirectories } from './sourceScan.js'

/**
 * The one place this repository decides that a scan actually scanned something.
 *
 * THE FAILURE IT EXISTS TO PREVENT
 * Every guard that works by DISCOVERY -- globbing files, walking the route
 * table, iterating a module's exports -- fails OPEN. A broken walker finds
 * nothing, reports no offenders, and passes. Its symptom is a green run, which
 * is also what success looks like, so the suite cannot detect this about
 * itself.
 *
 * A DISCOVERY GUARD IS TWO ASSERTIONS, NOT ONE
 *   1. Completeness -- by discovery, carrying NO number. "Every route mounts",
 *      "every exported service method rejects on a failed response". It moves
 *      with the codebase and never needs editing.
 *   2. A non-vacuity canary -- a number set deliberately BELOW the real count.
 *      That is this function. It is not a coverage target and must never be
 *      raised to match reality: a floor equal to the actual is fragile without
 *      being stricter, and reddens for reasons nobody caused.
 *
 * RULES
 *   - It asserts on the collection the caller ALREADY HOLDS. It never
 *     re-discovers; re-discovering proves the discoverer works, not that this
 *     guard looked at anything.
 *   - Each floor is measured THROUGH the guard's own discovery, never by a
 *     different tool. A `find` over `src/` and a walker disagree by design.
 *   - It is calibrated with real headroom. A guard that cries wolf gets its
 *     threshold lowered by whoever is unlucky enough to hit it.
 *
 * ITS OWN REDDENING CHANGE
 * Point a caller's discovery at a directory that does not exist. Every caller
 * must go red together. Run once per phase, reverted, and recorded.
 *
 * This is the client counterpart of the server's `NonVacuousScan`, and it is
 * deliberately a separate implementation: three independent repositories cannot
 * share a module, and pretending otherwise creates the sibling-path coupling
 * the plans forbid.
 */
export function assertNonVacuousScan(found, floor, what) {
  const size = Array.isArray(found) ? found.length : found?.size ?? found?.length

  expect(
    size,
    `NON-VACUITY CANARY for ${what}. This is not a coverage target -- it is the ` +
      `assertion that the scan scanned anything at all. Finding fewer than ${floor} ` +
      `means the discovery is broken, not that the codebase shrank. Fix the walker; ` +
      `do not lower the floor. Found ${size}.`,
  ).toBeGreaterThanOrEqual(floor)
}

/**
 * The blanker did not destroy the code it was supposed to preserve.
 *
 * The second half of every source-scanning guard's non-vacuity claim, and it
 * lives here for the same reason the canary does: a contract holds because ONE
 * function enforces it, not because every author remembered. Two call sites had
 * already grown two different failure messages for an identical failure.
 *
 * Takes the offenders the caller already computed via `blankingDestroyedCode`,
 * never a fresh scan -- re-scanning would prove the scanner works, not that
 * THIS guard looked at anything.
 */
export function assertBlankingPreservedCode(destroyed) {
  expect(
    destroyed.map((source) => source.path),
    'The comment blanker emptied a file that had code in it. Every scan built on it is ' +
      'now vacuous -- it will find no offenders and report green, which is also what ' +
      'success looks like. Fix the blanker; do not lower anything.',
  ).toEqual([])
}

/**
 * Nothing was silently dropped from the walk -- the completeness half that a
 * floor cannot provide.
 *
 * 🔑 A FLOOR AND THIS ASSERTION ANSWER DIFFERENT QUESTIONS, and a guard needs
 * both. The floor asks "did the scan find anything" and catches a discovery that
 * broke outright. This asks "did the scan find EVERYTHING it walked over", and
 * catches the subtler one: a subtree removed by an unreadable directory, leaving
 * a scan that is partial rather than empty. A guard with generous headroom --
 * `VOCABULARY_COMPARISONS` is 20 against an actual of 47 -- clears its floor
 * comfortably while missing a third of its population.
 *
 * See `unreadableDirectories()` in `sourceScan.js` for what gets recorded and
 * why the walk finishes instead of throwing where it fails.
 */
export function assertNothingWasSkipped(skipped, what) {
  expect(
    skipped,
    `INCOMPLETE SCAN for ${what}. The walk could not read the directories below, so ` +
      `everything beneath them was left out -- and a scan missing a subtree still ` +
      `clears its floor, still finds no offenders, and still reports success. Fix the ` +
      `access or the path; do not lower the floor, which was never the thing that ` +
      `noticed:\n  ${skipped.join('\n  ')}`,
  ).toEqual([])
}

/**
 * All three preflight assertions a source-scanning guard owes, in one call.
 *
 * 🔴 NINE GUARDS WROTE THIS TRIO OUT BY HAND, AND IT HAD ALREADY DRIFTED. Every scan in this
 * repository must establish three separate things before its findings mean anything — that the
 * walk reached everything (`assertNothingWasSkipped`), that the comment blanker did not empty a
 * file it was supposed to preserve (`assertBlankingPreservedCode`), and that it found enough to
 * be looking at the codebase at all (`assertNonVacuousScan`). Written out per guard, four of the
 * five newest were byte-identical and the fifth had reordered them — which is exactly the drift
 * this file's own header describes: *"Two call sites had already grown two different failure
 * messages for an identical failure."* There were nine.
 *
 * 🔑 ORDER MATTERS AND IS FIXED HERE. Completeness first: a partial walk can still clear a floor
 * with headroom, so checking the floor first would report the wrong cause. The blanker next,
 * because every offender list downstream is computed from its output. The floor last.
 */
export function assertScanIsHonest(sources, floor, what) {
  assertNothingWasSkipped(unreadableDirectories(), what)
  assertBlankingPreservedCode(blankingDestroyedCode(sources))
  assertNonVacuousScan(sources, floor, what)
}

/**
 * A detector convicts every offence it must and acquits every innocent shape.
 *
 * 🔴 BOTH HALVES, BECAUSE EITHER ALONE IS SATISFIED BY A BROKEN DETECTOR. One that matched
 * nothing passes the acquit half; one that matched everything passes the convict half. Six
 * guards wrote this pair out with six different failure messages, and the pattern is the whole
 * basis on which any of their results are believed.
 *
 * `detect` returns the offenders it found in a sample — any array-like; only emptiness is read.
 */
export function assertDetectorCanary(canary, detect, what) {
  expect(
    canary.mustConvict.filter((sample) => detect(sample).length === 0),
    `The ${what} detector stopped recognising an offence it is written to catch. Every scan ` +
      `built on it is now VACUOUS — it will find no offenders and report success, which is ` +
      `also exactly what success looks like. Fix the detector; do not lower anything.`,
  ).toEqual([])

  expect(
    canary.mustAcquit.filter((sample) => detect(sample).length > 0),
    `The ${what} detector convicted a shape that is CORRECT. Fix the detector, never the ` +
      `code it is accusing — a guard that cries wolf gets switched off by whoever is unlucky ` +
      `enough to hit it, and then it protects nothing.`,
  ).toEqual([])
}

/**
 * The floors live in `./floors.js`, which imports nothing from vitest so that
 * the three `scripts/*.mjs` guards running outside the runner can share them.
 * Re-exported here because that is where every existing caller looks for them.
 */
export { FLOORS } from './floors.js'
