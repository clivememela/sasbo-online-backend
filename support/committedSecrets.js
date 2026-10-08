import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { isAbsolute, join, relative, resolve, sep } from 'node:path'

import { workingTreeFiles } from './sourceFiles.js'

/**
 * Key-shaped material in files that are COMMITTED.
 *
 * THIS IS A DELIBERATE COPY of the browser repository's `src/test/committedSecrets.js`,
 * with this repository's own exclusions and its own (empty) allowlist. Three
 * independent repositories cannot share a module -- importing across a sibling
 * path would pass on a machine where all three happen to be cloned side by side
 * and fail everywhere else -- so the duplication is the design, and this
 * comment is what stops the copies drifting silently. Change one, check all
 * three: `client/src/test/`, `server/src/test/java/.../support/`, and here.
 *
 * A secret in a working tree is a mistake. A secret in git history is an
 * incident: it is on every clone, in every fork, and in every backup, and
 * deleting the file does not remove it. So the thing worth guarding is the
 * TRACKED set, which is why this asks git rather than walking the filesystem
 * -- wherever git can be asked. `trackedFiles` says where it cannot.
 *
 * 🔴 PATH EXCLUSIONS ARE APPLIED FIRST, AND THAT ORDER IS THE WHOLE DESIGN.
 * This repository's own `package-lock.json` holds 39 `integrity` hashes --
 * counted here on 2026-09-05, because a count copied from another repository
 * is a count that was never true of this one. Each is a 40+ character
 * high-entropy base64 string that no entropy test can tell from a real key.
 * Scan them and the guard's first run reports hundreds of findings -- and a
 * first run like that is not triaged, it is silenced. MobiZone recorded exactly
 * that: "a guard that cries wolf gets its threshold lowered by whoever is
 * unlucky enough to hit it."
 *
 * The fix is to scope the INPUT, never to raise the threshold. A guard tuned by
 * raising its threshold is a guard that has been switched off.
 */

/**
 * Paths never scanned, with the reason each is here.
 *
 * Asserted non-empty and justified entry by entry, because an exclusion nobody
 * can see is where the real key hides next time.
 */
export const PATH_EXCLUSIONS = [
  {
    match: /(^|\/)package-lock\.json$/,
    why: 'holds 39 `integrity` hashes (counted in THIS repository on 2026-09-05), every one a 40+ char high-entropy base64 string indistinguishable from a key. This single file is the difference between 0 findings and 39.',
  },
  {
    match: /\.(png|jpe?g|gif|ico|webp|avif|svg|woff2?|ttf|eot|pdf|zip|jar|mp4|webm)$/i,
    why: 'binary and asset files. Their bytes are not source, and base64-encoded image data trips every entropy heuristic there is.',
  },
  {
    match: /(^|\/)(dist|build|coverage|node_modules)\//,
    why: 'build output and dependencies. Nothing here is authored, and bundled output concatenates every string in the project.',
  },
  {
    match: /\.min\.(js|css)$/,
    why: 'minified bundles. Mangled identifiers read as high-entropy tokens by construction.',
  },
]

/**
 * Files allowed to contain a real secret.
 *
 * 🔴 EMPTY FOR THIS REPOSITORY, AND ASSERTED EMPTY -- which is a claim, not an
 * absence. We own every file here, so there is no incumbent's credential to
 * work around, and the day somebody adds an entry they are making a decision
 * about a real credential that deserves a reviewer rather than a commit.
 *
 * The browser's repository carries exactly one entry (`.env`, register row B2,
 * a live Google API key) and names it rather than excluding it quietly.
 */
export const ALLOWLIST = []

/** Shapes that are a secret regardless of entropy. */
const SIGNATURES = [
  { name: 'Google API key', pattern: /\bAIza[0-9A-Za-z_-]{35}\b/ },
  { name: 'private key block', pattern: /-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----/ },
  { name: 'Stripe-style secret key', pattern: /\bsk_(?:live|test)_[0-9A-Za-z]{16,}\b/ },
  { name: 'AWS access key id', pattern: /\bAKIA[0-9A-Z]{16}\b/ },
  { name: 'Slack token', pattern: /\bxox[abprs]-[0-9A-Za-z-]{10,}\b/ },
  {
    name: 'JDBC URL carrying a password',
    pattern: /jdbc:[^\s"']*[?&;]password=(?!\s|$|["'&])[^\s"'&;]+/i,
  },
  {
    name: 'assigned credential literal',
    // `password = "hunter2"`, `apiSecret: 'abc123...'`. An assignment to a
    // credential-shaped NAME, which is what separates a real secret from a
    // random long string.
    pattern:
      /\b(?:password|passwd|secret|api[_-]?key|apikey|access[_-]?token|private[_-]?key|client[_-]?secret)\b\s*[:=]\s*["'][^"'\s]{8,}["']/i,
  },
]

/** Placeholder values that are obviously not secrets. */
const OBVIOUS_PLACEHOLDERS =
  /^(?:x+|y+|\*+|\.+|-+|_+|0+|changeme|placeholder|your[_-]?\w+|example|test|dummy|none|null|undefined|password|secret|todo)$/i

export function isExcluded(path) {
  return PATH_EXCLUSIONS.some((exclusion) => exclusion.match.test(path))
}

