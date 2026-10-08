import { expect, test } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { basename, dirname, isAbsolute, join, relative, sep } from 'node:path'

import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'
import { featureFiles, repoRoot } from '../../support/sourceFiles.js'

/**
 * EVERY SKIPPED SCENARIO STILL COMPILES: its steps bind, with the arguments they
 * take, so un-skipping it when its defect is fixed is a tag deletion and nothing
 * more.
 *
 * 🔴 PLAYWRIGHT-BDD NEVER CHECKS THEM. A skipped scenario compiles to a skip
 * without its steps' arguments being matched to their functions, and a feature
 * whose EVERY scenario is skipped compiles to nothing at all (9.2.0). The A.C.B.
 * arc's `acb-returns.feature` was such a file: its pending steps took the wrong
 * number of arguments for a `{string}`, the suite was green, and it was found by
 * hand (the ACB session, 2026-09-21, fixed at its `c5cd189`). Proven here before
 * this was written: a skipped scenario with a rotten step passed the suite's own
 * `bddgen` and failed this check with "Function has 1 argument, but expected 2".
 *
 * 🔑 SO THE FEATURES ARE COMPILED AGAIN WITH EVERY SKIP REMOVED -- a copy of
 * `features/` with `@skip` and `@fixme` taken off the tag lines, compiled by the
 * real `bddgen` against the real step definitions, into this test's own output
 * directory. The configurations are the suite's own, read from what playwright-bdd
 * recorded for this worker (`PLAYWRIGHT_BDD_CONFIGS`), so a new project is covered
 * the day it is configured and nothing here lists them.
 *
 * ⚠️ THE CHILD GETS NO `PLAYWRIGHT_BDD_*` FROM THIS PROCESS. Inherited, it would
 * carry the real configurations into the child's `bddgen`, which would regenerate
 * the suite's own compiled directories while other workers run from them. Nor
 * this worker's `TEST_WORKER_INDEX`: playwright-bdd decides it is inside a
 * Playwright worker by that alone (`config/index.js`), and there it records no
 * configuration -- the child then reported "no BDD configs found" on this
 * test's first run.
 */
test('every skipped scenario still compiles once its skip is removed', () => {
  const work = test.info().outputPath('unskipped')
  const posix = (path) => path.split(sep).join('/')

  let removed = 0
  for (const file of featureFiles()) {
    const text = readFileSync(file, 'utf8').replace(/^[ \t]*@.*$/gm, (tags) =>
      tags.replace(/(^|\s)@(skip|fixme)(?=\s|$)/g, () => {
        removed += 1
        return ''
      }),
    )
    const copy = join(work, relative(repoRoot, file))
    mkdirSync(dirname(copy), { recursive: true })
    writeFileSync(copy, text)
  }
  assertNonVacuousScan(Array.from({ length: removed }), FLOORS.KNOWN_BROKEN_SCENARIOS, 'skip tags removed before compiling')

  const recorded = Object.values(JSON.parse(process.env.PLAYWRIGHT_BDD_CONFIGS || '{}'))
  assertNonVacuousScan(recorded, 2, 'playwright-bdd configurations recorded for this worker')

  // Each recorded configuration again, its features read from the copy and its
  // steps from the repository: feature globs stay relative (the copy mirrors the
  // tree), step globs become absolute.
  const absolute = (glob, from) => (glob.startsWith('!') ? `!${absolute(glob.slice(1), from)}` : posix(isAbsolute(glob) ? glob : join(from, glob)))
  const configs = recorded.map((bdd) => ({
    name: basename(bdd.outputDir),
    features: bdd.features,
    steps: [bdd.steps].flat().map((glob) => absolute(glob, bdd.configDir)),
    outputDir: posix(join(work, 'compiled', basename(bdd.outputDir))),
  }))
  const config = join(work, 'playwright.config.mjs')
  writeFileSync(config, [
    "import { defineConfig } from '@playwright/test'",
    "import { defineBddConfig } from 'playwright-bdd'",
    `const configs = ${JSON.stringify(configs)}`,
    'export default defineConfig({',
    '  projects: configs.map(({ name, ...bdd }) => ({ name, testDir: defineBddConfig(bdd) })),',
    '})',
    '',
  ].join('\n'))

  const worker = new Set(['TEST_WORKER_INDEX', 'TEST_PARALLEL_INDEX'])
  const env = Object.fromEntries(
    Object.entries(process.env).filter(([key]) => !key.startsWith('PLAYWRIGHT_BDD') && !worker.has(key)),
  )
  const cli = join(repoRoot, 'node_modules', 'playwright-bdd', 'dist', 'cli', 'index.js')
  const compiled = spawnSync(process.execPath, [cli, 'test', '-c', config], { cwd: work, env, encoding: 'utf8' })

  expect(
    compiled.status,
    `bddgen, with every skip removed, over ${configs.map((c) => c.name).join(', ')}:\n` +
      `${compiled.stdout}${compiled.stderr}`.split('\n').filter(Boolean).slice(0, 20).join('\n'),
  ).toBe(0)

  // Not green because it compiled nothing: with no skip left, every feature file
  // -- an all-skipped one included, which the real compile drops -- becomes a spec.
  const specs = readdirSync(join(work, 'compiled'), { recursive: true }).filter((path) => String(path).endsWith('.feature.spec.js'))
  expect(specs.length, 'every feature file compiled to a spec once nothing in it was skipped').toBe(featureFiles().length)
})
