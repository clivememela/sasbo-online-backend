import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { assertNonVacuousScan } from '../../support/assertNonVacuousScan.js'
import { MONTH_END_JOURNEY_FILES, dateAsWritten, monthBefore, monthOfDate, said } from '../../support/journeyMonth.js'
import { repoRoot } from '../../support/sourceFiles.js'

/**
 * No month-end journey asks a clock what month it is (MD55).
 *
 * The helper that names a journey's month is proved where it would fail: on the last evening of a month,
 * when the suite's UTC clock and Johannesburg's disagree about which month it is. The clock is replaced
 * with one that throws, so the proof is not only that the answer is right but that no clock was read.
 */

/** 22:30 UTC on 30 September -- already 1 October in Johannesburg. */
const LAST_EVENING = Date.parse('2026-09-30T22:30:00Z')

/** Run `fn` with every way of reading the clock made to throw. */
function withoutAClock(fn) {
  const RealDate = globalThis.Date
  class NoClock extends RealDate {
    constructor(...args) {
      if (args.length === 0) throw new Error('a clock was read')
      super(...args)
    }

    static now() {
      throw new Error('a clock was read')
    }
  }
  globalThis.Date = NoClock
  try {
    return fn()
  } finally {
    globalThis.Date = RealDate
  }
}

test.describe("a journey's month", () => {
  test('on the last evening of a month, the clocks disagree about which month it is', () => {
    // The premise, measured rather than assumed: if these agreed there would be nothing to prove.
    const utc = new Date(LAST_EVENING).toISOString().slice(0, 7)
    const johannesburg = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit' })
      .format(new Date(LAST_EVENING))
    expect(utc).toBe('2026-09')
    expect(johannesburg).toBe('2026-10')
  })

  test('is the month of the date the member was given, and reads no clock to say so', () => {
    expect(withoutAClock(() => monthOfDate('2026-09-30'))).toBe('2026-09')
    expect(withoutAClock(() => monthOfDate('2026-10-01'))).toBe('2026-10')
  })

  test('the month before a month is counted from that month, across a year, and reads no clock', () => {
    expect(withoutAClock(() => monthBefore('2026-10'))).toBe('2026-09')
    expect(withoutAClock(() => monthBefore('2026-01'))).toBe('2025-12')
    expect(() => monthBefore('2026-1')).toThrow(/yyyy-mm/)
  })

  test('reads a date in either shape the server writes it -- an array (A20) or ISO text', () => {
    expect(dateAsWritten([2026, 9, 30])).toBe('2026-09-30')
    expect(dateAsWritten('2026-09-30')).toBe('2026-09-30')
    expect(dateAsWritten('2026-09-30T00:00:00')).toBe('2026-09-30')
    expect(() => dateAsWritten([2026, 9])).toThrow(/Not a date/)
    expect(() => dateAsWritten(null)).toThrow(/Not a date/)
  })

  test('is never guessed from something that is not a date', () => {
    expect(() => monthOfDate('2026-09')).toThrow(/yyyy-mm-dd/)
    expect(() => monthOfDate(undefined)).toThrow(/yyyy-mm-dd/)
  })

  test('an instant the server wrote is said as its sentences say it, in Johannesburg, and reads no clock', () => {
    expect(withoutAClock(() => said('2026-10-02T10:03:56.324328Z'))).toBe('2 October 2026 at 12:03')
    // The last evening of a month: 22:30 UTC on 30 September is half past midnight on 1 October.
    expect(withoutAClock(() => said('2026-09-30T22:30:00.999999Z'))).toBe('1 October 2026 at 00:30')
  })

  test("month end's journey files read no clock", () => {
    const files = MONTH_END_JOURNEY_FILES.map((file) => ({ file, text: readFileSync(join(repoRoot, file), 'utf8') }))
    // A closed set, pinned exactly: every file named is read (and readFileSync above throws for one missing).
    assertNonVacuousScan(files, MONTH_END_JOURNEY_FILES.length, "month end's journey files")
    const code = (text) => text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
    const readers = files.filter(({ text }) => /new Date\(\s*\)|Date\.now\(/.test(code(text))).map(({ file }) => file)
    expect(readers, 'month-end journey files that read the clock').toEqual([])
  })

  test('the scan finds a clock where there is one', () => {
    // Its canary: the same predicate, over text that reads the clock, must see it.
    const code = (text) => text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
    expect(/new Date\(\s*\)|Date\.now\(/.test(code('const month = new Date().getMonth()'))).toBe(true)
    expect(/new Date\(\s*\)|Date\.now\(/.test(code('// new Date() in a comment is not a read'))).toBe(false)
  })
})
