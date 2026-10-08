import { describe, expect, it } from 'vitest'

import { STATISTIC_READS } from '../../services/monthEndService'
import { committedSurface, committedVocabulary } from '../../test/apiContractScan'
import { monthEndBody } from '../../test/msw/monthEndExamples'
import {
  LISTINGS, MONTH_END_P4_REFUSAL, STATISTICS, cellText, defaultMonth, failureOf, instantLabel, isYearMonth,
  monthChoices, monthStateOf, monthsOf, rowHeader, ruleCNote, statisticHref, statisticNamed, statisticOf,
  takenSentence, workbookLabel,
} from './monthEndRules'

/**
 * Month end P4's rules, held to the SERVER'S (MD48): the statistics are the routes the server publishes, the codes the
 * screens act on are codes it gives, every sheet it answers has a head for its first column, and what a month is comes
 * from its months, never the browser's clock (MD55).
 */
const MONTHS = monthEndBody('GET /api/month-end/months')

/** The statistics the server routes: `GET /api/month-end/{month}/<name>` -- a listing's is two segments, not one. */
const ROUTED = committedSurface()
  .map((line) => /^GET \/api\/month-end\/\{month\}\/([a-z-]+)$/.exec(line)?.[1])
  .filter(Boolean)
  .sort()

describe('the statistics are the server\'s', () => {
  it('the screens name exactly the statistics the server routes, and the service reads each of them', () => {
    expect(ROUTED).toContain('membership') // the surface was read at all
    expect(STATISTICS.map((statistic) => statistic.key).sort()).toEqual(ROUTED)
    expect(Object.keys(STATISTIC_READS).sort()).toEqual(ROUTED)
  })

  it('each has a golden answer, which names it as its route does, with its sheets and its notes', () => {
    for (const { key } of STATISTICS) {
      const body = monthEndBody(`GET /api/month-end/{month}/${key}`)
      expect(body?.statistic, key).toBe(key)
      expect(statisticOf(body), key).toBe(body)
      expect(body.sheets.length, key).toBeGreaterThan(0)
    }
  })

  it('every one has a plain name and a legacy title, and so has every listing', () => {
    for (const row of [...STATISTICS, ...LISTINGS]) {
      expect(row.name.trim().length, row.key).toBeGreaterThan(3)
      expect(row.legacy.trim().length, row.key).toBeGreaterThan(3)
    }
    expect(new Set(STATISTICS.map((s) => s.name)).size).toBe(STATISTICS.length)
  })

  it('a statistic is found by its route name, and a name the server has none of is not found', () => {
    expect(statisticNamed('leavers')?.name).toBe('Why members left')
    expect(statisticNamed('listings')).toBeNull()
    expect(statisticNamed(undefined)).toBeNull()
  })

  it('its screen is at the Month end page\'s address, for the month chosen -- or for none', () => {
    expect(statisticHref('membership', '2026-08')).toBe('/admin/monthly-menu/membership?month=2026-08')
    expect(statisticHref('membership', '')).toBe('/admin/monthly-menu/membership')
  })

  it('the refusals the screens act on are refusals the server publishes (ME-13)', () => {
    const published = committedVocabulary().monthEnd.refusalCodes.map((refusal) => refusal.code)
    expect(published).toEqual(expect.arrayContaining(Object.values(MONTH_END_P4_REFUSAL)))
  })

  it('every sheet the server answers today has a head for its first column', () => {
    const sheets = STATISTICS.flatMap(({ key }) => monthEndBody(`GET /api/month-end/{month}/${key}`).sheets)
    expect(sheets.length).toBeGreaterThan(10)
    const unnamed = sheets.filter((sheet) => rowHeader(sheet.key) === 'Row').map((sheet) => sheet.key)
    expect(unnamed, 'a sheet the screen cannot say the rows of').toEqual([])
    expect(rowHeader('by-table-code')).toBe('Table code')
    expect(rowHeader('suspended-by-code')).toBe('Suspension code')
  })

  it('the must-pass: a sheet this browser has never seen is headed Row, not guessed at', () => {
    expect(rowHeader('by-planet')).toBe('Row')
    expect(rowHeader('something-new')).toBe('Row')
    expect(rowHeader(undefined)).toBe('Row')
  })
})

