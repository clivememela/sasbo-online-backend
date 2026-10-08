import { describe, expect, it } from 'vitest'

import { REPO_ROOT } from './apiContractScan.js'
import { FLOORS, assertNonVacuousScan } from './assertNonVacuousScan'
import {
  ALLOWLIST,
  PATH_EXCLUSIONS,
  findingsIn,
  isExcluded,
  scanForSecrets,
  trackedFiles,
} from './committedSecrets.js'

/**
 * NO SECRET IS COMMITTED TO THIS REPOSITORY.
 *
 * The guard landed with one file on an allowlist: `.env`, holding a live
 * Google API key, register row B2 -- named rather than excluded, because
 * excluding it quietly would have made this guard read GREEN over a key on
 * every clone. Since 2026-09-22 (local only) the tracked `.env` holds no key,
 * the allowlist is empty, and the test B2 kept skipped runs. The key is still
 * in HISTORY, live until its owner rotates it; see `ALLOWLIST`.
 */
describe('committed secrets', () => {
  const tracked = trackedFiles(REPO_ROOT)
  const findings = scanForSecrets(REPO_ROOT)
  const allowed = new Set(ALLOWLIST.map((entry) => entry.path))

  describe('the scan actually scanned something', () => {
    it('asked git for the tracked files and got a repository back', () => {
      assertNonVacuousScan(
        tracked,
        FLOORS.TRACKED_FILES,
        'files tracked by git in this repository',
      )
    })

    it('scans source files rather than excluding everything', () => {
      const scanned = tracked.filter((path) => !isExcluded(path))

      // The mirror image of the exclusion list: an over-broad exclusion would
      // silence the guard just as effectively as a raised threshold, and the
      // finding count alone would not show it.
      assertNonVacuousScan(
        scanned,
        FLOORS.SCANNED_AFTER_EXCLUSIONS,
        'files actually scanned after path exclusions',
      )
      expect(scanned.some((path) => path.endsWith('.js') || path.endsWith('.jsx'))).toBe(true)
      expect(scanned).toContain('.env')
    })
  })

  describe('the path exclusions', () => {
    it('exist, and every one carries its reason', () => {
      // An exclusion nobody can see is where the real key hides next time.
      expect(PATH_EXCLUSIONS.length).toBeGreaterThan(0)
      for (const exclusion of PATH_EXCLUSIONS) {
        expect(exclusion.why, `${exclusion.match} has no justification`).toBeTruthy()
        expect(exclusion.why.length).toBeGreaterThan(30)
      }
    })

    it('excludes the lockfile, which is what stops the first run reporting hundreds', () => {
      // 🔴 THE ORDER IS THE DESIGN. package-lock.json holds 409 `integrity`
      // hashes (counted 2026-09-05 -- the plan recorded 276 on 2026-09-04, and
      // the file has grown since; a count expires). Each is a 40+ character
      // high-entropy base64 string no entropy test can distinguish from a key.
      //
      // Scanning it first and filtering after would produce a first run with
      // hundreds of findings -- which does not get triaged, it gets silenced.
      expect(isExcluded('package-lock.json')).toBe(true)
      expect(isExcluded('src/services/api.js')).toBe(false)
    })
  })

  describe('the allowlist', () => {
    it('is empty', () => {
      // An entry appearing later is a review conversation, not a quiet edit.
      // Its one entry, `.env` for B2, went on 2026-09-22 when the key left the
      // tracked file. A new one carries its register ROW, as that one did.
      expect(
        ALLOWLIST.map((entry) => entry.path),
        'Someone has allowlisted a file. That is a decision about a real secret in ' +
          'a real repository, and it needs a register row and a reviewer -- not a commit.',
      ).toEqual([])
    })
  })

  /**
   * B2's tree half, closed 2026-09-22 (local only).
   *
   * This was a skipped test written to the correct behaviour, beside a pin of
   * the wrong one. The key left the tracked `.env` -- the change the pin was
   * written to redden on -- so the pin is deleted and the test runs. The row's
   * other half, the key in history until its owner rotates it, no test of the
   * tree can hold.
   */
  describe('the committed Google API key', () => {
    it('is gone from the tracked .env', () => {
      expect(findings.filter((finding) => finding.path === '.env')).toEqual([])
    })
  })

  it('finds no secret outside the allowlist', () => {
    const unexpected = findings
      .filter((finding) => !allowed.has(finding.path))
      .map((finding) => `${finding.at}  [${finding.signature}]`)

    expect(
      unexpected,
      'A secret has been COMMITTED. It is now on every clone, every fork and every ' +
        'backup of this repository, and deleting the file does not remove it from ' +
        'history. Rotate the credential first -- that is the only step that actually ' +
        'revokes it -- then remove it from the working tree.',
    ).toEqual([])
  })

  it('reports the file and the shape, never the secret itself', () => {
    // A guard that prints the key it found copies that key into CI logs, test
    // reports and issue trackers -- a second disclosure, caused by the tool
    // meant to prevent the first.
    //
    // 🔑 ON A FIXTURE, BECAUSE THE TREE IS CLEAN. Until 2026-09-22 B2's key gave
    // this a real finding to inspect; with none left, a loop over the
    // repository's findings would pass by inspecting nothing. The key-shaped
    // value is assembled at run time, so this file holds none for the scan to find.
    const key = `AIza${'FIXTURE'.padEnd(35, '0')}`
    const reported = findingsIn('.env', `# local\nVITE_GOOGLE_API_KEY=${key}\n`)

    expect(reported.map((finding) => finding.at)).toEqual(['.env:2'])
    for (const finding of [...reported, ...findings]) {
      expect(Object.keys(finding).sort()).toEqual(['at', 'line', 'path', 'signature'])
      expect(JSON.stringify(finding)).not.toContain(key)
    }
  })
})
