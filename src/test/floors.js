/**
 * The floors, in one place, each measured through its own guard's discovery.
 *
 * WHY THIS IS ITS OWN MODULE, and not part of `assertNonVacuousScan.js`
 * That file imports `expect` from `vitest`, so anything importing it must run
 * inside the test runner. Three of this repository's guards do NOT --
 * `scripts/suite-floor.mjs`, `scripts/contract-witness.mjs` and
 * `scripts/bundle-budget.mjs` run as plain Node after the suite. So each of
 * them hand-rolled its threshold or omitted one, and the registry's claim to be
 * "the floors, in one place" was already false: the live suite floor was a bare
 * `const FLOOR = 260` inside `suite-floor.mjs`, and `FLOORS.TEST_FILES` sat
 * here with zero callers, documenting a caller (`src/test/suiteFloor.test.js`)
 * that does not exist.
 *
 * Splitting the data from the vitest binding lets both halves import it, so the
 * claim becomes true. `assertNonVacuousScan.js` re-exports `FLOORS`, so every
 * existing import keeps working.
 *
 * THE RULES THESE NUMBERS OBEY
 *   - A floor is a NON-VACUITY CANARY, not a coverage target. It asserts the
 *     scan scanned something, and is never raised to match a new actual: a
 *     floor equal to the count is fragile without being stricter.
 *   - Each is measured THROUGH the guard's own discovery, never by a different
 *     tool.
 *   - Every number here EXPIRES. The date it was measured is part of the
 *     record, and re-measuring is the only way to quote it.
 *   - A floor calibrated for one population is never lent to another.
 */
