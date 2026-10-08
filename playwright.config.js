import { defineConfig, devices } from '@playwright/test'
import { defineBddConfig } from 'playwright-bdd'
import { CLIENT_URL } from './support/env.js'

/**
 * One configuration. Every way of running this suite reads it.
 *
 * There is deliberately no second config for "demo mode" or "debug mode". Four
 * ways of watching a run exist -- `--headed`, `--ui`, `--debug` and
 * `show-trace` -- and all four read the same feature files through this file. A
 * separate demo suite drifts from the real one within a month and then
 * demonstrates something that is no longer true.
 */

/**
 * The journeys that share the stack, and can therefore all run at once.
 *
 * `features/serial/`, `features/acb/` and `features/monthly/` are excluded rather than forgotten -- see below.
 */
const featuresDir = defineBddConfig({
  features: ['features/**/*.feature', '!features/serial/**', '!features/acb/**', '!features/monthly/**'],
  steps: ['steps/**/*.js', 'support/fixtures.js'],
  outputDir: '.features-gen',
})

/**
 * The journeys that take the stack DOWN, compiled separately so they can be
 * given a project of their own.
 *
 * 🔴 WHY A SECOND PROJECT AND NOT A TAG. Playwright's serial mode orders tests
 * within one file; it does not stop OTHER files running beside them. A scenario
 * that stops the API container while eleven others are mid-request does not
 * fail -- it makes eleven unrelated scenarios fail, in eleven different ways,
 * none of which mentions a container. That is indistinguishable from flake, and
 * it is exactly the kind of thing this suite exists to not do.
 *
 * A project whose `dependencies` name every other project is the only construct
 * that gives real exclusivity: Playwright will not start it until they have
 * finished, so the stack is nobody else's while it is being taken apart.
 */
const serialFeaturesDir = defineBddConfig({
  features: 'features/serial/**/*.feature',
  steps: ['steps/**/*.js', 'support/fixtures.js'],
  outputDir: '.features-gen-serial',
})

/**
 * The A.C.B. runs journeys (ACB `06.1`), compiled separately for a project of their own.
 *
 * 🔴 EVERY ONE PREPARES A REAL RUN FOR A REAL MONTH. A member captured by a parallel journey mid-preparation
 * changes what the run contains, and two of these preparing the same month collide on the run's uniqueness.
 * So they run one at a time (`workers: 1`) and only once the parallel journeys have finished
 * (`dependencies`) -- and before the journeys that take the stack down, which wait for them in turn.
 * `@mode:serial` orders scenarios within one file and is no substitute (ACB `features/README.md` §3).
 */
const acbFeaturesDir = defineBddConfig({
  features: 'features/acb/**/*.feature',
  steps: ['steps/**/*.js', 'support/fixtures.js'],
  outputDir: '.features-gen-acb',
})

/**
 * Month end's journeys, compiled separately for a project of their own.
 *
 * 🔴 THEY COUNT WHAT OTHER JOURNEYS CHANGE. Their scenarios read a figure, do one thing and read it
 * again -- and a member captured by a parallel journey, or suspended by an A.C.B. run, between the two
 * readings would move the figure for a reason the scenario did not cause. So they run one at a time
 * (`workers: 1`), only once the parallel journeys and A.C.B.'s have finished (`dependencies`), and
 * before the journeys that take the stack down, which wait for them in turn.
 */
const monthlyFeaturesDir = defineBddConfig({
  features: 'features/monthly/**/*.feature',
  steps: ['steps/**/*.js', 'support/fixtures.js'],
  outputDir: '.features-gen-monthly',
})

