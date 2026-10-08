import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../../test/apiContractScan.js'
import { LETTER_OUTCOMES, emailingFrom, outcomeOf } from './welcomeLetterOutcome.js'

/**
 * 🔴 A64 -- WHAT THE CAPTURE DIALOG SAYS ABOUT THE LETTER, HELD TO THE SERVER'S OWN LIST.
 *
 * The server reports one of its `WelcomeLetterOutcome`s on every capture that asks for the letter, and publishes the
 * list in `api-vocabulary.json` (`welcomeLetter.outcomes`, generated -- never typed). The dialog's table must cover
 * exactly that list: an outcome the server adds would otherwise reach the clerk with nothing to say, and one the server
 * drops would be dead copy nobody notices.
 */
const vocabulary = committedVocabulary()

describe('the welcome letter outcomes (A64)', () => {
  it("the dialog's table covers exactly the outcomes the server publishes", () => {
    const published = vocabulary.welcomeLetter?.outcomes
    // The non-vacuity floor: an artifact that lost the block must not pass as "both empty".
    expect(published, 'api-vocabulary.json has no welcomeLetter block -- regenerate it with ./gradlew generateApiSurface').toBeDefined()
    expect(published.length).toBeGreaterThan(0)

    expect(Object.keys(LETTER_OUTCOMES).sort()).toEqual([...published].sort())
  })

  it('only SENT announces a sent letter; every other outcome still says the member is saved', () => {
    for (const [name, outcome] of Object.entries(LETTER_OUTCOMES)) {
      if (name === 'SENT') {
        expect(outcome.status).toBe('Welcome Letter Sent')
      } else {
        expect(outcome.status, `${name} must not claim a letter went`).toBe('Member Saved')
        expect(outcome.says, `${name} must say why no letter went`).toMatch(/saved/i)
      }
    }
  })

  it('only a failed send offers to try again', () => {
    const retrying = Object.entries(LETTER_OUTCOMES).filter(([, outcome]) => outcome.retry).map(([name]) => name)
    expect(retrying).toEqual(['FAILED'])
  })

  it('an answer with no welcomeLetter comes from a server before the fix, where a 200 meant SENT', () => {
    expect(outcomeOf({ id: 7 })).toBe('SENT')
    expect(outcomeOf(undefined)).toBe('SENT')
    // must-pass: an answer that says something is taken at its word
    expect(outcomeOf({ id: 7, welcomeLetter: 'EMAIL_OFF' })).toBe('EMAIL_OFF')
  })

  it('only a plain boolean says whether emailing is on; anything else is unknown', () => {
    expect(emailingFrom({ emailEnabled: true })).toBe('on')
    expect(emailingFrom({ emailEnabled: false })).toBe('off')
    // A proxy page, an older server without the route, and the suite's catch-all fixture.
    expect(emailingFrom([])).toBe('unknown')
    expect(emailingFrom({ emailEnabled: 'false' })).toBe('unknown')
    expect(emailingFrom(null)).toBe('unknown')
  })
})
