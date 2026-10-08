import { test, expect } from '@playwright/test'
import {
  ALPHABET,
  NEVER_TOUCH,
  TEST_PREFIX,
  mintTestName,
  mintEmailAddress,
  matchesMintedShape,
  hitsNeverTouchList,
  isDeletable,
  assertDeletable,
} from '../../support/guards.js'

/**
 * The guards stand between this suite and a members register that has no
 * restore path. They are the one piece of harness code whose failure mode is
 * not a red build -- so they are tested in both directions, and each guard is
 * tested ALONE as well as together. A guard only ever exercised through the
 * combined check can be broken without anything noticing.
 */
test.describe('guard one: the minted-name pattern', () => {
  test('accepts a name this suite minted', () => {
    expect(matchesMintedShape(mintTestName('bank'))).toBe(true)
  })

  test('rejects the prefix alone, without the random tail', () => {
    // The tail is what makes parallel scenarios safe. A name carrying the
    // prefix but no tail was not minted here and must not be treated as ours.
    expect(matchesMintedShape(`${TEST_PREFIX}-bank`)).toBe(false)
  })

  test('rejects a plausible real name', () => {
    expect(matchesMintedShape('Standard Bank')).toBe(false)
  })

  // A guard reached with a non-string must answer "no", not throw. It sits on
  // the path to a delete, so an exception there is a cleanup that stops halfway
  // and leaks whatever came after it.
  for (const value of [null, undefined, 42, {}, []]) {
    test(`rejects ${JSON.stringify(value) ?? String(value)} rather than throwing`, () => {
      expect(matchesMintedShape(value)).toBe(false)
    })
  }
})

test.describe('guard two: the never-touch list', () => {
  test('flags a seeded institution', () => {
    expect(hitsNeverTouchList('Nedbank Limited')).toBe(true)
  })

  test('is case-insensitive', () => {
    expect(hitsNeverTouchList('ABSA')).toBe(true)
    expect(hitsNeverTouchList('absa')).toBe(true)
  })

  test('does not flag a minted name', () => {
    expect(hitsNeverTouchList(mintTestName('bank'))).toBe(false)
  })
})

test.describe('the two guards together', () => {
  test('a minted name is deletable', () => {
    expect(isDeletable(mintTestName('branch'))).toBe(true)
  })

  test('guard two overrides guard one when they disagree', () => {
    // This is the whole point of having two. If a minted name ever collides
    // with something real, the suite must refuse rather than reason about it.
    // The tail is LETTERS since 2026-09-18 -- the client strips digits from name
    // fields, so a hex tail is no longer a shape this suite can mint (see MINTED_NAME).
    const collision = `${TEST_PREFIX}-absa-abcdeghijklm`
    expect(matchesMintedShape(collision)).toBe(true)
    expect(isDeletable(collision)).toBe(false)
  })

  test('no random tail can spell a name guard two protects', () => {
    // p16, 2026-09-18: an a-p tail spelled `fnb` and the suite refused to clean up
    // its own row. Checked over the WHOLE list, not the one word that bit: every
    // protected name must contain a character the tail alphabet cannot produce.
    const spellable = NEVER_TOUCH.filter((real) => [...real].every((ch) => ALPHABET.includes(ch)))
    expect(spellable).toEqual([])
  })

  test('the tail alphabet is sixteen distinct lowercase letters', () => {
    // Sixteen, because one random byte is spent as two characters (low and high
    // nibble); letters, because the client strips digits from name fields.
    expect(new Set(ALPHABET).size).toBe(16)
    expect(ALPHABET).toMatch(/^[a-z]{16}$/)
  })

  test('refuses a name it cannot prove it created, with a readable message', () => {
    expect(() => assertDeletable('Capitec', 'bank')).toThrow(/REFUSING to delete bank/)
  })

  test('returns the name when it is safe, so callers can chain', () => {
    const name = mintTestName('bank')
    expect(assertDeletable(name, 'bank')).toBe(name)
  })
})

test.describe('the minted recipient address', () => {
  test('is a minted name at a domain reserved never to resolve', () => {
    const address = mintEmailAddress()
    const [local, domain] = address.split('@')
    // RFC 2606: nothing can deliver here, whatever the stack's mail server is.
    expect(domain).toBe('example.invalid')
    // The minted-name shape, lower-cased -- so it is recognisably the suite's.
    expect(local).toMatch(new RegExp(`^${TEST_PREFIX.toLowerCase()}-mail-[${ALPHABET}]{12}$`))
  })

  test('is different every time, so no two scenarios read one mailbox', () => {
    const addresses = new Set(Array.from({ length: 50 }, () => mintEmailAddress()))
    expect(addresses.size).toBe(50)
  })
})
