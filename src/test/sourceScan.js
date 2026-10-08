import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * `src/test/` -> the repository root.
 *
 * 🔑 IT LIVES HERE, IN THE LOWEST MODULE, because every consumer of the walk needs it and
 * this file must not import from anything that imports it. It was in `apiContractScan.js`,
 * which imports `readSources` FROM here — so `nonTestSources()` below could not have reached
 * it without a cycle. `apiContractScan.js` re-exports it, so every existing
 * `import { REPO_ROOT } from './apiContractScan.js'` keeps working unchanged.
 */
export const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

/**
 * Reading this repository's own source, for the guards that scan it.
 *
 * WHY COMMENTS ARE BLANKED BEFORE ANYTHING IS MATCHED
 * Both P4 guards search source for string literals -- and the comment
 * EXPLAINING a known-broken constant contains the very string being searched
 * for. `usePaymentTypeValidation.test.js` says, in prose, that the hook
 * compares against `'Payroll Deduction'`. A scan that reads comments convicts
 * that comment, reports a finding nobody can act on, and -- worse -- would go
 * green the day someone deleted the comment.
 *
 * MobiZone states it exactly: "A guard its own subject's docs can falsify is
 * not a guard."
 *
 * WHY BLANKED AND NOT DELETED
 * A blanked line becomes spaces, not nothing, so 1-based line numbers still
 * line up with the file on disk. A finding can then name `File.jsx:270` and
 * the reader opens that line and sees the code. Deleting comment lines would
 * shift every number below them, which turns an actionable failure message
 * into a wrong one.
 *
 * STRING LITERALS ARE LEFT ALONE. They are the thing being looked for.
 *
 * This is the client counterpart of the server's `MainSourceInventory`, and it
 * is deliberately a separate implementation: three independent repositories
 * cannot share a module, and pretending otherwise creates the sibling-path
 * coupling the plans forbid.
 */

/**
 * Replaces every comment with spaces, keeping every newline.
 *
 * Deliberately a character walk and not a regex. Comment markers appear inside
 * string literals (`'https://x'` contains `//`) and quotes appear inside
 * comments, and a regex that gets that wrong fails in the direction of a guard
 * convicting the wrong line -- which is how a guard gets switched off.
 */
export function blankComments(source) {
  let out = ''
  let i = 0

  while (i < source.length) {
    const c = source[i]
    const next = i + 1 < source.length ? source[i + 1] : ''

    if (c === '/' && next === '/') {
      while (i < source.length && source[i] !== '\n') {
        out += ' '
        i += 1
      }
    } else if (c === '/' && next === '*') {
      while (i < source.length && !(source[i] === '*' && source[i + 1] === '/')) {
        out += source[i] === '\n' ? '\n' : ' '
        i += 1
      }
      out += '  '
      i = Math.min(i + 2, source.length)
    } else if (c === '"' || c === "'" || c === '`') {
      const quote = c
      out += c
      i += 1
      while (i < source.length) {
        const s = source[i]
        out += s
        i += 1
        if (s === '\\' && i < source.length) {
          out += source[i]
          i += 1
        } else if (s === quote) {
          break
        }
      }
    } else {
      out += c
      i += 1
    }
  }

  return out
}

/**
 * Directories the walk below could not read, recorded rather than skipped in
 * silence.
 *
 * 🔴 THE FAILURE THIS EXISTS FOR REMOVES AN ENTIRE SUBTREE FROM A SCAN AND
 * REPORTS NOTHING. `readdirSync` throwing was caught and answered with `return`,
 * so a permission error, a broken symlink or a directory renamed mid-walk
 * dropped everything beneath it. Three guards read through this walker and their
 * protection was uneven:
 *
 *   - `apiContract.test.js` asserts it "scanned EVERY service module on disk"
 *     against a second `readdirSync` -- which covers a lost FILE and, it turns
 *     out, not a lost DIRECTORY: that second read is non-recursive, so an
 *     unreadable subdirectory disappears from both sides and the two lists
 *     still agree. It asserts this explicitly now.
 *   - `vocabulary.parity.test.js` stands on `FLOORS.VOCABULARY_COMPARISONS`,
 *     which has deliberate headroom against a much larger actual. Losing
 *     `src/hooks/` still clears the floor, and the guard reports success.
 *     🔴 **Headroom is silence, in exactly the amount of the headroom.**
 *   - 🔴 `scripts/contract-witness.mjs` READS THROUGH IT TOO, and it is the one
 *     consumer that CANNOT be given the assertion below. It reaches this walker
 *     through `apiContractScan.js`'s `discoverEndpoints()`, and it runs as plain
 *     Node AFTER the suite -- so it cannot import `assertNothingWasSkipped`,
 *     which pulls in `expect` from vitest. It is left unguarded knowingly rather
 *     than by oversight.
 *
 *     ⏳ The fix, if this ever matters: split the throwing half out of
 *     `assertNonVacuousScan.js` into a vitest-free module, which is exactly the
 *     split `floors.js` already exists to provide for these same three scripts.
 *
 * 🔑 THAT IS THE FLOORS DOCTRINE'S OWN WARNING, ARRIVING FROM THE OTHER SIDE. A
 * floor proves a scan was not VACUOUS. Only completeness proves it was not
 * PARTIAL, and a floor with healthy headroom is exactly what a partial scan
 * hides behind.
 *
 * Collected and exposed rather than thrown at the point of failure, which is the
 * shape `merge-reports.mjs` already uses for its `parseErrors`: the walk finishes
 * and reports everything it could not read, so one unreadable directory does not
 * mask the second.
 *
 * ⚠️ MODULE-SCOPED AND PER TEST FILE, which is narrower than it first looks.
 * Vitest isolates by default and this config does not override it, so every test
 * file gets a fresh worker and therefore a fresh copy of this array -- the
 * runner's own "workers spawned, per file" line says so. It accumulates across
 * every walk WITHIN one file and never across files, so a guard reading it sees
 * only its own file's walks.
 *
 * 🔑 THAT IS WHY EACH CONSUMER INSIDE THE RUNNER ASSERTS IT rather than one place
 * doing so for everybody: there is no "one place" that can see another file's
 * walk. ⚠️ "Each consumer" means the two that run under vitest -- the third,
 * above, cannot, and saying "each" without that exception was the first draft of
 * this paragraph.
 */
