import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { basename, dirname, join, relative, resolve, sep } from 'node:path'

/**
 * The one place this repository decides what "this suite's own source" means.
 *
 * WHY IT EXISTS
 * Three harness specs each grew their own copy of this walk -- `invariants`,
 * `disabledPopulation` and `featureFloors` -- and the copies had already
 * drifted apart before anyone noticed. `invariants` skipped eight directories;
 * `disabledPopulation` skipped six, missing `playwright-report` and
 * `blob-report`. So a `test.only` left behind in a file under `blob-report/`
 * was caught by one guard and not the other, and the two disagreed about the
 * answer to a question only one of them can be right about.
 *
 * That is the drift `support/guards.js` already writes a paragraph against:
 * two implementations of "is this ours?" is the bug. This is the same argument,
 * applied to the walk instead of to the name.
 *
 * 🔑 THE SKIP LIST IS THE UNION, DELIBERATELY. Taking the larger of the two was
 * the only safe direction: a directory that one guard already refused to scan
 * is one neither should scan, and widening `disabledPopulation`'s list cannot
 * hide a real offender -- `playwright-report/` and `blob-report/` hold
 * generated output, never a `test.only` a human wrote.
 *
 * WHY IT IS READ ONCE
 * `MainSourceInventory` and `TestSourceInventory` in the server repository each
 * carry a paragraph explaining why their read is cached and held: source cannot
 * change while the process runs, so re-walking it per test buys nothing and
 * pays a directory traversal each time. This is that rule, applied here, where
 * it had not been -- the three specs between them re-walked the tree eighteen
 * times per run.
 *
 * `readdirSync(dir, { withFileTypes: true })` rather than a `statSync` per
 * entry: the dirent already carries the answer, and asking the filesystem again
 * measured 3x slower on this repository (2.75ms against 0.91ms, 2026-09-07).
 *
 * WHY IT IS NOT SHARED WITH THE OTHER TWO REPOSITORIES
 * The same reason as every other module here. Three independent repositories
 * cannot import one module without creating the sibling-path coupling
 * `tests/harness/invariants.spec.js` exists to forbid.
 */

export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * A repository-relative path with forward slashes, for a failure message.
 *
 * 🔑 IT LIVES HERE BECAUSE THIS MODULE OWNS `repoRoot`, and the expression
 * `relative(repoRoot, f).split(sep).join('/')` had been written out in FIVE
 * places across the harness specs. That is the same drift this module was created
 * to end, one layer up: four copies of the walk became one, and four copies of
 * "how do we name what it found" were left behind.
 *
 * Forward slashes on every platform, deliberately, so a failure message reads the
 * same on Windows and on Linux and can be pasted into a review.
 */
export const repoRelative = (file) => relative(repoRoot, file).split(sep).join('/')

/**
 * Generated, installed or produced -- none of it is ours to assert about.
 *
 * The union of the two lists that existed before this module. Never trimmed
 * without checking every caller: a directory removed here becomes scannable by
 * three guards at once. The compiled features are not listed: see
 * `GENERATED_PREFIX`.
 */
export const SKIP_DIRS = new Set([
  'node_modules',
  '.git',
  'reports',
  'test-results',
  'playwright-report',
  'blob-report',
])

/**
 * Every directory playwright-bdd compiles features into starts with this:
 * `.features-gen`, `.features-gen-serial`, `.features-gen-acb`, and whichever
 * comes next. `tests/harness/sourceFiles.spec.js` holds every `outputDir` in
 * `playwright.config.js` to it.
 *
 * 🔴 A PREFIX, BECAUSE A LIST WAS WRITTEN IN FIVE PLACES: the config, this
 * walker, `scripts/clean.mjs`, `.gitignore` and `tools/test-all.mjs`. The
 * A.C.B. arc's `.features-gen-acb` (2026-09-21) reached only the first. The walk
 * then scanned its compiled specs, and the disabled-population guard convicted
 * the two skip declarations playwright-bdd had generated for scenarios the
 * feature file skips on purpose. Every one of those places now recognises the
 * family by this name.
 */
export const GENERATED_PREFIX = '.features-gen'

/** Every file under the repository root that is not in a skipped directory. */
function walk(dir, found) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name) || entry.name.startsWith(GENERATED_PREFIX)) continue
    const full = join(dir, entry.name)
    if (entry.isDirectory()) walk(full, found)
    else found.push(full)
  }
  return found
}

let everything = null

/** Read once and held. Source cannot change while the run is in flight. */
function allFiles() {
  if (everything === null) everything = walk(repoRoot, [])
  return everything
}

/**
 * The broad view: everything this suite could meaningfully assert about.
 *
 * `package-lock.json` is excluded by name rather than by extension -- it is
 * ours, but it is generated, and it is 486 lines of integrity hashes that no
 * invariant here has an opinion about.
 */
export function sourceFiles() {
  return allFiles().filter((f) => {
    const name = basename(f)
    if (name === 'package-lock.json') return false
    return /\.(js|mjs|cjs|json|yml|yaml|feature|md|Dockerfile)$/.test(name) || name.endsWith('Dockerfile')
  })
}

/**
 * Every file on disk that is not generated or installed, whatever its type.
 *
 * The committed-secret guard's view when git cannot be asked -- see
 * `trackedFiles` in `support/committedSecrets.js` for when that is. A secret
 * does not care about the file extension, so this is not `sourceFiles()`.
 * Absolute paths. Another `root` is walked afresh; this repository's is the
 * cached read.
 */
export function workingTreeFiles(root = repoRoot) {
  return resolve(root) === repoRoot ? allFiles() : walk(resolve(root), [])
}

/** The narrow view: the JavaScript this suite is actually written in. */
export function scriptFiles() {
  return allFiles().filter((f) => /\.(js|mjs)$/.test(f))
}

/** Every `.feature` file, wherever it sits under `features/`. */
export function featureFiles() {
  const prefix = join(repoRoot, 'features')
  return allFiles().filter((f) => f.startsWith(prefix) && f.endsWith('.feature'))
}
