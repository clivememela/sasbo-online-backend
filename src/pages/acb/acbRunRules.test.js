import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../../test/apiContractScan'
import {
  CANCELLABLE, DEBIT_DAYS, DECIDABLE, DECISION_LABEL, DECLINE_REASON_LABEL, EXCLUSION_LABEL, LETTER_STATUS_LABEL,
  LETTER_STATUS, LETTER_TEMPLATE_LABEL, REFUSAL, RUN_STATUS, RUN_STATUS_LABEL, UNPAID_REASON_LABEL, asPage, calendarNotice,
  debitDayReplaced, debitsRuns, declinedMonthWarning, maskedAccount, monthLabel, ordinal, pollRun, preparableMonths,
  rateNote,
} from './acbRunRules'

/**
 * The Runs screen's rules, and its vocabulary held to the SERVER'S (`api-vocabulary.json`, `acb`).
 * A state or reason the server adds reddens this file instead of reaching the screen as a word
 * nobody wrote a label for.
 */

const acb = committedVocabulary().acb

describe('the run vocabulary is the server\'s', () => {
  it('every run state the server publishes, and no other, with a label for each', () => {
    expect(Object.values(RUN_STATUS)).toEqual(acb.runStatuses)
    expect(Object.keys(RUN_STATUS_LABEL)).toEqual(acb.runStatuses)
  })

  it('every reason the bank can decline a file for, as the server lists them, has its words (D29)', () => {
    expect(Object.keys(DECLINE_REASON_LABEL)).toEqual(acb.declineReasons)
  })

  it('every exclusion reason the server publishes has the words an administrator reads', () => {
    expect(Object.keys(EXCLUSION_LABEL)).toEqual(acb.exclusionReasons.map((r) => r.name))
    // SNAPSHOT_INCOMPLETE covers several fields -- a membership number, a surname, and since 00.5 the account type
    // (RunPreparer.snapshotProblem) -- so its label must name none of them: the row's detail does. It once read
    // "Membership number or surname missing" beside a detail of "no account type" (the presentation arc, 2026-09-22).
    expect(EXCLUSION_LABEL.SNAPSHOT_INCOMPLETE).not.toMatch(/membership|surname|account type/i)
  })

  it('the refusal codes the screen branches on are codes the server gives', () => {
    const published = acb.refusalCodes.map((c) => c.code)
    expect(published).toEqual(expect.arrayContaining(Object.values(REFUSAL)))
  })

  it('every unpaid reason, decision, letter status and letter the server publishes has its words (04.1)', () => {
    expect(Object.keys(UNPAID_REASON_LABEL)).toEqual(acb.unpaidReasons)
    expect(Object.keys(DECISION_LABEL)).toEqual(acb.unpaidDecisions)
    expect(Object.keys(LETTER_STATUS_LABEL)).toEqual(acb.letterStatuses)
    expect(Object.values(LETTER_STATUS)).toEqual(acb.letterStatuses)
    expect(Object.keys(LETTER_TEMPLATE_LABEL)).toEqual(acb.letterTemplates)
  })

  it('only a sent run whose month is not closed takes decisions -- what the server\'s returns allow', () => {
    expect([...DECIDABLE].sort()).toEqual([RUN_STATUS.RETURNED, RUN_STATUS.SUBMITTED])
  })

  it('only a proposal can be cancelled -- what the server\'s state table allows', () => {
    expect([...CANCELLABLE].sort()).toEqual([RUN_STATUS.APPROVED, RUN_STATUS.PREPARED])
  })
})

describe('monthLabel and preparableMonths', () => {
  it('names a month as an administrator says it, and passes anything else through', () => {
    expect(monthLabel('2026-10')).toBe('October 2026')
    expect(monthLabel('2026-1')).toBe('2026-1')
    expect(monthLabel(undefined)).toBe('')
  })

  it('offers this month and the next -- across a year end too', () => {
    expect(preparableMonths(new Date(2026, 8, 19)).map((m) => m.value)).toEqual(['2026-09', '2026-10'])
    expect(preparableMonths(new Date(2026, 11, 31)).map((m) => m.value)).toEqual(['2026-12', '2027-01'])
    expect(preparableMonths(new Date(2026, 11, 31))[1].label).toBe('January 2027')
  })
})

