import { describe, expect, it } from 'vitest'

import { actualDebitDate, debitDateSentence, holidaySetFor, lastBusinessDayOnOrBefore } from './debitDate'

/**
 * 04.3 — the actual debit date.
 *
 * The dates below are real calendar dates, chosen because they have the shape
 * being tested, and each one names why. A test that computes its own expectation
 * from the same rule as the code proves only that the code is self-consistent.
 */

/** August 2026: the 9th is a Sunday (Women's Day), so the 10th is a holiday too. */
const AUG_2026 = new Date(2026, 7, 15)

describe('lastBusinessDayOnOrBefore', () => {
  it('leaves a business day alone', () => {
    // Friday 21 August 2026.
    expect(lastBusinessDayOnOrBefore(new Date(2026, 7, 21)).getDate()).toBe(21)
  })

  it('moves BACKWARDS off a weekend, never forwards -- the month-end fallback it serves', () => {
    // Sunday 23 August 2026 -> Friday the 21st. The rule only asks this helper when
    // going forward would leave the month, so it must never step forwards itself: at a
    // month end that would push the collection into the next month and change which
    // month the member has paid for.
    expect(lastBusinessDayOnOrBefore(new Date(2026, 7, 23)).getDate()).toBe(21)
    // Saturday the 22nd, same answer.
    expect(lastBusinessDayOnOrBefore(new Date(2026, 7, 22)).getDate()).toBe(21)
  })

  it('🔴 ITERATES over consecutive non-business days -- the Public Holidays Act case', () => {
    // Women's Day 2026 falls on SUNDAY 9 August, so MONDAY 10 August is a public
    // holiday too. A one-step implementation moving off Tuesday the 11th would land
    // on the 10th and stop -- on another non-business day. The answer is Friday the
    // 7th, and reaching it takes three steps.
    const holidays = new Set(['2026-08-09', '2026-08-10'])
    expect(lastBusinessDayOnOrBefore(new Date(2026, 7, 10), holidays).getDate()).toBe(7)
  })

  it('does not mutate the date it was given', () => {
    const input = new Date(2026, 7, 23)
    lastBusinessDayOnOrBefore(input)
    expect(input.getDate()).toBe(23)
  })
})

describe('actualDebitDate', () => {
  it('clamps a 31st in a short month rather than overflowing into the next', () => {
    // February 2026 has 28 days. The 28th is a Saturday, so the answer is Friday
    // the 27th -- BOTH adjustments at once, which is why they are reported apart.
    const result = actualDebitDate(31, new Date(2026, 1, 10))
    expect(result.day).toBe(27)
    expect(result.clamped).toBe(true)
    expect(result.adjusted).toBe(true)
  })

  it('reports neither flag when the requested day is already a business day', () => {
    const result = actualDebitDate(21, AUG_2026)
    expect(result).toMatchObject({ day: 21, clamped: false, adjusted: false })
  })

  it('says whether public holidays were accounted for -- and by default they are not', () => {
    // The fallback has to be VISIBLE, not assumed. Without the run's calendar an
    // answer that did not say so would be overstating what it knows.
    expect(actualDebitDate(21, AUG_2026).holidaysKnown).toBe(false)
    expect(actualDebitDate(21, AUG_2026, new Set(['2026-08-09'])).holidaysKnown).toBe(true)
  })

  it('answers null for a day that is not a day', () => {
    for (const bad of [null, undefined, '', 0, 32, 'the 20th', NaN]) {
      expect(actualDebitDate(bad, AUG_2026)).toBeNull()
    }
  })

  it('never returns a date outside the requested month', () => {
    // The reason the adjustment turns backward at the end of a month. Checked across
    // a year rather than at one date, because the failure would be seasonal.
    for (let month = 0; month < 12; month += 1) {
      for (const day of [20, 25, 28, 30, 31]) {
        const result = actualDebitDate(day, new Date(2026, month, 15))
        expect(new Date(`${result.iso}T00:00:00`).getMonth(), `${result.iso}`).toBe(month)
      }
    }
  })
})

