import { describe, expect, it } from 'vitest'

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

/**
 * NO MEMBER PERSONAL DATA IS COMMITTED TO THIS REPOSITORY.
 *
 * <h2>What this replaces</h2>
 * `src/data/members.json` held 160 member records — names, addresses, phone
 * numbers and **South African identity numbers** — tracked in git and imported by
 * nothing. It was the arc's oldest open question, and the owner settled it on
 * 2026-09-09: **synthetic, delete it.**
 *
 * <h2>🔴 Why the deletion needs a guard and not just a commit</h2>
 * Deleting a file is not a decision anything remembers. The next person to want a
 * realistic fixture will add one back, in the same folder, for a good reason — and
 * the review that should catch it is a diff of a large JSON blob, which is the
 * kind nobody reads line by line. **A fix without its guard is the half that rots.**
 *
 * <p>Trade union membership is **Special Personal Information under POPIA** — the
 * same prohibited-by-default category as health and biometrics — so this is not
 * ordinary tidiness. A file like this on every clone, fork and backup is the
 * exposure, and deleting it later does not remove it from history.
 *
 * <h2>What it actually looks for</h2>
 * A South African ID number is 13 digits and self-describing: `YYMMDD` followed by
 * a gender sequence, a citizenship digit and a Luhn check digit. So the scan does
 * not look for a filename — it looks for **the data**, in any JSON under `src/`,
 * and it validates candidates rather than counting digits. That way a fixture
 * added under a different name in a different folder is still caught, and a
 * 13-digit branch code or account number is not a false positive.
 */

const SRC = fileURLToPath(new URL('..', import.meta.url))

/** Every `.json` under `src/`, so a new folder cannot escape the scan. */
function jsonFiles(dir) {
  const found = []
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      found.push(...jsonFiles(path))
    } else if (entry.endsWith('.json')) {
      found.push(path)
    }
  }
  return found
}

/**
 * Is this 13-digit string a valid South African identity number?
 *
 * Checks the embedded date and the Luhn check digit. Both must hold, which is what
 * separates a real identity number from any other 13-digit value — an account
 * number, a phone number with a country code, a timestamp.
 */
function isSaIdNumber(digits) {
  if (!/^\d{13}$/.test(digits)) return false

  const month = Number(digits.slice(2, 4))
  const day = Number(digits.slice(4, 6))
  if (month < 1 || month > 12 || day < 1 || day > 31) return false

  let sum = 0
  for (let i = 0; i < 13; i += 1) {
    let n = Number(digits[i])
    // Luhn, from the right: every second digit doubled.
    if ((13 - i) % 2 === 0) {
      n *= 2
      if (n > 9) n -= 9
    }
    sum += n
  }
  return sum % 10 === 0
}

describe('no member personal data is committed', () => {
  const files = jsonFiles(SRC)

  it('the scan is looking at something -- there are JSON files under src/', () => {
    // 🔴 The non-vacuity half, and it is the one that matters here. A scan that
    // walks nothing reports no offenders and passes, which is exactly the shape
    // this suite has caught three times.
    expect(files.length).toBeGreaterThan(0)
  })

  it('the detector can tell a real identity number from any other 13 digits', () => {
    // Proved before it is trusted. Without this the scan below could be passing
    // because `isSaIdNumber` answers false for everything.
    expect(isSaIdNumber('9001010000080')).toBe(true)   // valid, and the suite's own fixture
    expect(isSaIdNumber('9001010000081')).toBe(false)  // check digit wrong
    expect(isSaIdNumber('9013010000080')).toBe(false)  // month 13
    expect(isSaIdNumber('1234567890123')).toBe(false)  // not an ID at all
    expect(isSaIdNumber('632005')).toBe(false)         // a branch code
  })

  it('🔴 no committed JSON carries South African identity numbers', () => {
    const offenders = []
    for (const file of files) {
      const text = readFileSync(file, 'utf8')
      const hits = (text.match(/\d{13}/g) ?? []).filter(isSaIdNumber)
      if (hits.length > 0) {
        offenders.push(`${file.replace(SRC, 'src/')} (${hits.length})`)
      }
    }

    expect(
      offenders,
      'A committed fixture carries member identity numbers. Trade union membership is '
      + 'Special Personal Information under POPIA, and a file here reaches every clone, '
      + 'fork and backup -- deleting it later does not remove it from history. Generate '
      + 'the fixture at test time instead, or use the identity numbers the suite already '
      + 'mints in src/test/.',
    ).toEqual([])
  })
})
