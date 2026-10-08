import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../../test/apiContractScan'
import { monthEndBody, monthEndExamples } from '../../test/msw/monthEndExamples'
import {
  NOT_AVAILABLE, NOT_RECORDED, NO_ANSWER, STATUS_LABEL, STATUS_REASON_WORDS, UNKNOWN, failureMessage, failureOf,
  historyPageOf, historyRows, isSaveOfSeveral, maskedTail, olderThanHistory, pageGoneSentence, statusReasonText,
  valueText, whenOf, whoOf, workplaceOf,
} from './memberHistoryRules'

/**
 * The History tab's rules (month end 03.6), each on its own -- what the tab decides before it draws anything. Every
 * entry here is the server's golden entry (`month-end-examples.json`) with invented values in its own shape.
 */
const GOLDEN = monthEndBody('GET /api/members/{id}/history')
const entry = (fields) => ({ ...GOLDEN.content[0], ...fields })
const field = (id, what, saveId, fields = {}) => entry({ kind: 'FIELD', id, what, saveId, ...fields })
const status = (id, fields = {}) => entry({
  kind: 'STATUS', id, what: 'status', from: 'Active', to: 'Resigned', saveId: null, reason: 'DELETION_CODE', code: 2,
  codeName: 'Resigned Society', ...fields,
})

describe('who made a change', () => {
  it('"Not recorded" until sign-in, never "System" -- the owner\'s call on the mock', () => {
    expect(whoOf('system')).toBe(NOT_RECORDED)
    expect(NOT_RECORDED).toBe('Not recorded')
    expect(whoOf('SYSTEM')).toBe('Not recorded')
    expect(whoOf('')).toBe('Not recorded')
    expect(whoOf(null)).toBe('Not recorded')
    // The golden answer's own actor -- every change is `system` until sign-in (PA-12).
    expect(GOLDEN.content.every((e) => whoOf(e.changedBy) === 'Not recorded')).toBe(true)
  })

  it('any other actor is printed as the server wrote it', () => {
    expect(whoOf('seed')).toBe('seed')
    expect(whoOf(' A Person ')).toBe('A Person')
  })
})

describe('when a change was made', () => {
  it('is the day and the time in Johannesburg, in the record\'s own form', () => {
    expect(whenOf('2026-09-29T08:14:00Z')).toEqual({ date: '2026/09/29', time: '10:14' })
    // 22:30 UTC is half past midnight the next day in South Africa: the day is Johannesburg's, not the machine's.
    expect(whenOf('2026-09-29T22:30:00Z')).toEqual({ date: '2026/09/30', time: '00:30' })
  })

  it('an instant that cannot be read is shown as it came, never as "Invalid Date"', () => {
    expect(whenOf('not a moment')).toEqual({ date: 'not a moment', time: '' })
    expect(whenOf(null)).toEqual({ date: '—', time: '' })
  })
})

describe('a page of the history', () => {
  it('is the server\'s shape, the golden page among them', () => {
    expect(historyPageOf(GOLDEN)).toBe(GOLDEN)
  })

  it('an answer of the wrong shape is no page at all -- the shared fixture\'s [] above all (MV-36)', () => {
    for (const body of [[], null, undefined, {}, 'text', { ...GOLDEN, content: 'x' }, { ...GOLDEN, page: '0' },
      { ...GOLDEN, content: [null] }, { ...GOLDEN, totalElements: undefined }]) {
      expect(historyPageOf(body)).toBeNull()
    }
  })
})

