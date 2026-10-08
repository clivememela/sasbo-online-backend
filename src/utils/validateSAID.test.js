import { describe, expect, it } from 'vitest'

import {
  extractDateOfBirthFromSAID,
  extractGenderFromSAID,
  formatDate,
  validateEmail,
  validateSAID,
} from './validateSAID'

/**
 * THE SA ID VECTORS. This table is a SPECIFICATION, not just a test.
 *
 * Today the client is the ONLY thing in the system that checks the SA ID
 * checksum: the server's `SaIdParser.isValidIdFormat` checks `\d{13}` and
 * nothing else, while its own Javadoc documents "Z -- Luhn checksum digit"
 * (register A8). So this function is the whole of the validation, and a POST
 * straight to the API bypasses it entirely.
 *
 * When that validation migrates to the server, THIS TABLE is what proves the
 * new implementation agrees with the behaviour members already experience. The
 * same vectors, under the same NAMES, are transcribed into the server's
 * skipped parity test. Deliberate duplication: three independent repositories
 * cannot share a fixture module, and pretending otherwise would create exactly
 * the sibling-path coupling the plans forbid. It is safe because the vectors
 * are a FROZEN SPECIFICATION rather than live code.
 *
 * The names are the contract. A failure in either language names the SAME
 * case, and the two tables can be compared by eye in seconds. Do not rename a
 * case without renaming its counterpart.
 *
 * EVERY NUMBER HERE IS SYNTHETIC. They were constructed by running the Luhn
 * arithmetic forwards; none belongs to a person. Coverage reports and failure
 * output are artefacts that get forwarded, so a real member's ID must never
 * appear in one.
 */
const VALID = [
  // name                       value            what it pins
  ['validCheckDigitFemale', '9001010000080', 'sequence 0000 -> female, born 1990-01-01'],
  ['validCheckDigitMale', '8506155000084', 'sequence 5000 -> male, born 1985-06-15'],
  ['validPermanentResident', '0002294999186', 'citizenship digit 1, and sequence 4999 -- the female boundary'],
  ['validLeapDay', '0002295000083', '29 February 2000, a real leap day, and the male boundary at 5000'],
  ['validDecember31', '9912315000080', 'the last day of a year'],
  ['validJanuary1', '7001015000087', 'the first day of a year'],
]

const INVALID = [
  ['wrongCheckDigit', '9001010000081', 'Invalid South African ID (Check digit failure)'],
  ['nonNumeric', '90010100000AB', 'ID must be exactly 13 digits'],
  ['nonNumericWithSpace', '900101 000008', 'ID must be exactly 13 digits'],
  ['shortLength', '900101000008', 'ID must be exactly 13 digits'],
  ['longLength', '90010100000800', 'ID must be exactly 13 digits'],
  ['impossibleMonthZero', '9000010000085', 'Invalid birth month'],
  ['impossibleMonthThirteen', '9013010000082', 'Invalid birth month'],
  ['impossibleDayZero', '9001000000089', 'Invalid birth day'],
  ['impossibleDayThirtyTwo', '9001320000080', 'Invalid birth day'],
]

describe('validateSAID -- the specification table', () => {
  describe('valid identity numbers are accepted', () => {
    it.each(VALID)('%s (%s) -- %s', (name, value) => {
      expect(
        validateSAID(value),
        `The vector named "${name}" is a WELL-FORMED South African ID with a correct ` +
          `check digit, and the only checksum validation in this system rejected it. A ` +
          `real member is being told their own ID number is invalid, at the point of ` +
          `joining.`,
      ).toBeNull()
    })
  })

  describe('malformed identity numbers are refused, with the reason', () => {
    it.each(INVALID)('%s (%s)', (name, value, expectedMessage) => {
      const result = validateSAID(value)
      expect(
        result,
        `The vector named "${name}" is INVALID and validateSAID accepted it. This ` +
          `function is the only checksum check in the entire system -- the server ` +
          `accepts any 13 digits (register A8) -- so anything it lets through reaches ` +
          `the database unchallenged.`,
      ).not.toBeNull()
      expect(
        result,
        `"${name}" was correctly refused, but for the wrong stated reason. The message ` +
          `is what the member reads and what tells them how to fix it.`,
      ).toBe(expectedMessage)
    })
  })

  describe('the boundaries of the rule itself', () => {
    it('an empty value is NOT an error -- the field is optional here', () => {
      // Deliberately asserted rather than left implicit: `if (!id) return null`
      // means an EMPTY id is valid. That is the required-field question, and it
      // belongs to the form, not to this validator. If that ever changes, this
      // is the test that will say so.
      expect(validateSAID('')).toBeNull()
      expect(validateSAID(undefined)).toBeNull()
      expect(validateSAID(null)).toBeNull()
    })

    it('the check digit is arithmetic, not a range -- every wrong digit is refused', () => {
      const correct = '9001010000080'
      const wrongOnes = []
      for (let d = 0; d <= 9; d += 1) {
        const candidate = correct.slice(0, 12) + String(d)
        if (candidate === correct) continue
        if (validateSAID(candidate) === null) wrongOnes.push(candidate)
      }
      expect(
        wrongOnes,
        `These ID numbers differ from a valid one ONLY in the check digit and were still ` +
          `accepted: ${wrongOnes.join(', ')}. That means the checksum is not being ` +
          `computed -- a validator that accepts nine wrong answers out of ten is a ` +
          `validator that has stopped working.`,
      ).toHaveLength(0)
    })
  })
})