/**
 * The `--repeat-each` this command line asked for, or `undefined`.
 *
 * 🔴 PLAYWRIGHT APPLIES IT TO TOP-LEVEL PROJECTS ONLY, and two of this suite's
 * three are not top-level: `journeys-serial` lists `harness` and `journeys` as
 * `dependencies` (see below). 1.63 sets the command line's count on top-level
 * projects alone and builds a dependency with its own configured `repeatEach`
 * (`lib/runner/index.js`). So `npm run test:gate` -- "every test three times"
 * -- repeated the 6 serial scenarios and ran the other 110 tests once: 128
 * tests, not 348, measured on the deploy arc's runner (R6-2, run 35648999818).
 * Register E15; the same rule as E11's sharding.
 *
 * The count is therefore handed to the two dependency projects as their own
 * `repeatEach`, which Playwright honours. It is read from `process.argv`
 * because the command line is the only place it exists; only the runner needs
 * it -- a worker is told which repeat it is running.
 */
function repeatEachFromCommandLine(argv = process.argv) {
  for (let i = 0; i < argv.length; i += 1) {
    const flag = /^--repeat-each(?:=(\d+))?$/.exec(argv[i])
    if (flag) return Number(flag[1] ?? argv[i + 1])
  }
  return undefined
}
const REPEAT_EACH = repeatEachFromCommandLine()