describe('the rows the sheet draws', () => {
  it('one a change, newest first as the server gave them; the fields of one save are one row (ME-22)', () => {
    const content = [
      status(1),
      field(7, 'workplace', 's7'),
      field(6, 'address', 's4'),
      field(5, 'phone', 's4'),
      field(4, 'personalEmail', 's4'),
      field(3, 'identityNumber', 's3'),
    ]
    const rows = historyRows(content)
    expect(rows.map((row) => row.entries.map((e) => e.id))).toEqual([[1], [7], [6, 5, 4], [3]])
    expect(rows.map(isSaveOfSeveral)).toEqual([false, false, true, false])
    // A change of status is never part of a save, whatever it carries.
    expect(historyRows([status(1, { saveId: 's4' }), field(5, 'phone', 's4')]).map((row) => row.entries.length)).toEqual([1, 1])
  })

  it('a save straddling the page\'s edges says so on each page -- and only where the entry beyond is that save\'s', () => {
    const content = [field(9, 'phone', 'sA'), field(8, 'title', 'sB'), field(7, 'surname', 'sC')]
    const straddling = historyRows(content, { before: 'sA', after: 'sC' })
    expect(straddling.map((row) => [row.continuedFrom, row.continuesOn])).toEqual([[true, false], [false, false], [false, true]])
    // A single field of a save that runs on is a save of several, and says so.
    expect(straddling.map(isSaveOfSeveral)).toEqual([true, false, true])

    const ending = historyRows(content, { before: 'sZ', after: 'sY' })
    expect(ending.map((row) => [row.continuedFrom, row.continuesOn])).toEqual([[false, false], [false, false], [false, false]])
    // No page beyond an edge: nothing runs on.
    expect(historyRows(content).map((row) => [row.continuedFrom, row.continuesOn])).toEqual([[false, false], [false, false], [false, false]])
  })

  it('an entry beyond the edge that could not be read is NOT KNOWN, never "does not run on"', () => {
    const rows = historyRows([field(9, 'phone', 'sA'), field(8, 'title', 'sB')], { before: UNKNOWN, after: UNKNOWN })
    expect(rows.map((row) => [row.continuedFrom, row.continuesOn])).toEqual([[null, false], [false, null]])
    expect(rows.map(isSaveOfSeveral)).toEqual([true, true])
  })
})

describe('the values of a change', () => {
  it('a number the server keeps only the last four of, and nothing that merely starts with a star', () => {
    expect(maskedTail('****4087')).toBe('4087')
    expect(maskedTail('****')).toBe('')
    for (const value of ['4087', '*4087', '***4087', '****40871', '**** 4087', null, undefined, 42]) {
      expect(maskedTail(value)).toBeNull()
    }
  })

  it('a workplace as the trail writes it: its name and its R/A number', () => {
    expect(workplaceOf('900001 INVENTED MAIN STREET')).toEqual({ raNumber: '900001', name: 'INVENTED MAIN STREET' })
    expect(workplaceOf('INVENTED MAIN STREET')).toBeNull()
    expect(workplaceOf(null)).toBeNull()
  })

  it('reads each value as the record does, and nothing as an invented default', () => {
    expect(valueText('isShopSteward', 'true')).toBe('Yes')
    expect(valueText('isBec', 'false')).toBe('No')
    expect(valueText('isCosatu', 'Y')).toBe('Yes')
    expect(valueText('isCosatu', 'N')).toBe('No')
    expect(valueText('dateOfBirth', '1985-06-12')).toBe('1985/06/12')
    expect(valueText('shopStewardElectionDate', '2026-03-14')).toBe('2026/03/14')
    expect(valueText('becStructures', 'INC / IC,MANCOM')).toBe('INC / IC, MANCOM')
    expect(valueText('monthlySubscription', '151.00')).toBe('R 151,00')
    expect(valueText('phone', '+27 60 555 0188')).toBe('+27 60 555 0188')
    // Not on file: nothing, or nothing but spaces -- never "No", never R 0,00.
    for (const none of [null, undefined, '', '   ']) {
      expect(valueText('isBec', none)).toBeNull()
      expect(valueText('monthlySubscription', none)).toBeNull()
    }
    // A value that is not what its field says is shown as it came.
    expect(valueText('dateOfBirth', 'someday')).toBe('someday')
    expect(valueText('title', 'true')).toBe('true')
  })
})

