import { describe, expect, it } from 'vitest'

import {
  DERIVATION,
  DERIVATION_STATES,
  derivationNote,
  derivationStateOf,
} from './derivation'

/**
 * WHY A DERIVED FIELD IS EMPTY — the five states, and the order they are decided in.
 *
 * The rule these tests exist for is the ORDER, not the vocabulary. Any four of the
 * five states are easy to get right in isolation; what goes wrong in practice is
 * two of them being swapped, and the two that get swapped are always
 * SOURCE_UNAVAILABLE and NO_MATCH — "we could not ask" reported as "there is
 * nothing to fill in". That is CM-15's exact shape, and it is why the job-grade
 * field carries two separately-worded empty messages.
 */

describe('derivationStateOf decides in the documented order', () => {
  const base = { userEdited: false, hasInput: true, sourceLoaded: true, match: true }

  it('the user always wins -- S4, and the defect it comes from is Initials', () => {
    // Every other input says "we could derive a value". The user's edit still wins.
    expect(derivationStateOf({ ...base, userEdited: true })).toBe(DERIVATION.USER_EDITED)
    // Including when the source failed and there is no match to be had: a user who
    // typed a value must not then be told the field could not be loaded.
    expect(derivationStateOf({
      userEdited: true, hasInput: false, sourceLoaded: false, match: false,
    })).toBe(DERIVATION.USER_EDITED)
  })

  it('no input yet is NOT a failure', () => {
    // "Choose a bank first" and "the bank list is down" must not read the same.
    expect(derivationStateOf({ ...base, hasInput: false })).toBe(DERIVATION.NOT_APPLICABLE)
    // Even when the source is also unavailable: the user has nothing to act on yet,
    // so leading with the outage would be noise.
    expect(derivationStateOf({ ...base, hasInput: false, sourceLoaded: false }))
      .toBe(DERIVATION.NOT_APPLICABLE)
  })

  it('🔴 a missing SOURCE is distinguished from a missing MATCH', () => {
    // The pair this module exists for. Both leave the field blank; they are
    // opposite facts and they get opposite words.
    expect(derivationStateOf({ ...base, sourceLoaded: false, match: false }))
      .toBe(DERIVATION.SOURCE_UNAVAILABLE)
    expect(derivationStateOf({ ...base, sourceLoaded: true, match: false }))
      .toBe(DERIVATION.NO_MATCH)
  })

  it('and fills in when there is something to fill in', () => {
    expect(derivationStateOf(base)).toBe(DERIVATION.DERIVED)
  })

  it('every state it can answer is one of the five -- 04.4 audits against this', () => {
    // The canary. A sixth state invented at a call site would make the audit in
    // 04.4 meaningless, and nothing else would notice.
    for (const userEdited of [true, false]) {
      for (const hasInput of [true, false]) {
        for (const sourceLoaded of [true, false]) {
          for (const match of [true, false]) {
            expect(DERIVATION_STATES).toContain(
              derivationStateOf({ userEdited, hasInput, sourceLoaded, match }),
            )
          }
        }
      }
    }
  })
})

describe('derivationNote says where the value came from', () => {
  it('names the source, because "auto-filled" answers half of S3', () => {
    expect(derivationNote(DERIVATION.DERIVED, { source: "the branch's address on file" }))
      .toBe("Filled in from the branch's address on file.")
  })

  it('a failed source tells the user what to do about it', () => {
    const note = derivationNote(DERIVATION.SOURCE_UNAVAILABLE, { source: 'the bank list' })
    expect(note).toContain('could not be loaded')
    // The actionable half. "Could not load" on its own leaves the user unable to
    // tell whether waiting or typing is the right response.
    expect(note).toContain('you can still enter this yourself')
  })

  it('says nothing at all once the user has typed -- telling them so is noise', () => {
    expect(derivationNote(DERIVATION.USER_EDITED)).toBeUndefined()
  })

  it('a caller-supplied detail replaces the generic wording', () => {
    expect(derivationNote(DERIVATION.NO_MATCH, { detail: 'No subscription is on file yet.' }))
      .toBe('No subscription is on file yet.')
  })

  it('never throws, and never returns a half-built sentence', () => {
    // Called on every render of every derived field, so a throw here is a blank
    // screen rather than a blank field.
    for (const state of [...DERIVATION_STATES, undefined, null, 'INVENTED']) {
      const note = derivationNote(state)
      expect(note === undefined || typeof note === 'string').toBe(true)
      expect(note ?? '').not.toContain('undefined')
    }
  })
})
