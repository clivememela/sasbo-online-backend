import { describe, expect, it } from 'vitest'

import {
  EMPTY,
  formatDate,
  formatDateLong,
  formatInt,
  formatPercent,
  formatZar,
  isValidDate,
  parseDate,
  toWireDate,
  today,
  sameMonth,
  yearsBetween,
} from './format'

describe('format — the UTC trap, which is the reason this module exists', () => {
  /**
   * 🔴 R-ROB-N, and the reason it is a rule rather than a preference. `CaptureMember.jsx:85`
   * computes today as `new Date().toISOString().split("T")`, so in South Africa (UTC+2) a
   * member captured between 00:00 and 01:59 SAST is dated YESTERDAY. That is CM-38, the
   * Capture Member arc's defect, and this asserts that this module cannot reproduce it.
   */
  it('today() reads the LOCAL calendar, where toISOString would be a day out', () => {
    // 00:30 on 15 June, local time. In any zone east of UTC this instant is still 14 June
    // in UTC — which is exactly the window the defect lives in.
    const justAfterMidnight = new Date(1988, 5, 15, 0, 30, 0)

    expect(today(justAfterMidnight)).toEqual({ y: 1988, m: 6, d: 15 })

    // The comparison that makes the point rather than merely stating it: what the broken
    // idiom would have produced, from the very same instant.
    const viaUtc = justAfterMidnight.toISOString().split('T')[0]
    const viaThisModule = toWireDate(today(justAfterMidnight))
    // In UTC+2 these differ; in UTC they agree. Asserting `viaThisModule` is right is the
    // contract — the UTC value is printed for whoever reads a failure here.
    expect(viaThisModule, `toISOString path gave ${viaUtc}`).toBe('1988-06-15')
  })

  it('every exported formatter is reachable without constructing a Date from a date string', () => {
    // A date-only value must never be parsed by `new Date("1988-06-15")`, which is midnight
    // UTC. These all go through the field parser instead.
    expect(formatDate('1988-06-15')).toBe('1988/06/15')
    expect(formatDateLong('1988-06-15')).toBe('15 Jun 1988')
    expect(toWireDate('15/06/1988')).toBe('1988-06-15')
  })
})

describe('format — parseDate accepts the four typed forms the approved mock names', () => {
  const expected = { y: 1988, m: 6, d: 15 }

  it.each([
    ['15 Jun 1988', 'the display form'],
    ['15 June 1988', 'the display form, spelled out'],
    ['15/06/1988', 'day first, the South African convention'],
    ['1988-06-15', 'year first, ISO-shaped'],
    ['19880615', 'eight digits'],
    ['1988/06/15', 'the form this app STORES, read back'],
  ])('accepts %s (%s)', (input) => {
    expect(parseDate(input)).toEqual(expected)
  })

  it('refuses an impossible date instead of rolling it over', () => {
    // `new Date(1988, 1, 31)` silently becomes 2 March. A birth date that quietly moves is
    // worse than one that is refused, and the mock's contract is refusal in plain words.
    expect(parseDate('31/02/1988')).toBeNull()
    expect(parseDate('1988-13-01')).toBeNull()
    expect(parseDate('1988-02-30')).toBeNull()
  })

  it('honours the leap year rather than approximating it', () => {
    expect(parseDate('29/02/2024')).toEqual({ y: 2024, m: 2, d: 29 })
    expect(parseDate('29/02/2023')).toBeNull()
    expect(parseDate('29/02/1900')).toBeNull() // divisible by 100, not by 400
    expect(parseDate('29/02/2000')).toEqual({ y: 2000, m: 2, d: 29 })
  })

  it('refuses to guess between day-first and month-first', () => {
    // 06/15/1988 is unambiguous ONLY because 15 cannot be a month. 05/06/1988 is genuinely
    // ambiguous, and this parser reads it day-first, always, rather than sometimes.
    expect(parseDate('05/06/1988')).toEqual({ y: 1988, m: 6, d: 5 })
    expect(parseDate('06/15/1988')).toBeNull()
  })

  it('returns null rather than throwing, for anything at all', () => {
    for (const input of [null, undefined, '', '   ', 'yesterday', {}, [], 'Jun 1988', '15/6/88']) {
      expect(parseDate(input)).toBeNull()
    }
  })

  it('isValidDate rejects non-integers, so a partially-typed field cannot slip through', () => {
    expect(isValidDate({ y: 1988, m: 6, d: 15 })).toBe(true)
    expect(isValidDate({ y: 1988, m: 6.5, d: 15 })).toBe(false)
    expect(isValidDate({ y: 1988, m: 6 })).toBe(false)
  })
})