describe('the reason a status changed', () => {
  it('SASBO\'s code by its number and its name -- "Code 2 · Resigned Society" (RC7)', () => {
    expect(statusReasonText(status(1))).toBe('Code 2 · Resigned Society')
    expect(statusReasonText(status(1, { code: 13, codeName: 'DEBIT RETURNED', reason: 'SUSPENSION_CODE' }))).toBe('Code 13 · DEBIT RETURNED')
    expect(statusReasonText(status(1, { codeName: null }))).toBe('Code 2')
  })

  it('with no code, the reason in words -- and a reason the browser does not know, as the server wrote it', () => {
    expect(statusReasonText(status(1, { code: null, codeName: null, reason: 'REINSTATED' }))).toBe('Reinstated')
    expect(statusReasonText(status(1, { code: null, codeName: null, reason: 'SUBS_IN_ARREARS' }))).toBe('Three unpaid debit orders (Debit Order Deductions)')
    expect(statusReasonText(status(1, { code: null, codeName: null, reason: 'SOMETHING_NEW' }))).toBe('SOMETHING_NEW')
    expect(statusReasonText(status(1, { code: null, codeName: null, reason: null }))).toBeNull()
  })

  it('every reason the server gives has its words, and no other (api-vocabulary.json)', () => {
    expect(Object.keys(STATUS_REASON_WORDS).sort()).toEqual([...committedVocabulary().monthEnd.statusReasons].sort())
  })

  it('a change of status is named as the record\'s Membership tab names the field', () => {
    expect(STATUS_LABEL).toBe('Account Membership Status')
  })
})

describe('a member older than the history (ME-7)', () => {
  it('captured before the history began, by the day -- whichever spelling the day arrives in', () => {
    expect(olderThanHistory({ createdAt: [2026, 8, 31, 23, 0, 0] }, '2026-09-01')).toBe(true)
    expect(olderThanHistory({ createdAt: '2019-04-01T09:00:00' }, '2026-09-01')).toBe(true)
    expect(olderThanHistory({ createdAt: [2026, 9, 1, 8, 0] }, '2026-09-01')).toBe(false)
    expect(olderThanHistory({ createdAt: '2026-09-30T10:00:00' }, '2026-09-01')).toBe(false)
  })

  it('never claimed where either day is not known', () => {
    expect(olderThanHistory({}, '2026-09-01')).toBe(false)
    expect(olderThanHistory({ createdAt: [2019, 4, 1] }, null)).toBe(false)
    expect(olderThanHistory({ createdAt: 'yesterday' }, '2026-09-01')).toBe(false)
    expect(olderThanHistory(null, '2026-09-01')).toBe(false)
  })
})

describe('a read that failed', () => {
  const refusal = (code) => ({ status: monthEndExamples.refusals[code].status, retryable: false, body: monthEndExamples.refusals[code].body })

  it('an older server with no such route -- a 404 carrying no code -- is not available, with no retry (MR-ROB-31)', () => {
    const failure = failureOf({ status: 404, retryable: false, body: { status: 404, error: 'Not Found' } })
    expect(failure).toEqual({ message: NOT_AVAILABLE, retryable: false })
    expect(failureMessage(failure)).toBe(NOT_AVAILABLE)
    // No answer at all, and an answer of the wrong shape, are the same.
    expect(failureOf(null)).toEqual({ message: NOT_AVAILABLE, retryable: false })
  })

  it('MEMBER_NOT_FOUND is the server\'s own sentence (ME-13)', () => {
    const failure = failureOf(refusal('MEMBER_NOT_FOUND'))
    expect(failureMessage(failure)).toBe('That member is no longer on record.')
  })

  it('a server that did not answer is never an empty history -- the approved sentence', () => {
    const network = { kind: 'network', status: 0, retryable: true, body: null, message: 'The server could not be reached.' }
    expect(failureOf(network)).toBe(network)
    expect(failureMessage(network)).toBe(NO_ANSWER)
    expect(NO_ANSWER).toMatch(/^The server did not answer, so this is not an empty history\./)
    // A gateway's 503 with no envelope is the same failure.
    expect(failureMessage({ kind: 'http', status: 503, retryable: true, body: null })).toBe(NO_ANSWER)
  })

  it('a page that no longer exists: back to the first, in the server\'s words', () => {
    expect(pageGoneSentence(refusal('HISTORY_PAGE_INVALID')))
      .toBe('That page of the history does not exist. The first page is shown instead.')
    expect(pageGoneSentence(null)).toBe('That page of the history does not exist. The first page is shown instead.')
  })
})