describe('the month -- the server\'s, never the browser\'s clock (MD55)', () => {
  it('a month is read only as the server writes one', () => {
    for (const month of ['2026-08', '2026-12', '1999-01']) expect(isYearMonth(month), month).toBe(true)
    for (const not of ['2026-13', '2026-00', '2026-8', '08-2026', '2026-08-01', 202608, null, undefined, '']) {
      expect(isYearMonth(not), String(not)).toBe(false)
    }
  })

  it('the golden months are the server\'s shape; an answer of any other shape is not', () => {
    expect(monthsOf(MONTHS)).toBe(MONTHS)
    for (const wrong of [[], null, undefined, {}, 'months', { openMonth: 202609, taken: [] }, { openMonth: '2026-09' },
      { openMonth: 'September', taken: [] }]) {
      expect(monthsOf(wrong)).toBeNull()
    }
  })

  it('opens on the newest month taken, whatever order they came in -- and on the open month before any is taken', () => {
    expect(defaultMonth(MONTHS)).toBe('2026-08')
    const three = { openMonth: '2026-09', taken: [{ month: '2026-06' }, { month: '2026-08' }, { month: '2026-07' }] }
    expect(defaultMonth(three)).toBe('2026-08')
    expect(defaultMonth({ openMonth: '2026-09', taken: [] })).toBe('2026-09')
  })

  it('offers the open month, then the months taken, newest first -- and keeps a month asked for that is neither', () => {
    const three = { openMonth: '2026-09', taken: [{ month: '2026-06' }, { month: '2026-08' }] }
    expect(monthChoices(three)).toEqual([
      { value: '2026-09', label: 'September 2026 — open' },
      { value: '2026-08', label: 'August 2026 — taken' },
      { value: '2026-06', label: 'June 2026 — taken' },
    ])
    expect(monthChoices(three, '2026-07').at(-1)).toEqual({ value: '2026-07', label: 'July 2026' })
    expect(monthChoices(three, '2026-08')).toHaveLength(3)
  })

  it('says what a month is: open, taken (when and by whom), not taken, not begun -- or nothing it could not read', () => {
    expect(monthStateOf(MONTHS, '2026-09')).toMatchObject({ state: 'OPEN', takenAt: null })
    expect(monthStateOf(MONTHS, '2026-08')).toEqual(MONTHS.taken[0])
    expect(monthStateOf(MONTHS, '2026-07')).toMatchObject({ state: 'NOT_TAKEN' })
    expect(monthStateOf(MONTHS, '2026-10')).toMatchObject({ state: 'NOT_BEGUN' })
    expect(monthStateOf(MONTHS, 'last-month')).toBeNull()
    expect(monthStateOf(MONTHS, '2026-13')).toBeNull()
  })

  it('an instant is said in Johannesburg, the zone named by the rule and not by the machine\'s', () => {
    expect(instantLabel('2026-09-29T08:00:00Z')).toBe('29 September 2026 at 10:00')
    // Past midnight in Johannesburg while it is still the day before in UTC: the date a person there lived.
    expect(instantLabel('2026-09-30T22:30:00Z')).toBe('1 October 2026 at 00:30')
    expect(instantLabel('not a moment')).toBe('not a moment')
    expect(instantLabel(null)).toBe('')
  })

  it('a taken month says when and by whom', () => {
    expect(takenSentence(MONTHS.taken[0])).toBe('Taken on 29 September 2026 at 10:00 by seed.')
    expect(takenSentence({ takenAt: '2026-09-29T08:00:00Z', takenBy: null })).toBe('Taken on 29 September 2026 at 10:00.')
  })
})

describe('a statistic, as a person reads it', () => {
  it('MV-36: an answer of the wrong shape is not a statistic -- never an empty one', () => {
    const golden = monthEndBody('GET /api/month-end/{month}/membership')
    for (const wrong of [[], null, {}, 'statistic', { ...golden, sheets: null }, { ...golden, month: 202608 },
      { ...golden, month: '08-2026' }, { ...golden, sheets: [{ key: 'x', rows: [] }] }]) {
      expect(statisticOf(wrong)).toBeNull()
    }
    expect(statisticOf({ ...golden, sheets: [] })).not.toBeNull()
  })

  it('a refusal is said in the server\'s sentence; a server without the route, or a wrong shape, is not available', () => {
    const refusal = Object.assign(new Error('That month was not taken.'), { status: 404, body: { code: 'MONTH_NOT_TAKEN' } })
    expect(failureOf(refusal, 'n/a')).toBe(refusal)
    expect(failureOf(Object.assign(new Error('Not Found'), { status: 404, body: { status: 404 } }), 'n/a'))
      .toEqual({ message: 'n/a', retryable: false })
    expect(failureOf(null, 'n/a')).toEqual({ message: 'n/a', retryable: false })
    const down = Object.assign(new Error('down'), { status: 503, retryable: true })
    expect(failureOf(down, 'n/a')).toBe(down)
  })

  it('counts, money and shares each through their one formatter -- and a cell with no meaning is the absence mark', () => {
    expect(cellText('COUNT', 12345)).toBe('12 345')
    expect(cellText('RAND', 174)).toBe('R 174,00')
    expect(cellText('SHARE', 0.0056)).toBe('0,56 %')
    for (const kind of ['COUNT', 'RAND', 'SHARE']) expect(cellText(kind, null), kind).toBe('—')
    expect(cellText('COUNT', 0)).toBe('0')
  })

  it('a kind the browser has never heard of is shown as the server wrote it', () => {
    expect(cellText('RATIO', 3)).toBe('3')
    expect(cellText('RATIO', null)).toBe('—')
  })

  it('finds the note that says why a row shows no number -- in the golden notes, and nowhere it is not', () => {
    const notes = monthEndBody('GET /api/month-end/{month}/branches').notes
    expect(ruleCNote(notes)).toBeGreaterThanOrEqual(0)
    expect(notes[ruleCNote(notes)]).toMatch(/fewer than five/)
    expect(ruleCNote(['August 2026 was taken on 2026-09-29.'])).toBe(-1)
    expect(ruleCNote(undefined)).toBe(-1)
  })

  it('a workbook by its name, and one the browser has never heard of as the server wrote it', () => {
    expect(workbookLabel('statistics')).toBe('the statistics workbook')
    expect(workbookLabel('monthly')).toBe('the monthly workbook')
    expect(workbookLabel('quarterly')).toBe('quarterly')
  })
})
