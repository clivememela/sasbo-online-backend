import { execFileSync } from 'node:child_process'
import { readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Key-shaped material in files that are COMMITTED.
 *
 * A secret in a working tree is a mistake. A secret in git history is an
 * incident: it is on every clone, in every fork, and in every backup, and
 * deleting the file does not remove it. So the thing worth guarding is the
 * TRACKED set, which is why this asks git rather than walking the filesystem.
 *
 * 🔴 PATH EXCLUSIONS ARE APPLIED FIRST, AND THAT ORDER IS THE WHOLE DESIGN.
 * `package-lock.json` holds 409 `integrity` hashes -- counted 2026-09-05; the
 * plan recorded 276 on 2026-09-04 and the file has grown since, because a count
 * expires. Each is a 40+ character high-entropy base64 string that no entropy
 * test can tell from a real key.
 * Scan it and the guard's first run reports hundreds of findings -- and a first
 * run like that is not triaged, it is silenced. MobiZone recorded exactly
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
    why: 'holds 409 `integrity` hashes (counted 2026-09-05), every one a 40+ char high-entropy base64 string indistinguishable from a key. This single file is the difference between 1 finding and 410.',
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
 * Files allowed to contain a real secret, each with its register row.
 *
 * ✅ EMPTY SINCE 2026-09-22 (local only). Its one entry was `.env`, row B2: a
 * live Google API key -- the incumbent developer's -- committed to this
 * repository and kept VISIBLE here rather than excluded. The project left his
 * Vercel and Render that day and his key left the tracked `.env` with them, so
 * the entry went, and a key-shaped string in any tracked file now fails.
 *
 * ⚠️ WHAT THAT DOES NOT FIX: the key is still in this repository's HISTORY, and
 * it is live until its owner rotates it -- deleting a line revokes nothing.
 * That half of B2 belongs to the register; no test of the tree can hold it.
 *
 * Same discipline as KNOWN_MISSING: asserted exactly, so a new entry is a
 * review conversation rather than a quiet edit.
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
 * Every file git tracks in `repoRoot`, as repo-relative forward-slash paths.
 *
 * The pathspec is optional and additive: no arguments means the whole repository, which
 * is what the secret scan wants. `ignoredSources.js` passes `'src', 'index.html'` rather
 * than keeping a second copy of this call -- the NUL-splitting convention and the "git
 * absent, or not a repository" behaviour belong in one place, because the guard that
 * consumes them reports a CLEAN repository when they go wrong.
 */
export function trackedFiles(repoRoot, ...pathspec) {
  return execFileSync('git', ['ls-files', '-z', ...pathspec], { cwd: repoRoot, encoding: 'utf8' })
    .split('\0')
    .filter(Boolean)
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
 * with `.env` and `.env.local` moved aside -- and `.env` was the one file on
 * this guard's allowlist until 2026-09-22. Reading only the working tree would
 * have made the B2 pin redden during the very proof that is supposed to show
 * the suite needs no local environment.
 */
function readTrackedContents(repoRoot, path) {
  const full = join(repoRoot, path)
  try {
    if (statSync(full).size > 2_000_000) return null
    return readFileSync(full, 'utf8')
  } catch {
    // Not in the working tree. It is still tracked, so ask git for the blob.
    try {
      return execFileSync('git', ['show', `:${path}`], {
        cwd: repoRoot,
        encoding: 'utf8',
        maxBuffer: 2_000_000,
      })
    } catch {
      return null
    }
  }
}

/**
 * The secret signatures in one file's text, as `{ path, signature, line, at }`.
 *
 * Split out of `scanForSecrets` so the report's shape can be checked on a
 * fixture: a clean repository has no finding to check it on.
 */
export function findingsIn(path, contents) {
  const findings = []

  contents.split('\n').forEach((line, index) => {
    for (const { name, pattern } of SIGNATURES) {
      const match = pattern.exec(line)
      if (!match) continue

      const value = (match[0].split(/[:=]/).pop() ?? '').replace(/["'\s]/g, '')
      if (OBVIOUS_PLACEHOLDERS.test(value)) continue

      findings.push({ path, signature: name, line: index + 1, at: `${path}:${index + 1}` })
    }
  })

  return findings
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

  for (const path of trackedFiles(repoRoot)) {
    if (isExcluded(path)) continue

    const contents = readTrackedContents(repoRoot, path)
    if (contents === null || contents.includes('\0')) continue

    findings.push(...findingsIn(path, contents))
  }

  return findings
}
