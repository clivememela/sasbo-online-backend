import { describe, expect, it } from 'vitest'

import { READ_CEILING_MS, SLOW_NOTICE_MS } from '../services/parseApiError'
import { committedVocabulary } from '../test/apiContractScan'
import { monthEndBody } from '../test/msw/monthEndExamples'
import {
  LIST_EMPTY_REASON, MEMBER_STATUS_KIND, MONTH_END_REFUSAL, STATUS_KIND, STATUS_REASON, SUMMARY_GROUPING,
  UNGROUPED_BECAUSE, WORKPLACES_SERVED, committeeLine, dayLabel, deletionCodeOptions, emptyReasonSentence, listProvenance,
  listRows,
  listsProvenance, monthLabel, monthsTo, placeOptions, placeQuery, reasonFor, statusKind, summaryOf, suspensionCodeOptions,
  unplacedSentence,
} from './monthEnd'

/**
 * Month end's vocabulary held to the SERVER'S (`api-vocabulary.json`, `monthEnd` -- MD40): the codes the browser
 * branches on are codes the server gives, and every word the server can say of an empty list has its sentence.
 */
const monthEnd = committedVocabulary().monthEnd

describe('the month-end vocabulary is the server\'s', () => {
  it('publishes its own section, beside ACB\'s, which it does not touch', () => {
    expect(monthEnd).toBeDefined()
    expect(committedVocabulary().acb.refusalCodes.map((c) => c.code)).not.toContain('WORKPLACE_PAGE_INVALID')
  })

  it('the refusal codes the screens branch on are codes the server gives (ME-13)', () => {
    const published = monthEnd.refusalCodes.map((c) => c.code)
    expect(published).toEqual(expect.arrayContaining(Object.values(MONTH_END_REFUSAL)))
  })

  it('every reason a list can be empty for has its sentence, and no other (ME-19)', () => {
    expect(Object.keys(LIST_EMPTY_REASON)).toEqual(monthEnd.listEmptyReasons)
  })

  it('the switch\'s three words are the server\'s (MD62)', () => {
    expect(Object.values(WORKPLACES_SERVED)).toEqual(monthEnd.workplaceSwitch)
  })

  it('the published ceilings: two hundred a page, a hundred characters a search (MR-PERF-12, MR-SEC-22)', () => {
    expect(monthEnd.workplacePageMaxSize).toBe(200)
    expect(monthEnd.searchMaxLength).toBe(100)
  })
})

