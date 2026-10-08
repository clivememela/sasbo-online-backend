/**
 * Make the world clean BEFORE the suite runs.
 *
 * 🔑 WHY THIS IS `pretest` AND NOT `posttest`
 * npm SKIPS `posttest` when `test` exits non-zero -- and a failed or
 * interrupted run is exactly the one that leaves state behind. A teardown wired
 * to run only on success cleans up after the runs that did not need cleaning,
 * and leaves the mess made by the ones that did. Someone pressing Ctrl-C
 * halfway through a run is the ordinary case, not the exotic one, and the
 * residue it leaves poisons the NEXT run in a way that looks like a code bug
 * for an afternoon.
 *
 * So the run starts by making the world clean. Cleaning up afterwards as well
 * is fine, but it is not what makes the next run trustworthy.
 *
 * Scenario-level state is handled separately and per-scenario, in
 * support/world.js. This file owns only what is global to a run.
 */
import { readdir, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

import { GENERATED_PREFIX } from '../support/sourceFiles.js'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * Artefacts from the previous run.
 *
 * Reports are deleted rather than overwritten because a reporter that fails
 * partway leaves the PREVIOUS run's file in place, and a stale green report
 * sitting beside a red run is the most misleading artefact a suite can produce.
 * Generated specs go too: a `.feature` deleted from the repository leaves its
 * generated spec behind, and it keeps running and keeps passing.
 */
const STALE = ['reports', 'test-results', 'playwright-report', 'blob-report']

// Every compiled-feature directory, found by its prefix rather than listed. A
// generated spec left behind by a deleted feature keeps running and keeps
// passing, and one output directory forgotten here is one journey the suite
// still believes in -- which a list of them made easy (`GENERATED_PREFIX`).
const generated = (await readdir(repoRoot, { withFileTypes: true }))
  .filter((entry) => entry.isDirectory() && entry.name.startsWith(GENERATED_PREFIX))
  .map((entry) => entry.name)

for (const dir of [...STALE, ...generated]) {
  await rm(resolve(repoRoot, dir), { recursive: true, force: true })
}

console.log(`clean: removed ${STALE.length} artefact directories and ${generated.length} compiled-feature directories`)
