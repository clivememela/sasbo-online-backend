import { expect, test } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'

import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'
import { repoRoot } from '../../support/sourceFiles.js'

/**
 * `--repeat-each=N` RUNS EVERY TEST N TIMES -- the determinism gate's whole claim.
 *
 * 🔴 IT DID NOT, FOR 110 OF 116 (register E15). Playwright applies the command
 * line's count to top-level projects only, and `harness` and `journeys` are
 * dependencies of `journeys-serial`, so `npm run test:gate` repeated six
 * scenarios and reported green. Nothing said so: the count was plausible and
 * every test in it passed. The deploy arc's runner found it by counting cases
 * (R6-2, 2026-09-21); `playwright.config.js` now hands the count on.
 *
 * So this counts them, by asking Playwright itself what a repeated run would
 * run. `--list` starts no browser, needs no stack, and writes no report -- the
 * outer run's `reports/` and `test-results/` were checked untouched by it. The
 * fact, rather than the configuration that is supposed to produce it.
 */
const REPEAT = 2

test('--repeat-each repeats every test in every project, dependencies included', () => {
  const cli = join(repoRoot, 'node_modules', '@playwright', 'test', 'cli.js')
  const listed = spawnSync(process.execPath, [cli, 'test', '--list', '--reporter=json', `--repeat-each=${REPEAT}`], {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  })
  expect(listed.status, listed.stderr).toBe(0)
  const report = JSON.parse(listed.stdout)

  // "<project> › <file>:<line> <title>" -> how many times a repeated run would run it.
  const runs = new Map()
  const walk = (suite) => {
    for (const spec of suite.specs ?? []) {
      for (const run of spec.tests) {
        const key = `${run.projectName} › ${spec.file}:${spec.line} ${spec.title}`
        runs.set(key, (runs.get(key) ?? 0) + 1)
      }
    }
    for (const child of suite.suites ?? []) walk(child)
  }
  for (const suite of report.suites) walk(suite)

  assertNonVacuousScan([...runs.keys()], FLOORS.SCENARIOS, 'tests a repeated run lists')
  // Every configured project lists tests, so none can pass this by being absent from it.
  const projects = new Set([...runs.keys()].map((key) => key.split(' › ')[0]))
  expect([...projects].sort()).toEqual(report.config.projects.map((project) => project.name).sort())

  const wrong = [...runs].filter(([, times]) => times !== REPEAT).map(([key, times]) => `${key}: ${times}x`)
  expect(wrong, `tests that --repeat-each=${REPEAT} would not run ${REPEAT} times`).toEqual([])
})