describe('format — money and numbers name their locale', () => {
  it('formats rands with two decimals, always', () => {
    // 🔑 The assertion is on the DIGITS and the separator positions, not on the exact
    // whitespace: en-ZA uses a narrow no-break space as the group separator and which
    // codepoint that is has changed between ICU versions. Asserting the literal string
    // would make this test a report on the Node build rather than on this function.
    const formatted = formatZar(1234.5)
    expect(formatted.startsWith('R ')).toBe(true)
    expect(formatted.replace(/[^\d,.]/g, '')).toBe('1234,50')
  })

  it('renders a missing amount as the em dash, not as R 0.00', () => {
    // "R 0.00" is a FACT about a member's subscription. "—" is the absence of one, and
    // reporting the first when you mean the second is a confident wrong answer.
    expect(formatZar(null)).toBe(EMPTY)
    expect(formatZar(undefined)).toBe(EMPTY)
    expect(formatZar('')).toBe(EMPTY)
    expect(formatZar('not a number')).toBe(EMPTY)
    expect(formatZar(0)).not.toBe(EMPTY)
  })

  it('🔴 only a number, or a string that is one, is an amount -- never R 0,00 for nothing (00.2)', () => {
    // Number('  '), Number([]) and Number(false) are all 0: each would read as a member who pays nothing.
    for (const missing of ['   ', {}, [], true, false, NaN, Infinity, -Infinity]) {
      expect(formatZar(missing), String(missing)).toBe(EMPTY)
    }
    // The must-pass: a BigDecimal can arrive as a number or as a numeric string, and both are amounts.
    expect(formatZar('120').replace(/[^\d,.]/g, '')).toBe('120,00')
    expect(formatZar(99.99).replace(/[^\d,.]/g, '')).toBe('99,99')
  })

  it('formats counts and refuses nonsense', () => {
    expect(formatInt(1234).replace(/[^\d]/g, '')).toBe('1234')
    expect(formatInt(null)).toBe(EMPTY)
    expect(formatInt('abc')).toBe(EMPTY)
  })

  it('02.3: a penetration is a percentage with two decimals, shown as it is above a hundred', () => {
    // The same rule as the rands: the digits and the separator, not the exact space before the sign.
    expect(formatPercent(0.6337)).toMatch(/^63,37\s%$/)
    expect(formatPercent(1.042)).toMatch(/^104,20\s%$/)
    // The must-pass beside the refusals below: a penetration of nought is a figure.
    expect(formatPercent(0)).toMatch(/^0,00\s%$/)
  })

  it('🔴 02.3: no staff complement on record is no penetration -- the em dash, never 0,00 %', () => {
    for (const missing of [null, undefined, '0.5', NaN, Infinity]) {
      expect(formatPercent(missing), String(missing)).toBe(EMPTY)
    }
  })
})

describe('format — yearsBetween is the picker\'s age readout', () => {
  it('does not count a birthday that has not happened yet this year', () => {
    expect(yearsBetween('1988-06-15', { y: 2026, m: 6, d: 15 })).toBe(38)
    expect(yearsBetween('1988-06-15', { y: 2026, m: 6, d: 14 })).toBe(37)
    expect(yearsBetween('1988-06-15', { y: 2026, m: 5, d: 30 })).toBe(37)
  })

  it('is null when either end is unparseable, rather than a misleading number', () => {
    expect(yearsBetween('not a date')).toBeNull()
    expect(yearsBetween('1988-06-15', 'nonsense')).toBeNull()
  })
})

describe('format — sameMonth is the register\'s "New this month"', () => {
  it('matches year and month, whatever the day', () => {
    expect(sameMonth({ y: 2026, m: 9, d: 1 }, { y: 2026, m: 9, d: 30 })).toBe(true)
    expect(sameMonth(parseDate([2026, 9, 3]), today(new Date(2026, 8, 18)))).toBe(true)
  })

  it('does not match the same month of another year, or the neighbouring month', () => {
    expect(sameMonth({ y: 2025, m: 9, d: 18 }, { y: 2026, m: 9, d: 18 })).toBe(false)
    expect(sameMonth({ y: 2026, m: 8, d: 31 }, { y: 2026, m: 9, d: 1 })).toBe(false)
  })

  it('is false, never a match, when either date is missing', () => {
    expect(sameMonth(null, { y: 2026, m: 9, d: 1 })).toBe(false)
    expect(sameMonth(parseDate(null), today())).toBe(false)
  })
})