const unreadable = []

/**
 * Every directory the walk could not read, in the order it met them.
 *
 * Pure -- no `vitest` import -- because this module is imported by
 * `apiContractScan.js`, which the plain-Node scripts under `scripts/` reach.
 * The assertion that consumes this lives in `assertNonVacuousScan.js`, which is
 * already the vitest-bound half of this pair.
 */
export function unreadableDirectories() {
  return [...unreadable]
}

/** Every file under `dir` whose name matches `pattern`, recursively. */
export function filesUnder(dir, pattern = /\.(jsx?|mjs)$/) {
  const found = []

  const walk = (current) => {
    let entries
    try {
      entries = readdirSync(current, { withFileTypes: true })
    } catch (error) {
      // The path AND the reason. "could not read a directory" sends the reader
      // looking for a bug in the walker; `EACCES` or `ENOENT` with a path names
      // the actual condition and whether it is theirs to fix.
      unreadable.push(`${current}: ${error.code ?? error.message}`)
      return
    }
    for (const entry of entries) {
      if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === '.git') continue
      const full = join(current, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if (pattern.test(entry.name)) {
        found.push(full)
      }
    }
  }

  walk(dir)
  return found.sort()
}

/**
 * One source file, ready to be searched: comments blanked, line numbers intact.
 *
 * `path` is repo-relative with forward slashes, so a failure message reads the
 * same on Windows and on Linux and can be pasted into a review.
 *
 * `root` is REQUIRED rather than defaulted to `process.cwd()`. The default was
 * caught by `npm run lint` -- `process` is not defined in this repository's
 * browser-scoped eslint config -- and removing it is better than importing
 * `node:process` to support it: a scan whose paths depend on the directory the
 * runner happened to start in produces failure messages that point somewhere
 * else on another machine.
 */
export function readSources(dir, { root, pattern, exclude } = {}) {
  return filesUnder(dir, pattern)
    .map((full) => ({ full, path: relative(root, full).split(sep).join('/') }))
    // 🔑 EXCLUDED BEFORE THE READ, NOT AFTER IT. Every caller that wanted non-test sources used
    // to read the whole tree and then `.filter()` the test files out — so 46 files and ~392 KB
    // were read from disk AND run through `blankComments`' character walk purely to be
    // discarded, once per guard, and this arc added five more such guards. Measured on this
    // tree: 150 files match the widest pattern, 104 survive the exclusion. The filter is
    // purely path-based, so the resulting array is identical either way.
    .filter(({ path }) => !exclude?.(path))
    .map(({ full, path }) => {
      const raw = readFileSync(full, 'utf8')
      return {
        path,
        raw,
        code: blankComments(raw),
        at(line) {
          return `${path}:${line}`
        },
      }
    })
}

/**
 * Every non-test source file under `src/`, which is what nine guards actually want.
 *
 * 🔴 THE EXCLUSION RULE WAS HELD IN NINE PLACES. `readSources(join(REPO_ROOT, 'src'), …)`
 * followed by `.filter((s) => !/\.test\.|\/test\//.test(s.path))` was written out in five new
 * guards and four older ones, with the regex retyped each time. The day a `__tests__/`
 * directory appears, eight of them start reading their own canary fixtures as source and the
 * ninth does not — and a guard that scans its own fixtures finds the offence it planted.
 *
 * `pattern` is per-guard on purpose: a viewport-unit scan must read `.css`, a JSX walker must
 * not. What is shared is the exclusion, not the population.
 */
export function nonTestSources({ pattern = /\.(jsx?|mjs)$/ } = {}) {
  return readSources(join(REPO_ROOT, 'src'), {
    root: REPO_ROOT,
    pattern,
    exclude: (path) => /\.test\.|\/test\//.test(path),
  })
}

/** Does this text contain anything that looks like JavaScript? */
export function looksLikeCode(text) {
  return /\b(import|export|const|let|function|return|class)\b/.test(text)
}

/**
 * Files the blanker DESTROYED: they held code before it ran and hold none after.
 *
 * A blanker that blanks everything makes every guard downstream vacuous, and no
 * file-count floor catches it -- the files are all still there, they are just
 * empty. So the check compares each file against ITSELF, before and after.
 *
 * Comparing against the file count instead was wrong, and this repository has
 * the case that proves it: `src/pages/maintainance/Province.jsx` is a ZERO-BYTE
 * file. It survives nothing because it contains nothing, and a count-based
 * check reported the blanker as broken on a file the blanker never touched.
 * Found on 2026-09-05 by running the check rather than by reading it.
 */
export function blankingDestroyedCode(sources) {
  return sources.filter((s) => looksLikeCode(s.raw) && !looksLikeCode(s.code))
}