describe('what a person reads', () => {
  it('a day as a person writes it', () => {
    expect(dayLabel('2026-08-31')).toBe('31 August 2026')
    expect(dayLabel('2026-8-31')).toBe('2026-8-31')
    expect(dayLabel(undefined)).toBe('')
  })

  it('RC17: a day whose month or day is none is passed through, never "1 undefined 2026"', () => {
    expect(dayLabel('2026-13-01')).toBe('2026-13-01')
    expect(dayLabel('2026-00-01')).toBe('2026-00-01')
    expect(dayLabel('2026-09-00')).toBe('2026-09-00')
    expect(dayLabel('2026-09-32')).toBe('2026-09-32')
    expect(dayLabel('2026-12-01')).toBe('1 December 2026')
  })

  it("a workplace's committee in one line: code and name trimmed, the absence mark for neither (MO24)", () => {
    expect(committeeLine({ committee: 'Q01', committeeName: 'INVENTED' })).toBe('Q01, INVENTED')
    expect(committeeLine({ committee: 'Q01 ', committeeName: ' INVENTED ' })).toBe('Q01, INVENTED')
    expect(committeeLine({ committee: 'Q01', committeeName: null })).toBe('Q01')
    expect(committeeLine({ committee: '  ', committeeName: undefined })).toBe('—')
    expect(committeeLine(undefined)).toBe('—')
  })

  it('MD28: a list read off a report says so until SASBO confirms it', () => {
    expect(listProvenance({ printedOn: '2026-08-31', confirmedOn: null }))
      .toBe('As printed on 31 August 2026 — not yet confirmed by SASBO')
    expect(listProvenance({ printedOn: '2026-08-31', confirmedOn: '2026-10-09' }))
      .toBe('As printed on 31 August 2026 — confirmed by SASBO on 9 October 2026')
    expect(listProvenance(null)).toBe('')
  })

  it('MD28 for SASBO\'s lists together (02.1): one line where every list gives the same one, and none where they differ', () => {
    const status = monthEndBody('GET /api/reference/status')
    expect(listsProvenance(status)).toBe('As printed on 31 August 2026 — not yet confirmed by SASBO')
    const confirmed = (list) => ({ ...list, confirmedOn: '2026-10-09' })
    expect(listsProvenance({ ...status, workplaces: confirmed(status.workplaces), lists: status.lists.map(confirmed) }))
      .toBe('As printed on 31 August 2026 — confirmed by SASBO on 9 October 2026')
    // One list confirmed and the rest not, or a list printed on another day: there is no one line to say.
    expect(listsProvenance({ ...status, lists: [confirmed(status.lists[0]), ...status.lists.slice(1)] })).toBe('')
    expect(listsProvenance({ ...status, workplaces: { ...status.workplaces, printedOn: '2026-09-30' } })).toBe('')
    // Not a status read: nothing is claimed.
    for (const wrong of [null, undefined, [], {}, { lists: status.lists }]) expect(listsProvenance(wrong)).toBe('')
  })

  it('an empty list says why; a word the browser has never heard of is shown as the server wrote it', () => {
    expect(emptyReasonSentence('NOT_LOADED')).toBe('Not loaded on this demonstration yet.')
    expect(emptyReasonSentence('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })

  it('MV-36: an answer of the wrong shape is not a list -- never an empty one', () => {
    expect(listRows({ rows: [1] })).toEqual([1])
    expect(listRows([])).toBeNull()
    expect(listRows(null)).toBeNull()
    expect(listRows({})).toBeNull()
  })
})

describe('a status, its kind, and the reason a change into it carries (03.1, 03.2)', () => {
  it('the six statuses and their kinds are the server\'s, in its order', () => {
    expect(Object.entries(MEMBER_STATUS_KIND).map(([name, kind]) => ({ name, kind }))).toEqual(monthEnd.memberStatuses)
  })

  it('the kinds are the three the server knows, and no other', () => {
    expect([...new Set(monthEnd.memberStatuses.map((s) => s.kind))].sort()).toEqual(Object.values(STATUS_KIND).sort())
  })

  it('every reason a screen sends is one the server knows -- and none is ACB\'s or capture\'s', () => {
    expect(monthEnd.statusReasons).toEqual(expect.arrayContaining(Object.values(STATUS_REASON)))
    for (const theirs of ['SUBS_IN_ARREARS', 'UNPAID_SUSPENDED', 'CAPTURED']) {
      expect(monthEnd.statusReasons).toContain(theirs)
      expect(Object.values(STATUS_REASON)).not.toContain(theirs)
    }
  })

  it('the reason follows the kind, as the server judges it, and a status nobody knows has neither', () => {
    const byKind = { LEAVING: 'DELETION_CODE', SUSPENDED: 'SUSPENSION_CODE', NEITHER: 'REINSTATED' }
    for (const { name, kind } of monthEnd.memberStatuses) {
      expect(statusKind(name)).toBe(kind)
      expect(reasonFor(name)).toBe(byKind[kind])
    }
    expect(statusKind('Gone')).toBeNull()
    expect(reasonFor('Gone')).toBeNull()
  })
})

describe('SASBO\'s codes as a person chooses one (RC7: the number, never the row\'s id)', () => {
  it('every numbered deletion code the server publishes is offered by its number, the reasons under code 2 beneath it', () => {
    const options = deletionCodeOptions(monthEnd.deletionCodes)
    const numbered = monthEnd.deletionCodes.filter((row) => Number.isInteger(row.code))
    expect(options.map((o) => o.code)).toEqual(numbered.map((row) => row.code))
    expect(options.every((o) => o.label.startsWith(`${o.code} · `))).toBe(true)
    const underTwo = monthEnd.deletionCodes.filter((row) => row.under === 2).map((row) => row.name)
    expect(underTwo.length).toBeGreaterThan(0)
    expect(options.find((o) => o.code === 2).under).toEqual(underTwo)
    expect(options.filter((o) => o.code !== 2).every((o) => o.under.length === 0)).toBe(true)
  })

  it('every suspension code the server publishes is offered by its number', () => {
    expect(suspensionCodeOptions(monthEnd.suspensionCodes).map((o) => o.code))
      .toEqual(monthEnd.suspensionCodes.map((row) => row.code))
  })
})

describe('the figures (02.2, 02.3)', () => {
  it('the six groupings a person may choose are the server\'s, in its order (MD31)', () => {
    expect(Object.keys(SUMMARY_GROUPING)).toEqual(monthEnd.summaryGroupings)
  })

  it('ME-15: the server gives up on a figure before the browser says it is slow -- and long before the browser gives up', () => {
    // Four seconds, published by the server; five and ten are the browser's own (conventions §16). A person is told by
    // the server, in its sentence, before the browser would say "taking longer than expected".
    const serverGivesUpMs = monthEnd.summaryTimeoutSeconds * 1000
    expect(serverGivesUpMs).toBeGreaterThan(0)
    expect(serverGivesUpMs).toBeLessThan(SLOW_NOTICE_MS)
    expect(serverGivesUpMs).toBeLessThan(READ_CEILING_MS)
  })

  it('the switch\'s words that group nothing each have their line; ALL has none', () => {
    expect(Object.keys(UNGROUPED_BECAUSE).sort()).toEqual([WORKPLACES_SERVED.NAMES, WORKPLACES_SERVED.NONE].sort())
    expect(UNGROUPED_BECAUSE[WORKPLACES_SERVED.NONE]).toBe("SASBO's workplaces are not loaded on this demonstration yet.")
    expect(UNGROUPED_BECAUSE[WORKPLACES_SERVED.ALL]).toBeUndefined()
  })

  it('a month as a person writes it', () => {
    expect(monthLabel('2026-09')).toBe('September 2026')
    expect(monthLabel('2026-9')).toBe('2026-9')
    expect(monthLabel(undefined)).toBe('')
  })

  it('RC17: a month that is none -- the address can carry ?month=2026-13 -- is passed through, never "undefined 2026"', () => {
    expect(monthLabel('2026-13')).toBe('2026-13')
    expect(monthLabel('2026-00')).toBe('2026-00')
    expect(monthLabel('2026-12')).toBe('December 2026')
    expect(monthLabel('2026-01')).toBe('January 2026')
  })

  it('MD55: the months offered are the server\'s and the twelve before it, counted back across a year', () => {
    const months = monthsTo('2026-01')
    expect(months).toHaveLength(13)
    expect(months[0]).toEqual({ value: '2026-01', label: 'January 2026' })
    expect(months[1]).toEqual({ value: '2025-12', label: 'December 2025' })
    expect(months.at(-1)).toEqual({ value: '2025-01', label: 'January 2025' })
  })

  it('MV-36: the golden answer is a summary; an answer of the wrong shape is not -- never an empty one', () => {
    const golden = monthEndBody('GET /api/members/summary')
    expect(summaryOf(golden)).toBe(golden)
    for (const wrong of [[], null, undefined, {}, 'summary', { ...golden, rows: [] }, { ...golden, rows: undefined },
      { ...golden, totals: null }, { ...golden, month: 202609 }]) {
      expect(summaryOf(wrong)).toBeNull()
    }
  })

  it('MD35: all of SASBO, a region or an area -- and the request\'s part for each', () => {
    const options = placeOptions([{ code: 'NORTH', name: 'NORTH' }], [{ letter: 'A', region: 'NORTH' }])
    expect(options).toEqual([
      { value: '', label: 'All areas' },
      { value: 'region:NORTH', label: 'NORTH region' },
      { value: 'area:A', label: 'Area A' },
    ])
    expect(placeQuery('')).toEqual({})
    expect(placeQuery('region:NORTH')).toEqual({ region: 'NORTH' })
    expect(placeQuery('area:A')).toEqual({ area: 'A' })
  })

  it('ME-17: what the figures could not place is said in one line, by the value a status holds', () => {
    expect(unplacedSentence({ statuses: [{ status: 'Inactive', members: 3 }], noWorkplace: 12, noTableCode: 1 })).toBe(
      '3 members have a status this system does not know: "Inactive" · 12 members have no workplace on record'
        + ' · 1 member has a workplace with no table code',
    )
    expect(unplacedSentence({ statuses: [{ status: 'Inactive', members: 1 }, { status: 'Gone', members: 1 }] }))
      .toBe('2 members have a status this system does not know: "Inactive", "Gone"')
  })

  it('the must-pass: nothing unplaced says nothing', () => {
    expect(unplacedSentence(monthEndBody('GET /api/members/summary').unplaced)).toBe('1 member has no workplace on record')
    expect(unplacedSentence({ statuses: [], noWorkplace: 0, noTableCode: 0 })).toBe('')
    expect(unplacedSentence(undefined)).toBe('')
  })
})
