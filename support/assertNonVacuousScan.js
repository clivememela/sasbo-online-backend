import { expect } from '@playwright/test'

/**
 * The one place this repository decides that a scan actually scanned something.
 *
 * THE FAILURE IT EXISTS TO PREVENT
 * Every guard that works by DISCOVERY -- globbing `.feature` files, counting
 * scenarios, walking a directory -- fails OPEN. A broken glob matches nothing,
 * finds no offenders, and reports a green run of zero scenarios. Its symptom is
 * a green run, which is also what success looks like, so the suite cannot
 * detect this about itself. A renamed directory or a changed `testDir` is
 * enough to cause it.
 *
 * A DISCOVERY GUARD IS TWO ASSERTIONS, NOT ONE
 *   1. Completeness -- by discovery, carrying NO number. "Every feature file
 *      compiles", "every scenario has a step definition". It moves with the
 *      suite and never needs editing.
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
 *     different tool. A `find` over `features/` and Playwright's own collection
 *     disagree by design.
 *   - It is calibrated with real headroom. A guard that cries wolf gets its
 *     threshold lowered by whoever is unlucky enough to hit it.
 *   - 🔑 ONE EXCEPTION, stated here so the entries below cite it rather than
 *     re-arguing it each time: over a CLOSED SET -- one whose growth would be a
 *     deliberate act rather than the codebase moving -- the floor is pinned
 *     EXACTLY to the actual. There is no "discovery drifting upward" to leave
 *     headroom for, and an exact number then catches a member going missing as
 *     well as the scan breaking. `BLANKABLE_HOST_PORTS` and
 *     `ORCHESTRATOR_FILES` are the two.
 *
 * ITS OWN REDDENING CHANGE
 * Point a caller's discovery at a directory that does not exist. Every caller
 * must go red together. Run once per phase, reverted, and recorded.
 *
 * This is the third of three: the client and the server each have their own
 * counterpart, deliberately duplicated rather than shared. Three independent
 * repositories cannot import one module without creating exactly the
 * sibling-path coupling this suite forbids everywhere else.
 */
export function assertNonVacuousScan(found, floor, what) {
  const size = Array.isArray(found) ? found.length : (found?.size ?? found?.length)

  expect(
    size,
    `NON-VACUITY CANARY for ${what}. This is not a coverage target -- it is the ` +
      `assertion that the scan scanned anything at all. Finding fewer than ${floor} ` +
      `means the discovery is broken, not that the suite shrank. Fix the walker; ` +
      `do not lower the floor. Found ${size}.`,
  ).toBeGreaterThanOrEqual(floor)
}

/**
 * The floors, in one place, each measured through its own guard's discovery.
 *
 * Every number here EXPIRES. It is re-derived when the suite changes, never
 * carried forward on trust, and the date it was measured is part of the record.
 */