export const FLOORS = {
  /** Route smoke. Actual on 2026-09-04: 27 path-bearing routes in App.jsx. */
  ROUTES: 20,

  /** Service modules under src/services/. Actual on 2026-09-04: 9. */
  SERVICE_MODULES: 7,

  /**
   * Exported service methods across all nine modules, as the error-contract
   * guard enumerates them. Actual on 2026-09-04: 25 fetching methods.
   */
  SERVICE_METHODS: 18,

  /**
   * Distinct endpoints the API-contract guard finds in `src/services/`.
   *
   * MEASURED 2026-09-05 through `discoverEndpoints()` itself: 31 `apiUrl(...)`
   * call sites collapsing to **20** distinct normalised paths, across 9 service
   * modules.
   *
   * NOTE THE PLAN SAID 19. It was written on 2026-09-03 and the actual is 20 --
   * a count expires, which is the doctrine this arc keeps restating about its
   * own numbers. The floor is set from what the guard produces today, not from
   * what a document remembered.
   *
   * This is the single highest risk in P4: a regex that matches nothing gives a
   * guard that passes while checking ZERO endpoints, which is worse than no
   * guard because it reads as green.
   */
  ENDPOINTS: 15,

  /**
   * Lines in the committed `api-surface.txt` copy. Actual on 2026-09-07: 41.
   *
   * Its own entry rather than `ENDPOINTS`. The two populations are different --
   * one endpoint can appear as several `METHOD /path` lines -- and the artifact
   * guard was borrowing the endpoint floor, which is a floor calibrated for a
   * different discovery.
   */
  SURFACE_LINES: 25,

  /**
   * Files git tracks in this repository. Actual on 2026-09-05: 122.
   *
   * What it catches is `git ls-files` returning nothing -- in a detached
   * checkout, or with git absent from the PATH. The secret guard would then
   * report no secrets and pass.
   */
  TRACKED_FILES: 60,

  /**
   * Tracked files under src/ plus index.html -- everything Tailwind's source
   * detection must be able to reach. Actual on 2026-09-09: 114.
   *
   * What it catches is `git ls-files src` returning nothing. The shadowing
   * guard would then find zero shadowed files out of zero files and report the
   * repository clean, which is the precise shape of the defect it exists to
   * catch: a check that cannot tell its subject from nothing.
   */
  TRACKED_SOURCES: 60,

  /**
   * The ENTRY javascript chunk -- what a first paint actually downloads before
   * anything can render. Actual on 2026-09-09, after 03.1 split every route:
   * 286.58 KiB, down from 506.41 KiB when every page was a static import.
   *
   * 🔑 IT IS A SEPARATE ROW BECAUSE TOTAL JS CANNOT SEE THE WIN. Splitting moved
   * bytes out of the entry and into 47 lazy chunks, so the TOTAL went UP (506 ->
   * 520 KiB, the cost of module boundaries) while the number a user waits for
   * nearly halved. A budget watching only the total would have called that a
   * regression, and a budget watching only the total is also blind to the reverse:
   * someone converting a lazy route back to a static import moves 40 KiB into the
   * entry without changing the total at all.
   */
  BUNDLE_ENTRY_JS_BYTES: 120 * 1024,

  /**
   * Files the secret guard still scans AFTER its path exclusions. Actual on
   * 2026-09-05: 117 of 122.
   *
   * The mirror image of the exclusion list, and the reason it is a separate
   * floor: an over-broad exclusion silences that guard exactly as effectively
   * as a raised threshold would, and the finding count alone would never show
   * it.
   */
  SCANNED_AFTER_EXCLUSIONS: 60,

  /**
   * Option-backed fields the vocabulary guard derives from `memberConstants`.
   * Actual on 2026-09-05: 18 (16 by naming convention, 2 by alias).
   */
  OPTION_BACKED_FIELDS: 8,

  /**
   * Comparisons against those fields found in `src/pages` and `src/hooks`.
   * Actual on 2026-09-05: 47.
   */
  VOCABULARY_COMPARISONS: 20,

  /**
   * Keys the server publishes in its error envelope. Actual on 2026-09-05: 6
   * (`timestamp`, `status`, `error`, `message`, `path`, `validationErrors`).
   */
  ENVELOPE_KEYS: 3,

  /**
   * Test source files the defect-population guard walks -- every `.test.js` and `.test.jsx` under `src/`. Actual on
   * 2026-09-07: 18. Previously written as a bare `8` at its call site.
   *
   * 🔒 RAISED AT `10.4`, 2026-09-18: **8 -> 75**, against a measured **121** through the same glob.
   * At 8 the floor had **113 files of headroom** -- a walk that silently lost `src/pages/` and
   * `src/components/` together would still have cleared it, and that is the partial-scan hole this
   * registry exists to close ("headroom is silence, in the amount of the headroom"). 75 is about
   * 62% of the actual, the same proportion as `SRC_JSX_FILES` and `PAGE_FILES`: far enough under
   * that deleting a batch of obsolete tests does not redden it, close enough that losing a
   * directory does. `testingAndErrorHandling.md` §5 scheduled this raise for `10.4`.
   */
  TEST_SOURCE_FILES: 75,

  /**
   * Register rows declared in `knownDefects.js`. Actual on 2026-09-10: **15**
   * (16 on 2026-09-07, less A25, closed by `06.1b`).
   *
   * 🔴 LOWERED TO 1 ON PURPOSE, AND IT IS THE ONLY FLOOR IN THIS FILE THAT SHOULD BE.
   *
   * Every other entry here guards a population that grows with the codebase: fewer
   * routes than expected means the route walker broke, not that routes were deleted.
   * This one guards a population that SHRINKS WHEN THE WORK SUCCEEDS. A floor of 8
   * under a DEFECT register asserts, in effect, that we still have at least eight
   * open defects -- so it goes red on the day the ninth is fixed, with the message
   * "the scan found too few rows to be trusted", which would be false.
   *
   * 💥 IT IS NOT HYPOTHETICAL AND IT IS ALREADY SCHEDULED. Measured by the automation
   * arc, 2026-09-10: this branch closes A11, A14, A25, A27 and A28; the capture-member
   * branch closes A6, A7, A37 and A38. The merged intersection falls BELOW eight, so the
   * merge commit would have met a red guard in the middle of a 29-file reconciliation and
   * had to work out that the guard, not the merge, was wrong.
   *
   * 🔑 SO THE PREDICATE IS RE-AIMED AT THE FACT IT ACTUALLY CARES ABOUT: that the scan
   * could READ ITS SOURCE. One row proves the module resolved, parsed and exported a
   * populated register; the day that legitimately reaches zero, the correspondence tests
   * either side of it are what carry the weight, and the right move is to delete this
   * guard rather than to keep a row alive to satisfy it.
   *
   * ⚠️ A floor should guard that the scan can read its source, not that the source is
   * still long. The automation arc's phrasing, adopted here verbatim because it is the
   * general rule and not a note about this entry.
   */
  DEFECT_ROWS: 1,

  /**
   * Tests the suite must produce, counted from `reports/junit.xml` by
   * `scripts/suite-floor.mjs`. Last RECORDED actual 398, on 2026-09-06 -- not
   * re-measured when this entry was moved here, because measuring it means
   * running the suite, and a number carried across is exactly what this
   * registry tells its readers not to trust. Re-derive it before quoting it.
   *
   * 🔑 THE ONE THAT LIVED OUTSIDE THE REGISTRY, as a bare `const FLOOR = 260`
   * inside the script -- so the repository's "floors in one place" claim was
   * false for its single most load-bearing floor. Moved here unchanged; it is
   * not re-tuned by this pass.
   */
  //
  // 🔒 RAISED AT `10.4`, 2026-09-18: **260 -> 700**, against a MEASURED **1,103 tests in 123 files**
  // (`npm test`, this worktree, `4ea88a4`'s gate). At 260 the floor sat 843 tests below the suite
  // -- three quarters of it could have stopped being discovered without a sound. 700 is ~63% of
  // the actual, the proportion the other population floors here use. It is still a canary, not a
  // target: it will not be raised to follow the suite, only re-measured when someone quotes it.
  SUITE_TESTS: 700,

  /**
   * Endpoints the route smoke ACTUALLY REQUESTED, counted by
   * `scripts/contract-witness.mjs` from `reports/observed-endpoints.json`.
   * Measured through that guard's own read on 2026-09-08: 9.
   *
   * 🔑 ITS OWN ENTRY RATHER THAN `ENDPOINTS`, which counts a population that
   * looks similar and is not. `ENDPOINTS` counts what the STATIC scan
   * finds in `src/services/`; this counts what a browser was observed asking
   * for. Most discovered endpoints need a user action the smoke never performs,
   * so this number is legitimately much smaller, and lending the larger floor
   * to it would disarm the guard it was lent to.
   *
   * What it is for: `contract-witness.mjs` compares the two sets by DIFFERENCE,
   * and a difference against an empty set is empty -- so with no witness the
   * script reported success. This is the floor that makes that state loud.
   */
  OBSERVED_ENDPOINTS: 5,

  /**
   * The shipped bundle, in bytes, floored by `scripts/bundle-budget.mjs`.
   * Measured through that guard's own walk of `dist/` on 2026-09-08:
   * 502.08 KiB JavaScript, 48.97 KiB CSS, 788.16 KiB across 5 emitted files.
   *
   * 🔑 FLOORS ON A GUARD THAT WAS ALL CEILING. Every other assertion there is
   * `actual <= ceiling`, which a build emitting nothing but `index.html`
   * satisfies completely -- three cheerful OK lines and exit 0. A broken build
   * is far likelier than a bundle that doubled, and it was the one case the
   * guard could not see.
   *
   * These are not size targets. They separate "a real build" from "no build".
   * If a legitimate change ever takes the bundle below one, that is worth a
   * conversation rather than an edit.
   */
  BUNDLE_JS_BYTES: 200 * 1024,
  BUNDLE_CSS_BYTES: 10 * 1024,
  BUNDLE_TOTAL_BYTES: 300 * 1024,
  BUNDLE_FILES: 4,

  /**
   * Non-test source files under `src/` matching `.js` / `.jsx` / `.mjs`.
   * Measured through that guard's own `readSources` walk on 2026-09-09: 81.
   * Re-measured 2026-09-10 through `nonTestSources()`: 84.
   *
   * What it catches is the walk returning nothing -- a wrong root, a pattern
   * that stopped matching `.jsx`. The scan would then find no palette literals
   * in no files, and P4's "no literal colour utilities, grep-proved" box would
   * be ticked by a guard that looked at nothing.
   *
   * ⚠️ THIS ROW IS FOR THE `jsx?|mjs` POPULATION ONLY. It was lent to three other walks and
   * that broke this registry's own rule -- see `SRC_JSX_FILES` and `SRC_STYLEABLE_FILES`.
   */
  SRC_SOURCE_FILES: 50,

  /**
   * Non-test `.jsx` files under `src/` -- the population a JSX-only walk sees.
   * Measured 2026-09-10 through `tableScroller.structural.test.js`'s own walk: 61.
   *
   * 🔴 ITS OWN ROW BECAUSE LENDING `SRC_SOURCE_FILES` TO IT BROKE THIS FILE'S STATED RULE:
   * *"A floor calibrated for one population is never lent to another."* That floor is 50,
   * measured against 84 files; this walk sees 61. Eleven files of headroom on a population the
   * number was never measured against -- so a walk that silently lost the whole of `src/pages/`
   * (31 files, per `PAGE_FILES`) would still clear it, which is precisely the partial-scan hole
   * `assertNothingWasSkipped` exists to close. Headroom is silence, in the amount of the headroom.
   */
  SRC_JSX_FILES: 40,

  /**
   * Non-test source files under `src/` that can carry a STYLE -- `.js` / `.jsx` / `.mjs` plus
   * `.css`. Measured 2026-09-10 through `viewportUnits`' and `hoverIntent`'s own walk: 86.
   *
   * Its own row for the same reason as above, and the difference is small but real: a viewport-
   * unit or hover-media scan that stopped matching `.css` would lose the two stylesheets where
   * those rules most plausibly live while still clearing a floor set for the JS population.
   */
  SRC_STYLEABLE_FILES: 55,

  /**
   * `--color-*` declarations in `index.css`. Measured through that guard's own
   * regex on 2026-09-09: 87 -- the 34 semantic roles declared twice, once per
   * theme, plus shadcn's 19 compatibility aliases.
   *
   * 🔑 IT FLOORS A COLLISION CHECK, WHICH IS WHY IT EXISTS AT ALL. The check
   * asks "is any token named after a Tailwind palette family". Against an empty
   * token list the answer is a truthful, useless "no", and the guard reports
   * that the detector is safe from a collision it never looked for.
   */
  COLOUR_TOKENS: 40,

  /**
   * Page components under `src/pages/`, as the list-state guard walks them.
   * Measured through that guard's own `readSources` walk on 2026-09-10: 31.
   *
   * What it catches is the walk returning nothing -- a wrong root, or a pattern that
   * stopped matching `.jsx`. The guard would then find no page that fetches, no page
   * that fails to handle a failure, and report the rule holding over an empty set.
   */
  PAGE_FILES: 20,

  /**
   * Routes the heading-contract guard mounts, from `App.jsx`'s own route table.
   * Measured through that guard's own render on 2026-09-09: 27 path-bearing
   * routes, the same population the route smoke drives.
   *
   * Its own entry rather than `ROUTES`, which floors the smoke's discovery of
   * the route TABLE. This floors how many were actually MOUNTED AND MEASURED --
   * a render that throws for every route leaves an empty result set, and an
   * assertion that no route has two `<h1>`s is trivially true of no routes.
   */
  HEADINGS_MEASURED: 20,

  /**
   * State-gated sites (`<Modal`, `<ConfirmDialog`, `<BankBranchModal`,
   * `<DataState`, `<DateField`, `aria-expanded`) that `coverageComplaints` in
   * `tools/surfaceCatalogue.mjs` counts under `src/`, through that guard's own
   * scan. Lowest honest reading at `8d01904` (2026-09-15): 55 sites; far under
   * it, the scan is not seeing the source.
   */
  STATE_GATED_SITES: 40,
}