describe('🔴 A57 / Q33 -- a non-business day moves FORWARD, and backward only where forward would leave the month', () => {
  // The source of the rule: SASBO's answer to question 33 (2026-09-21) -- "If a debit
  // date is on the 25th and it is a Saturday it will move to the following business day
  // which is the 27 ... we never go into the next month" -- and their July 2026 bureau
  // file, which moved Saturday the 25th to Monday the 27th. It overrules the signed
  // specification's backward-first reading, which this file ran until that answer.

  it('🔴 a weekend INSIDE the window moves FORWARD -- SASBO\'s own example: day 25, Saturday 25 July 2026, is Monday 27 July', () => {
    // The case that separates the two readings (W6). A backward-first rule says Friday the 24th, and agrees with this
    // one on all three of the specification's examples -- they differ on 56 of 288 cells in 2026-2027.
    expect(actualDebitDate(25, new Date(2026, 6, 1))).toMatchObject({ iso: '2026-07-27', day: 27, adjusted: true })
    // 26 and 27 September 2026 are a weekend: both are Monday the 28th, not Friday the 25th.
    expect(actualDebitDate(26, new Date(2026, 8, 1)).iso).toBe('2026-09-28')
    expect(actualDebitDate(27, new Date(2026, 8, 1)).iso).toBe('2026-09-28')
  })

  it('🟡 the seam, provisional until SASBO\'s August 2026 file answers question 47: a Sunday 30th with Monday 31st free goes forward', () => {
    // 30 August 2026 is a Sunday and the 31st a Monday: forward stays in the month, so the rule goes forward (W17).
    // SASBO's sentence read literally ("the 30 or 31st ... moved back") would say Friday the 28th.
    expect(actualDebitDate(30, new Date(2026, 7, 1)).iso).toBe('2026-08-31')
  })

  it('a Saturday 20th is debited on Monday the 22nd, never Friday the 19th', () => {
    // 20 June 2026 is a Saturday.
    const result = actualDebitDate(20, new Date(2026, 5, 10))
    expect(result).toMatchObject({ iso: '2026-06-22', day: 22, adjusted: true, clamped: false })
  })

  it('a Sunday 20th is debited on Monday the 21st -- the specification example', () => {
    // 20 September 2026 is a Sunday.
    expect(actualDebitDate(20, new Date(2026, 8, 1)).iso).toBe('2026-09-21')
  })

  it('a weekend at the END of the month moves backward, so it never leaves the month', () => {
    // 31 October 2026 is a Saturday: forward would be 2 November, another month.
    expect(actualDebitDate(31, new Date(2026, 9, 1)).iso).toBe('2026-10-30')
  })

  it('never lands below the 20th, across two years', () => {
    // The bottom of the window, which "never leaves the month" alone did not guard.
    for (let month = 0; month < 24; month += 1) {
      for (const day of [20, 21, 25, 28, 30, 31]) {
        const result = actualDebitDate(day, new Date(2026, month, 15))
        expect(result.day, `${result.iso} for the ${day}th`).toBeGreaterThanOrEqual(20)
      }
    }
  })

  it('a holiday Monday after a Sunday 20th keeps moving forward', () => {
    // Iteration still matters going forward: Sunday 20 September plus a supplied
    // holiday on Monday the 21st is Tuesday the 22nd.
    expect(actualDebitDate(20, new Date(2026, 8, 1), new Set(['2026-09-21'])).iso).toBe('2026-09-22')
  })
})

describe('debitDateSentence', () => {
  it('says the date, and says WHY it is not the day that was picked', () => {
    // A date with no explanation invites "that is wrong, I chose the 31st".
    const sentence = debitDateSentence(31, new Date(2026, 1, 10))
    expect(sentence).toContain('February has no 31st')
    expect(sentence).toContain('not a business day')
    expect(sentence).toContain('Friday 27 February')
  })

  it('carries the weekends-only caveat when no calendar is supplied', () => {
    expect(debitDateSentence(31, new Date(2026, 1, 10)))
      .toContain('Weekends only — public holidays are not accounted for.')
  })

  it('drops the caveat once holidays ARE known', () => {
    // The non-vacuity half: without this the caveat could be unconditional text.
    expect(debitDateSentence(31, new Date(2026, 1, 10), new Set(['2026-02-27'])))
      .not.toContain('Weekends only')
  })

  it('🔴 carries the caveat on an UNMOVED date too -- a weekday can be a public holiday', () => {
    // It used to stay plain here, which is the one case the caveat exists for: Heritage Day, Thursday
    // 24 September 2026, read as certain while the run debited Friday the 25th.
    expect(debitDateSentence(24, new Date(2026, 8, 22))).toBe(
      'This member will be debited on Thursday 24 September.'
      + ' Weekends only — public holidays are not accounted for.')
  })

  it('stays plain when nothing was adjusted and the calendar is known', () => {
    expect(debitDateSentence(21, AUG_2026, new Set(['2026-08-09', '2026-08-10'])))
      .toBe('This member will be debited on Friday 21 August.')
  })

  it('🔴 with the calendar, a weekday holiday moves like a weekend: day 24 in September 2026 is Friday the 25th', () => {
    expect(debitDateSentence(24, new Date(2026, 8, 22), new Set(['2026-09-24']))).toBe(
      'That day is not a business day, so this member will be debited on Friday 25 September.')
  })

  it('starts with a capital letter whichever reason leads it', () => {
    // Saturday 26 September 2026 moves forward to Monday the 28th: the day was only moved,
    // so "that day is not a business day" opens the sentence.
    expect(debitDateSentence(26, new Date(2026, 8, 1))).toBe(
      'That day is not a business day, so this member will be debited on Monday 28 September.'
      + ' Weekends only — public holidays are not accounted for.')
    // The twin: when the month is short, its name leads, as it always did.
    expect(debitDateSentence(31, new Date(2026, 1, 10))).toMatch(/^February has no 31st, and that day/)
  })
})

describe('holidaySetFor -- the run\'s calendar, as the set the sentence takes (D30)', () => {
  const HERITAGE_DAY = { date: '2026-09-24', name: 'Heritage Day', source: 'STATUTORY' }
  const calendar = (knownUntil) => ({ name: 'za-statutory', knownUntil, endingSoon: false, year: 2026, holidays: [HERITAGE_DAY] })

  it('is the year\'s dates when the calendar reaches the end of the month', () => {
    expect([...holidaySetFor(calendar('2027-12-31'), new Date(2026, 8, 22))]).toEqual(['2026-09-24'])
    // The boundary: reaching the month's last day exactly is reaching it.
    expect(holidaySetFor(calendar('2026-09-30'), new Date(2026, 8, 22)).size).toBe(1)
  })

  it('is EMPTY when the calendar stops before the month ends -- the run refuses that month (HOLIDAYS_NOT_KNOWN)', () => {
    expect(holidaySetFor(calendar('2026-09-29'), new Date(2026, 8, 22)).size).toBe(0)
  })

  it('is EMPTY for anything that is not a calendar', () => {
    // `[]` is what the page tests' GET wildcard answers; `none` is a database that holds no calendar.
    for (const notACalendar of [[], null, undefined, { name: 'none', knownUntil: null, holidays: [] }]) {
      expect(holidaySetFor(notACalendar, new Date(2026, 8, 22)).size).toBe(0)
    }
  })
})