export const FLOORS = {
  /**
   * `.feature` files discovered by the feature floor.
   * Actual on 2026-09-06: 5 -- banks and branches, member capture, the member
   * register, subscriptions and A.C.B., and the serial backend-down journeys.
   *
   * Floor 4: the four areas the plans promised a report on, which is the number
   * that would be WRONG to be missing rather than a target to grow past. The
   * previous value of 1 was a stated exception -- with one feature file the only
   * number below one is zero, and a floor of zero is the vacuous pass this
   * module exists to prevent. That exception has now expired: there is real
   * headroom, and the floor means what floors here are supposed to mean.
   *
   * Re-derived 2026-10-02, when month end's journeys came: 9 -- with A.C.B.'s two,
   * month end's page and the first screen with the backend down -- and 12 when they
   * merged (suite `bf4d8c8`), month end's figures, statuses and workplaces having
   * come. The floor stays.
   */
  FEATURES: 4,

  /**
   * Scenarios discovered by the scenario floor.
   * Actual on 2026-09-06: 34 across five feature files, of which 18 run and 16
   * are skipped defect assertions. Floor 12 -- the phase's stated minimum, and
   * well below the actual. Never edited up to match a new actual: a floor equal
   * to the count is fragile without being stricter.
   *
   * Re-derived 2026-10-02, through `featureFloors.spec.js`'s own discovery, when
   * month end's journeys came: 62 scenarios in 9 feature files -- and 89 in 12 when
   * they merged (suite `bf4d8c8`). The floor stays.
   */
  SCENARIOS: 12,

  /**
   * Scenarios in `features/monthly/` -- month end's own project.
   *
   * 🔴 THE TWO FLOORS ABOVE CANNOT SEE THIS ONE GO EMPTY. `journeys-monthly` compiles
   * its own directory; if that glob stopped matching, the project would run no
   * scenario and report green, while every other project's scenarios still cleared
   * the suite-wide floors by forty (55 against 12, at `bf4d8c8`). So the project
   * carries a floor of its own.
   * Actual on 2026-10-02: 7 in one feature file, measured through
   * `featureFloors.spec.js`'s discovery -- and 34 in four by the end of that day,
   * the first screen's figures, the status and its history, and the workplaces
   * having come. Floor 3, with the headroom the rules ask for, and never edited up
   * to match a new actual.
   */
  MONTHLY_SCENARIOS: 3,

  /**
   * Scenarios in `features/home/` -- the first screen's own directory (October build: client `oct/home` shows the
   * modules as cards, a list or a table, and nothing else).
   *
   * 🔑 A NEW FEATURE DIRECTORY GETS A FLOOR OF ITS OWN, and no floor above is re-aimed to make room -- the condition the
   * automation arc set for the October build's suite work (sasbo-online-5c, 2026-10-06), on the reasoning of
   * MONTHLY_SCENARIOS above: the directory runs in `journeys`, and the suite-wide floors cannot see one directory go
   * empty while every other still clears them.
   * Actual on 2026-10-07: 3 in one feature file, measured through `featureFloors.spec.js`'s discovery. Floor 1: with
   * three, zero is the only count that says the discovery broke. Never edited up to match a new actual.
   */
  HOME_SCENARIOS: 1,

  /**
   * Scenarios in `features/signin/` -- signing in (client and server `oct/signin`): the open door, a demonstration user
   * by name and position, signing out, an administrator creating a user, a refusal. A new feature directory, so a floor
   * of its own (HOME_SCENARIOS says why).
   * Actual on 2026-10-07: 5 in one feature file, measured through `featureFloors.spec.js`'s discovery. Floor 2, with
   * headroom. Never edited up to match a new actual.
   */
  SIGNIN_SCENARIOS: 2,

  /**
   * Scenarios in `features/batch-print/` -- the organisers' batch print (client and server `oct/reports-visits`). A new
   * feature directory, so a floor of its own (HOME_SCENARIOS says why).
   * ⚠️ NOT `features/reports/`, the stream's own name: `.gitignore` ignores every directory called `reports` (test
   * output), and `sourceFiles.js` skips every one -- a feature there would be neither committed nor found.
   * Actual on 2026-10-07: 2 in one feature file, measured through `featureFloors.spec.js`'s discovery. Floor 1: with
   * two, zero is the only count that says the discovery broke. Never edited up to match a new actual.
   */
  BATCH_PRINT_SCENARIOS: 1,

  /**
   * Harness support modules the self-tests walk. Actual on 2026-09-07: 7
   * (assertNonVacuousScan, env, fixtures, guards, sourceFiles, stack, world).
   * The floor sits below it with headroom and is never edited up to match a new
   * actual.
   *
   * 🔑 ITS ONE CALLER IS `canary.spec.js`, and that is now load-bearing. This
   * floor was previously ALSO passed to `invariants.spec.js`'s whole-repository
   * scan, a population an order of magnitude larger -- so four suite-wide invariants ran
   * behind a canary with 93% headroom and would have passed over almost
   * nothing. A floor is calibrated for one population; reusing it for another
   * silently disarms the guard it is lent to. Use `SUITE_SOURCE_FILES` below.
   */
  SUPPORT_MODULES: 3,

  /**
   * Every file the suite-wide invariants scan, discovered through
   * `support/sourceFiles.js`'s own `sourceFiles()`.
   * Actual on 2026-09-08: 47, re-derived after this pass added three files.
   *
   * Floor 30: real headroom below the actual, and far enough above zero that a
   * broken walk cannot pass. The invariants standing on it -- no sibling-repo
   * path, no fixed wait, no planning path -- are all of the "assert an absence"
   * shape, which is exactly the shape that passes when it finds nothing.
   */
  SUITE_SOURCE_FILES: 30,

  /**
   * The JavaScript files the disabled-population and focused-test scans walk,
   * discovered through `support/sourceFiles.js`'s own `scriptFiles()`.
   * Actual on 2026-09-08: 34, re-derived after this pass added three files.
   *
   * Floor 20. This one guards the most fail-open assertions in the repository:
   * `EXPECTED_DISABLED` is empty by design, so `toHaveLength(0)` and
   * `toEqual([])` both pass on a walk that returned nothing. Before this floor
   * existed those three assertions had no canary at all -- in the file whose
   * entire subject is that failure mode.
   */
  SUITE_SCRIPT_FILES: 20,

  /**
   * Host ports in `docker-compose.yml` written blankable, as `${VAR-x}` rather
   * than `${VAR:-x}`. Actual on 2026-09-07: 3 (database, API, client).
   *
   * Floor 3, pinned exactly -- the closed-set exception in the rules above. A
   * fourth published port would be a deliberate act. The number was previously
   * written bare at the call site.
   */
  BLANKABLE_HOST_PORTS: 3,

  /**
   * Scenarios tagged `@known-broken`, discovered by the disabled-population
   * guard. Actual on 2026-09-06: 17 -- 16 skipped, plus A29's, which RUNS
   * because it asserts the crash and must redden when the crash is fixed.
   *
   * 🔑 A FLOOR ON THE NUMBER OF DEFECT ASSERTIONS, which reads backwards until
   * you see what it catches. The guard's real work is checking that every such
   * scenario names a register row -- and a guard that finds no scenarios finds
   * no violations either. A renamed directory or a changed tag would leave it
   * passing over nothing, which is the same fail-open shape as every other
   * discovery here.
   *
   * 🔴 AND THAT IS WHY IT IS 1 AND NOT 8. Every OTHER floor in this file sits
   * under a population that GROWS with the work -- features, support modules,
   * source files, compose images -- so "fewer than expected" really does mean
   * the walker broke. This one sits under the number of scenarios pinned to
   * defects nobody has fixed yet, and the whole project is trying to reduce it.
   * A floor of 8 therefore asserts that at least eight defects remain, which
   * becomes false exactly when the work succeeds:
   *
   *   actual 2026-09-06   17        floor 8, written the same week
   *   actual 2026-09-10   19        two arcs about to flip several at once
   *
   * Measured 2026-09-10 across all three repositories, prompted by the capture-
   * member pass finding the same shape in a canary of its own (its mutation
   * targets were deleted by the fixes it existed to guard, so it skipped and
   * reported success). Two floors in this project had it; the other,
   * `FLOORS.DEFECT_ROWS` in the client, was already going to redden on the
   * merge -- the two branches in flight close nine of sixteen register rows,
   * leaving seven against a floor of eight, with a message about a broken scan
   * that would have been false.
   *
   * 🔑 A FLOOR OVER A POPULATION THE WORK IS TRYING TO REDUCE ASSERTS THAT THE
   * WORK FAILED. It must guard that the scan can still READ its source, never
   * that the source is still long -- and 1 is the whole of that job here,
   * because zero is the only count that means the discovery broke. The dated
   * actuals above stay as evidence; they are not a threshold.
   */
  KNOWN_BROKEN_SCENARIOS: 1,

  /**
   * Files under `tools/` -- the three-repository orchestrator.
   * Actual on 2026-09-08: 2 (`test-all.mjs`, `merge-reports.mjs`).
   *
   * Floor 2, pinned exactly -- the closed-set exception in the rules above.
   *
   * 🔑 What it guards is the test it is passed to. That test asserts nothing
   * outside `tools/` imports from `tools/` -- a scan that found no orchestrator
   * would find no importers either, and report the suite's independence from a
   * thing that was not there.
   */
  ORCHESTRATOR_FILES: 2,

  /**
   * `image:` references in `docker-compose.yml`.
   * Actual on 2026-09-08: 4 (postgres, server, client, suite).
   *
   * Floor 3, NOT pinned -- and the difference from the two entries above is
   * the point. Those are closed sets; this is not. A compose file legitimately
   * grows a service (a socket proxy is already proposed for CI), so the floor
   * sits below the actual with headroom. What it guards is a changed
   * indentation or key spelling leaving the shape check comparing an empty list
   * against an empty list and reporting success.
   */
  COMPOSE_IMAGES: 3,
}