describe('extractDateOfBirthFromSAID', () => {
  it.each([
    ['validCheckDigitFemale', '9001010000080', '1990-01-01'],
    ['validCheckDigitMale', '8506155000084', '1985-06-15'],
    ['validLeapDay', '0002295000083', '2000-02-29'],
  ])('%s yields %s', (name, id, expected) => {
    expect(extractDateOfBirthFromSAID(id), `vector ${name}`).toBe(expected)
  })

  it('refuses anything that is not 13 digits', () => {
    expect(extractDateOfBirthFromSAID('900101')).toBeNull()
    expect(extractDateOfBirthFromSAID('90010100000AB')).toBeNull()
    expect(extractDateOfBirthFromSAID('')).toBeNull()
  })

  it('never returns a date in the future', () => {
    // The century is inferred from the CURRENT year, so a two-digit year just
    // ahead of today resolves into the previous century. This asserts the
    // property -- "never in the future" -- rather than mirroring the
    // arithmetic, because a test that recomputes the rule changes its own
    // answer every 1 January along with the code.
    for (let yy = 0; yy <= 99; yy += 1) {
      const base = `${String(yy).padStart(2, '0')}0615500008`
      const id = base + '0'
      const out = extractDateOfBirthFromSAID(id.padEnd(13, '0').slice(0, 13))
      if (out === null) continue
      expect(
        new Date(out).getTime(),
        `A two-digit year of ${yy} produced ${out}, which is in the future. A member ` +
          `cannot have been born after today, and a date of birth in the future flows ` +
          `into age, eligibility and subscription arithmetic.`,
      ).toBeLessThanOrEqual(Date.now())
    }
  })
})

describe('extractGenderFromSAID', () => {
  it.each([
    ['validCheckDigitFemale', '9001010000080', 'Female'],
    ['femaleUpperBoundary', '0002294999186', 'Female'],
    ['maleLowerBoundary', '0002295000083', 'Male'],
    ['validCheckDigitMale', '8506155000084', 'Male'],
  ])('%s yields %s', (name, id, expected) => {
    expect(
      extractGenderFromSAID(id),
      `vector ${name}. The 4999/5000 boundary is the whole rule -- if it is off by one, ` +
        `every member whose sequence lands on it is recorded as the wrong gender.`,
    ).toBe(expected)
  })

  it('refuses anything that is not 13 digits', () => {
    expect(extractGenderFromSAID('12345')).toBeNull()
    expect(extractGenderFromSAID('abcdefghijklm')).toBeNull()
  })
})

describe('validateEmail', () => {
  it.each([
    ['plain', 'member@sasbo.example'],
    ['subdomain', 'member@mail.sasbo.example'],
    ['plusAddressing', 'member+union@sasbo.example'],
  ])('accepts %s', (_name, value) => {
    expect(validateEmail(value)).toBeNull()
  })

  it.each([
    ['noAt', 'member.sasbo.example'],
    ['noDomainDot', 'member@sasbo'],
    ['spaceInLocal', 'mem ber@sasbo.example'],
    ['trailingAt', 'member@'],
  ])('refuses %s', (_name, value) => {
    expect(validateEmail(value)).toBe('Invalid email specified')
  })

  it('treats an empty value as not-an-error, like validateSAID does', () => {
    expect(validateEmail('')).toBeNull()
  })
})

describe('formatDate', () => {
  it('formats an ISO string as dd-mm-yyyy', () => {
    expect(formatDate('2023-06-15')).toBe('15-06-2023')
  })

  it('formats the ARRAY the server actually sends', () => {
    // Register A20: JacksonConfig replaces Boot's ObjectMapper with a hand-built
    // one that never disables WRITE_DATES_AS_TIMESTAMPS, so every date on the
    // wire is [2023,6,15] rather than "2023-06-15". This branch exists ONLY
    // because of that defect. When A20 is fixed the array stops arriving -- and
    // this test is the note explaining why the branch can then be deleted.
    expect(formatDate([2023, 6, 15])).toBe('15-06-2023')
  })

  it('renders a missing date as a dash rather than as "undefined-undefined-"', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate('')).toBe('-')
  })
})
