#!/usr/bin/env node
/**
 * THE DISCOVERY FLOOR -- a count AND a zero exit code.
 *
 * WHY IT IS A SCRIPT AND NOT A TEST
 * A test inside the suite cannot assert the suite's own exit code, and the
 * exit code is half the claim. MobiZone observed a client suite reporting
 * 941/941 PASSING while the process exited 1 -- every human signal said green
 * and the build was red. Nothing running inside that suite could have noticed.
 *
 * It also cannot honestly assert its own total: a broken test-file glob makes
 * the runner discover fewer files, and a guard that lives in one of those
 * files disappears along with them. The failure mode of a discovery guard is
 * that it is not there, and only something outside can see that.
 *
 * So this runs the suite, then reads BOTH the exit code and the JUnit XML the
 * run produced, and asserts against both.
 *
 * THE FLOOR IS A NON-VACUITY CANARY, NOT A COVERAGE TARGET
 * It sits deliberately BELOW the real count with headroom, and it is never
 * raised to match reality. A floor equal to the actual is fragile without
 * being stricter -- it reddens for reasons nobody caused, and then gets
 * lowered by whoever is unlucky enough to hit it.
 *
 * The floor and the measurement it was calibrated against both live in
 * `FLOORS.SUITE_TESTS`. They are deliberately not repeated here -- the comment
 * beside the old inline constant had already drifted away from its own value,
 * which is why the constant moved.
 */
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import process from 'node:process'

import { FLOORS } from '../src/test/floors.js'
import { junitCounts } from './lib/junitCounts.mjs'

const FLOOR = FLOORS.SUITE_TESTS
const REPORT = 'reports/junit.xml'

const run = spawnSync('npx', ['vitest', 'run'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
})

const exitCode = run.status

let xml
try {
  xml = readFileSync(REPORT, 'utf8')
} catch {
  console.error(
    `\n  FLOOR FAILED: the suite produced no ${REPORT} at all.\n` +
      `  A run that reports nothing is not a run that passed -- it is a run that did\n` +
      `  not happen, or a reporter that is no longer wired up.\n`,
  )
  process.exit(1)
}

// The counting lives in `scripts/lib/junitCounts.mjs` so that it can be put
// under test -- see `scripts/junitCounts.test.js`. It could not be while it
// lived here: this file's first act is to spawn the entire suite, so a test
// inside the suite exercising it would run the suite inside itself.
//
const { suites, total, failures, skipped } = junitCounts(xml)

console.log(
  `\n  suite floor -- ${total} tests in ${suites} file(s), ` +
    `${failures} failure(s), ${skipped} skipped, exit ${exitCode}\n`,
)

let failed = false

if (exitCode !== 0) {
  console.error(
    `  FLOOR FAILED: the suite exited ${exitCode}.\n` +
      `  This is asserted separately from the pass counts on purpose: a suite can report\n` +
      `  every test passing and still exit non-zero -- an unhandled rejection after the\n` +
      `  last test, a reporter that threw, a worker that died. Every human-facing signal\n` +
      `  says green; the process says otherwise, and the process is right.\n`,
  )
  failed = true
}

if (total < FLOOR) {
  console.error(
    `  FLOOR FAILED: ${total} tests ran, and the floor is ${FLOOR}.\n` +
      `  This is NOT a coverage target. It is the assertion that the suite still\n` +
      `  DISCOVERED itself. The usual cause is a broken test-file glob in\n` +
      `  vite.config.js, which makes the runner find fewer files and report a\n` +
      `  cheerful green for the handful it still sees.\n` +
      `  Fix the discovery. Do not lower the floor.\n`,
  )
  failed = true
}

if (failures > 0) {
  console.error(`  FLOOR FAILED: ${failures} test(s) failed.\n`)
  failed = true
}

process.exit(failed ? 1 : 0)