export default defineConfig({
  /**
   * ZERO RETRIES, EVERYWHERE, INCLUDING CI.
   *
   * A retry that turns a red run green is a mechanism for not knowing. A flaky
   * test is fixed in the session that finds it -- there is no quarantine list
   * and nothing is skipped for unreliability.
   */
  retries: 0,

  /**
   * `retain-on-failure`, and NOT Playwright's usual `on-first-retry`.
   *
   * This pairing matters and is easy to get wrong: `on-first-retry` is the
   * common default, but with `retries: 0` there is never a first retry, so it
   * captures NOTHING -- silently. The two defaults are only coherent with each
   * other, and the anti-flake rule above is what makes the usual one wrong
   * here. The same reasoning applies to video and screenshots.
   *
   * ── The recording cost, MEASURED and INCONCLUSIVE -- L2 ──────────────────────
   * `retain-on-failure` does not mean "record on failure". It means RECORD
   * EVERYTHING, THEN DELETE THE PASSES. Almost every test in this suite passes,
   * so the recording cost is paid on nearly all of them to retain it on nearly
   * none -- and with `retries: 0` there is no retry to defer it to, so the trade
   * is real rather than an oversight. (No test count here: the one first written
   * was stale in the commit that added it, because the same commit added two
   * scenarios. The merged report carries the current figure.)
   *
   * Two containerised runs, 2026-09-09, same host, same command, summing every
   * test's own reported duration:
   *
   *     video: 'retain-on-failure'    88 timed tests    216.8s
   *     video: 'off'                  88 timed tests    287.7s
   *
   * 🔴 VIDEO-OFF CAME OUT SLOWER, BY A THIRD, WHICH IS NOT A RESULT -- IT IS THE
   * NOISE FLOOR. Disabling a recorder cannot make a suite slower, so the honest
   * reading is that this box's variance is LARGER than the effect being measured.
   * It has been between 1.0 and 2.7 GB free throughout, and the two runs differed
   * in what else the daemon was doing.
   *
   * ⚠️ SO THE DECISION IS TO KEEP IT, AND THE REASON IS THE MEASUREMENT RATHER
   * THAN A PREFERENCE. A cost that cannot be distinguished from scheduling noise
   * is not a cost worth trading a failure artefact for -- and with no retries,
   * the video is the only moving picture of a red run anybody gets. Re-measure on
   * a quiet host if the suite grows enough for this to matter; do not conclude
   * anything from the numbers above beyond "smaller than we can see from here".
   */
  use: {
    baseURL: CLIENT_URL,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'retain-on-failure',

    /**
     * Explicit at every level, calibrated ABOVE the slowest legitimate scenario
     * under parallel load -- not around what a single scenario takes on an idle
     * machine.
     *
     * 🔴 THESE NUMBERS WERE RAISED BECAUSE THE CONSTRAINED GATE CAUGHT THEM,
     * and the first constrained run had passed. At 30s, `page.goto` timed out
     * on ALL SIX journeys under `--cpus=1` with six workers -- six Chromium
     * instances sharing one core, with the stack healthy the whole time. The
     * run before it was green, which makes it worse: a ceiling that survives
     * on luck is a flake waiting for a loaded CI runner.
     *
     * This is exactly what the constrained gate is for, and the honest fix is
     * the ceiling, not the parallelism -- a real runner IS loaded, and a
     * navigation that takes 40s there is slow, not broken. The per-test ceiling
     * moves with it so a slow navigation cannot exhaust the whole test's budget
     * and report as a hang.
     *
     * ⚠️ AND THE COST OF THAT GENEROSITY, STATED, because it is a real trade and
     * not a free win -- L10. Ceilings this high mean a HOST-STARVATION event
     * presents as a slow pass rather than as a fast failure, and the two
     * incidents this project has recorded (F9 and F11) are both that event. So
     * the numbers below deliberately buy anti-flake at the price of making
     * starvation quieter.
     *
     * 🔑 That trade is only acceptable because the loudness lives elsewhere: the
     * orchestrator prints free memory at every stage boundary, and a client
     * suite that runs short is caught from OUTSIDE by a test-count floor rather
     * than by a timeout. Tightening these ceilings to make starvation fail fast
     * would reintroduce the flake they were raised to remove, and would be the
     * wrong lever -- the right one is measuring the host, which is done.
     */
    actionTimeout: 30_000,
    navigationTimeout: 90_000,
  },

  /** Per-test ceiling. Above it, a scenario is hung rather than slow. */
  timeout: 180_000,

  /** Per-assertion ceiling, for the auto-retrying `expect` matchers. */
  expect: { timeout: 10_000 },

  /**
   * Chromium only, for now. Three browsers is three times the runtime for no
   * defect anyone has seen; cross-browser coverage is a deliberate later
   * decision rather than a default nobody chose.
   */
  projects: [
    {
      /**
       * The harness's own tests. They prove the suite's machinery -- target
       * resolution, the destructive guards, the non-vacuity canary -- and need
       * neither a browser nor a running application, so they stay fast and run
       * everywhere.
       */
      name: 'harness',
      testDir: 'tests/harness',
      // A dependency of journeys-serial, so `--repeat-each` would pass it by (E15).
      repeatEach: REPEAT_EACH,
    },
    {
      name: 'journeys',
      testDir: featuresDir,
      use: { ...devices['Desktop Chrome'] },
      // A dependency of journeys-serial, so `--repeat-each` would pass it by (E15).
      repeatEach: REPEAT_EACH,
    },
    {
      name: 'journeys-acb',
      testDir: acbFeaturesDir,
      dependencies: ['harness', 'journeys'],
      workers: 1,
      fullyParallel: false,
      use: { ...devices['Desktop Chrome'] },
      // A dependency of journeys-serial, so `--repeat-each` would pass it by (E15).
      repeatEach: REPEAT_EACH,
    },
    {
      // Month end's: one at a time, after A.C.B.'s, which suspend the members these count (see above).
      name: 'journeys-monthly',
      testDir: monthlyFeaturesDir,
      dependencies: ['harness', 'journeys', 'journeys-acb'],
      workers: 1,
      fullyParallel: false,
      use: { ...devices['Desktop Chrome'] },
      // A dependency of journeys-serial, so `--repeat-each` would pass it by (E15).
      repeatEach: REPEAT_EACH,
    },
    {
      /**
       * The scenarios that stop the API container and start it again.
       *
       * `dependencies` is what makes them exclusive -- they begin only once
       * both other projects have finished -- and `fullyParallel: false` keeps
       * them in order among themselves, which matters because each one leaves
       * the stack in a state the next one inherits.
       *
       * ⚠️ Playwright SKIPS a project whose dependency failed. That is the
       * right behaviour here and worth knowing rather than discovering: if the
       * journeys are already red, taking the stack down proves nothing, and the
       * report says "skipped" rather than adding a second failure with a
       * different cause.
       */
      name: 'journeys-serial',
      testDir: serialFeaturesDir,
      // `journeys-acb` too: its runs are prepared on a stack nobody is taking down (ACB 06.1). And
      // `journeys-monthly`: its figures are read on a stack nobody is taking down.
      dependencies: ['harness', 'journeys', 'journeys-acb', 'journeys-monthly'],
      fullyParallel: false,
      /**
       * 🔴 A LONGER PER-TEST CEILING, and the reason is the application rather
       * than the suite. With the API stopped, the client waits on the proxy's
       * 60-second read timeout before it learns anything -- it sets no deadline
       * of its own (A14) -- and bringing the API back and waiting for it to
       * report healthy costs another ~40 seconds. The default 180s ceiling
       * leaves no room for both, and a scenario that times out mid-restart
       * leaves the stack down for whatever runs next.
       */
      timeout: 300_000,
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /**
   * Reporters, all from one run. The human report, the merge input for the
   * cross-repo summary, and the CI input are three different audiences reading
   * the same execution -- generating them separately would mean three runs that
   * can disagree.
   */
  reporter: [
    ['list'],
    ['playwright-bdd/reporter/cucumber', { $type: 'html', outputFile: 'reports/cucumber/report.html' }],
    ['playwright-bdd/reporter/cucumber', { $type: 'json', outputFile: 'reports/cucumber/report.json' }],
    /**
     * JUnit XML beside the human report. Every CI test-reporting integration
     * reads it; without it a red build says "job failed" and the reader has to
     * open raw logs. Playwright's own reporter is used rather than the Cucumber
     * one so the harness project appears in it too.
     */
    ['junit', { outputFile: 'reports/junit.xml' }],
  ],

  /**
   * `--shard=i/n` was meant to work from the first commit, so a suite that
   * outgrows one runner could be split later without restructuring anything.
   *
   * ❌ IT DOES NOT SPLIT THIS SUITE TODAY -- register row E11. `journeys-serial`
   * lists `harness` and `journeys` as `dependencies`, and Playwright shards only
   * top-level projects: a dependency project is added back whole. Measured
   * 2026-09-21: `--list --shard=1/2` names all 116 tests, `--shard=2/2` none.
   * The split (shard harness + journeys, run journeys-serial `--no-deps` on its
   * own runner) waits for a measured need rather than being built on spec.
   */
  fullyParallel: true,

  /**
   * 🔴 Worker count must follow the CPU actually available, not `nproc`.
   *
   * A container CPU limit is a CFS *quota*: it caps throughput but does NOT
   * change the visible core count. Inside `--cpus=1` this machine still reports
   * 12 cores, so Playwright's default (half of them) starts SIX Chromium
   * instances on one core's worth of CPU. That is not a configuration any real
   * runner has -- a genuine 1-CPU runner reports 1 core and gets 1 worker.
   *
   * The symptom was a constrained gate that passed, then failed all six
   * journeys on navigation timeouts, then passed again. Raising the timeouts
   * did not fix it, because the ceiling was never the cause: the contention
   * was. Deriving workers from the same variable that sets the quota makes the
   * constrained run mean what it is supposed to mean -- a slower machine, not
   * an oversubscribed one.
   *
   * Unset, this is `undefined` and Playwright's own default applies.
   */
  workers: Number(process.env.SASBO_SUITE_CPUS) > 0
    ? Number(process.env.SASBO_SUITE_CPUS)
    : undefined,

  /**
   * Refuse to pass a run that contains a `test.only` left behind. On a local
   * run that is a warning; unset, in a pipeline, it is how one focused test
   * silently becomes the entire suite.
   */
  forbidOnly: !!process.env.CI,

  outputDir: 'test-results',
})