describe('calendarNotice (D30, SD3-G)', () => {
  const CALENDAR = { name: 'za-statutory', knownUntil: '2027-12-31', endingSoon: false, year: 2027, holidays: [] }

  it('names the date the calendar ends on once the server says it is near', () => {
    expect(calendarNotice({ ...CALENDAR, endingSoon: true })).toBe(
      'The holiday calendar is known until 31 Dec 2027. A month after that cannot be prepared until the calendar is extended.')
  })

  it('the must-pass twin: a calendar far from its end says nothing', () => {
    expect(calendarNotice(CALENDAR)).toBeNull()
  })

  it('no calendar on record says that nothing can be prepared -- before anyone tries', () => {
    expect(calendarNotice({ ...CALENDAR, name: 'none', knownUntil: null, endingSoon: true }))
      .toBe('No holiday calendar is on record, so no month can be prepared yet.')
  })

  it('anything that is not a calendar -- the route smoke\'s [] among them -- says nothing', () => {
    expect(calendarNotice([])).toBeNull()
    expect(calendarNotice(null)).toBeNull()
    expect(calendarNotice('za-statutory')).toBeNull()
  })
})

describe('declinedMonthWarning (SD3-H)', () => {
  const DECLINED = { id: 40, actionMonth: '2026-10', status: 'DECLINED', declinedReason: 'WRONG_DEBIT_DATE', calendarAgrees: true }

  it('a wrong-date decline with the calendar unchanged says the new run would carry the same dates', () => {
    expect(declinedMonthWarning([DECLINED], '2026-10')).toMatch(/has not changed since — this run would carry the same dates/)
  })

  it('a wrong-date decline with the calendar changed since says the dates can differ', () => {
    expect(declinedMonthWarning([{ ...DECLINED, calendarAgrees: false }], '2026-10')).toMatch(/has changed since, so this run's dates can differ/)
  })

  it('the must-pass twins: another month, another reason, or no decline at all says nothing', () => {
    expect(declinedMonthWarning([DECLINED], '2026-11')).toBeNull()
    expect(declinedMonthWarning([{ ...DECLINED, declinedReason: 'LAYOUT' }], '2026-10')).toBeNull()
    expect(declinedMonthWarning([{ ...DECLINED, status: 'CANCELLED' }], '2026-10')).toBeNull()
    expect(declinedMonthWarning(undefined, '2026-10')).toBeNull()
  })
})

describe('rateNote (Q43)', () => {
  const RATES = {
    rates: [
      { effectiveFrom: '2026-01-01', amount: 174, source: 'From January' },
      { effectiveFrom: '2027-01-01', amount: 185, source: 'AGM' },
    ],
    januaryNotice: null,
  }

  it('names what the month charges, since when, and the next dated rate', () => {
    expect(rateNote(RATES, '2026-10')).toMatch(/^October 2026 charges R\s?174,00 a member — the rate since January 2026\. From January 2027: R\s?185,00\.$/)
  })

  it('a month on the new rate names it, and nothing after it', () => {
    expect(rateNote(RATES, '2027-01')).toMatch(/^January 2027 charges R\s?185,00 a member — the rate since January 2027\.$/)
  })

  it('the must-pass twins: no rate reaching the month, or no list at all, says nothing', () => {
    expect(rateNote(RATES, '2025-12')).toBeNull()
    expect(rateNote([], '2026-10')).toBeNull()
    expect(rateNote(null, '2026-10')).toBeNull()
  })
})

describe('asPage', () => {
  it('reads a server page', () => {
    expect(asPage({ content: [{ id: 1 }], page: 2, totalPages: 3, totalElements: 25 }))
      .toEqual({ content: [{ id: 1 }], page: 2, totalPages: 3, totalElements: 25 })
  })

  it('turns anything else -- the route smoke\'s [] among them -- into an empty page, never a crash', () => {
    for (const odd of [[], null, undefined, {}, { content: 'x' }]) {
      expect(asPage(odd)).toEqual({ content: [], page: 0, totalPages: 0, totalElements: 0 })
    }
  })
})

describe('maskedAccount', () => {
  it('shows the last four the server sent, or a dash', () => {
    expect(maskedAccount('9911')).toBe('•••• 9911')
    expect(maskedAccount(null)).toBe('—')
  })
})

describe('pollRun', () => {
  const answers = (...statuses) => {
    const reads = []
    const read = async (id) => {
      reads.push(id)
      return { id, status: statuses[Math.min(reads.length - 1, statuses.length - 1)] }
    }
    return { read, reads }
  }

  it('re-reads until the run leaves PREPARING, and answers the settled run', async () => {
    const { read, reads } = answers(RUN_STATUS.PREPARING, RUN_STATUS.PREPARING, RUN_STATUS.PREPARED)
    const settled = await pollRun(read, 41, { delays: [0, 0, 0, 0, 0] })
    expect(settled.status).toBe(RUN_STATUS.PREPARED)
    expect(reads).toEqual([41, 41, 41])
  })

  it('gives up after its last delay, and says so with undefined -- the screen then says "check back"', async () => {
    const { read, reads } = answers(RUN_STATUS.PREPARING)
    expect(await pollRun(read, 41, { delays: [0, 0, 0] })).toBeUndefined()
    expect(reads).toHaveLength(3)
  })

  it('stops reading the moment the screen is left, and answers null', async () => {
    const { read, reads } = answers(RUN_STATUS.PREPARING)
    let left = false
    const polling = pollRun(read, 41, { delays: [0, 0, 0, 0], stopped: () => left })
    left = true
    expect(await polling).toBeNull()
    expect(reads).toEqual([])
  })

  it('a failed read rejects -- the screen says what it could not confirm', async () => {
    const failing = async () => { throw new Error('lost') }
    await expect(pollRun(failing, 41, { delays: [0] })).rejects.toThrow('lost')
  })
})

describe('debitsRuns (04.1)', () => {
  const run = (id, actionMonth, status) => ({ id, actionMonth, status })

  it('takes the newest run whose unpaids still take a decision, and the newest proposal after it', () => {
    const runs = [run(42, '2026-11', 'CANCELLED'), run(41, '2026-10', 'PREPARED'), run(40, '2026-09', 'RETURNED'),
      run(39, '2026-08', 'CLOSED')]
    expect(debitsRuns(runs)).toEqual({ returned: runs[2], next: runs[1] })
  })

  it('a proposal older than the returned run is not its next run; a closed month takes no decisions', () => {
    expect(debitsRuns([run(40, '2026-09', 'SUBMITTED'), run(38, '2026-07', 'PREPARED')]).next).toBeNull()
    expect(debitsRuns([run(39, '2026-08', 'CLOSED')])).toEqual({ returned: null, next: null })
  })

  it('anything that is not a list -- the route smoke\'s [] among them -- is no runs, never a crash', () => {
    expect(debitsRuns(undefined)).toEqual({ returned: null, next: null })
    expect(debitsRuns([])).toEqual({ returned: null, next: null })
  })
})

describe('the debit day (Q42)', () => {
  it('is any day from the 20th to the 31st, and each is said as a day of the month', () => {
    expect(DEBIT_DAYS).toEqual([20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31])
    expect(DEBIT_DAYS.map(ordinal)).toEqual(['20th', '21st', '22nd', '23rd', '24th', '25th', '26th', '27th', '28th',
      '29th', '30th', '31st'])
    expect([1, 2, 3, 11, 12, 13, 111].map(ordinal)).toEqual(['1st', '2nd', '3rd', '11th', '12th', '13th', '111th'])
  })

  it('a day the server kept other than the one chosen is said (the net A67 left behind)', () => {
    expect(debitDayReplaced('28', 25)).toBe('The debit day was saved as the 25th, not the 28th chosen. '
      + 'Check the member’s banking details before the next debit order run.')
  })

  it('the must-pass twins: the day kept as chosen, or no day chosen, says nothing', () => {
    expect(debitDayReplaced('25', 25)).toBeNull()
    expect(debitDayReplaced('', 25)).toBeNull()
    expect(debitDayReplaced('25', null)).toBeNull()
  })
})