/**
 * Whether this filesystem holds `repoRoot`'s git metadata, and if not, why not.
 *
 * 🔴 THE SUITE LANE RUNS THIS INSIDE A CONTAINER, and the first version of this
 * guard asked git unconditionally -- green natively (83/83), and never run
 * where the gate runs it, so it was reverted (suite `5b42b78`). A git WORKTREE's
 * `.git` is a FILE naming its metadata by the HOST's path (`gitdir: C:/...`);
 * mounted into a Linux container that path does not exist, and no git can
 * answer. That -- and a checkout with no `.git` at all -- is the whole list of
 * reasons git may be passed over. A `.git` directory, or a pointer that
 * resolves, means git is asked and must answer.
 */
export function gitMetadata(repoRoot) {
  const dotGit = join(repoRoot, '.git')
  let stat
  try {
    stat = statSync(dotGit)
  } catch {
    return { reachable: false, why: 'there is no .git in this checkout' }
  }
  if (stat.isDirectory()) return { reachable: true }
  const pointer = /^gitdir:\s*(.+?)\s*$/m.exec(readFileSync(dotGit, 'utf8'))
  if (!pointer) return { reachable: false, why: '.git is a file with no gitdir line' }
  const gitdir = isAbsolute(pointer[1]) ? pointer[1] : resolve(repoRoot, pointer[1])
  return existsSync(gitdir)
    ? { reachable: true }
    : { reachable: false, why: `.git points at ${pointer[1]}, which is not on this filesystem -- a worktree mounted into a container` }
}

/**
 * `git` for THIS repository and nothing else, runnable as a different user.
 *
 * 🔑 `safe.directory`, scoped to the one checkout and the one command. On a
 * Linux runner the suite container is root and the checkout is the runner's
 * user's, so git refuses the repository as "dubious ownership" (deploy F19).
 * The check exists so that git does not act on a stranger's repository
 * configuration; this is the repository whose code the same process is already
 * running, so it trusts nothing new -- and a command-line `-c` is one of the
 * scopes git honours for this setting.
 */
function git(repoRoot, args, options = {}) {
  return execFileSync('git', ['-c', `safe.directory=${repoRoot.split(sep).join('/')}`, ...args], {
    cwd: repoRoot,
    encoding: 'utf8',
    ...options,
  })
}

/**
 * The files to scan, as repo-relative forward-slash paths, and where they came from.
 *
 * `git ls-files` wherever `gitMetadata` says git can be asked -- and then a
 * failure is thrown, never absorbed. Otherwise every file in the working tree
 * outside the generated and installed directories, and `from` SAYS so, which
 * the spec puts on its report. That is a wider set, not a narrower one, bar
 * one case: in a checkout whose metadata is elsewhere, the files ARE the
 * working tree, and a tracked file deleted from it is only visible to a run
 * where git is.
 */
export function trackedFiles(repoRoot) {
  const metadata = gitMetadata(repoRoot)
  if (!metadata.reachable) {
    const files = workingTreeFiles(repoRoot).map((f) => relative(repoRoot, f).split(sep).join('/'))
    return { files, from: `the working tree, because ${metadata.why}` }
  }
  const files = git(repoRoot, ['ls-files', '-z']).split('\0').filter(Boolean)
  return { files, from: 'git ls-files' }
}

/**
 * What git HAS for a tracked path -- working tree if present, index if not.
 *
 * 🔑 THE INDEX IS THE FALLBACK, AND IT IS NOT AN EDGE CASE. This guard is about
 * what is COMMITTED, so a file that is tracked but missing from the working
 * tree is exactly the case it must still see: deleting a file does not remove
 * its blob from history, and a guard that read only the working tree would go
 * green the moment someone moved the file aside.
 *
 * That is not hypothetical here. The CI-readiness proof runs the whole suite
 * with `.env` and `.env.local` moved aside -- and `.env` is the one file on
 * this guard's allowlist. Reading only the working tree would have made the
 * B2 pin redden during the very proof that is supposed to show the suite needs
 * no local environment.
 */
function readTrackedContents(repoRoot, path) {
  const full = join(repoRoot, path)
  try {
    if (statSync(full).size > 2_000_000) return null
    return readFileSync(full, 'utf8')
  } catch {
    // Not in the working tree. It is still tracked, so ask git for the blob.
    try {
      return git(repoRoot, ['show', `:${path}`], { maxBuffer: 2_000_000 })
    } catch {
      return null
    }
  }
}

/**
 * Every committed file whose contents match a secret signature.
 *
 * Returns `{ path, signature, line }` -- never the matched text. A guard that
 * prints the key it found copies that key into CI logs, test reports and issue
 * trackers, which is a second disclosure caused by the tool meant to prevent
 * the first.
 */
export function scanForSecrets(repoRoot) {
  const findings = []

  for (const path of trackedFiles(repoRoot).files) {
    if (isExcluded(path)) continue

    const contents = readTrackedContents(repoRoot, path)
    if (contents === null || contents.includes('\0')) continue

    contents.split('\n').forEach((line, index) => {
      for (const { name, pattern } of SIGNATURES) {
        const match = pattern.exec(line)
        if (!match) continue

        const value = (match[0].split(/[:=]/).pop() ?? '').replace(/["'\s]/g, '')
        if (OBVIOUS_PLACEHOLDERS.test(value)) continue

        findings.push({ path, signature: name, line: index + 1, at: `${path}:${index + 1}` })
      }
    })
  }

  return findings
}
