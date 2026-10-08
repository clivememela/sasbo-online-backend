import { describe, expect, it } from 'vitest'

import { ERROR_KINDS } from '../services/parseApiError'
import { monthEndBody } from '../test/msw/monthEndExamples'
import {
  NOT_AVAILABLE, WORKPLACE_SEARCH, memberWorkplace, notHere, workplaceFill, workplaceResults, workplaceSource,
} from './workplaces'

/**
 * THE MEMBER'S WORKPLACE, AS RULES (month end `01.3`) -- read apart from the screen, against the server's own golden
 * answers (`month-end-examples.json`), which hold invented lists and invented workplaces only.
 */
const STATUS = monthEndBody('GET /api/reference/status')
const EMPLOYERS = monthEndBody('GET /api/reference/employers')
const PAGE = monthEndBody('GET /api/workplaces')
const [HARBOUR, HEAD_OFFICE] = PAGE.content

describe('what the control can offer (the status read and the employers)', () => {
  it('ready, with the employers and where each list was read from', () => {
    const source = workplaceSource(STATUS, EMPLOYERS)
    expect(source.employers.map((row) => row.name)).toEqual(['INVENTED BANK', 'INVENTED INSURER'])
    expect(source.employersFrom).toBe('As printed on 31 August 2026 — not yet confirmed by SASBO')
    expect(source.workplacesFrom).toBe('As printed on 31 August 2026 — not yet confirmed by SASBO')
  })

  it('not available where the server says none is loaded here -- in the server\'s word (ME-19)', () => {
    const notLoaded = { ...STATUS, workplaces: { ...STATUS.workplaces, rows: 0, printedOn: null, reason: 'NOT_LOADED' } }
    expect(workplaceSource(notLoaded, EMPLOYERS))
      .toEqual({ why: 'SASBO’s workplaces: Not loaded on this demonstration yet.' })
  })

  it('not available where there is no employer to choose, and why', () => {
    const none = { ...EMPLOYERS, rows: [], reason: 'NOT_CONFIRMED' }
    expect(workplaceSource(STATUS, none))
      .toEqual({ why: 'SASBO’s employers: Not confirmed by SASBO yet.' })
  })

  it('🔴 MV-36: an answer of the wrong shape is NOT AVAILABLE, never an empty control -- the fixture answers []', () => {
    expect(workplaceSource([], EMPLOYERS)).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
    expect(workplaceSource(STATUS, [])).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
    expect(workplaceSource({ lists: [] }, EMPLOYERS)).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
  })
})

describe('a page of the search', () => {
  it('is a page when it is one', () => {
    expect(workplaceResults(PAGE)).toEqual({ page: PAGE })
  })

  it('an empty page with no reason is an ANSWER -- the search found none', () => {
    const none = { ...PAGE, content: [], totalElements: 0, totalPages: 0 }
    expect(workplaceResults(none)).toEqual({ page: none })
  })

  it('an empty page with a reason is not available, and the wrong shape is too', () => {
    expect(workplaceResults({ ...PAGE, content: [], reason: 'NOT_LOADED' }))
      .toEqual({ why: 'SASBO’s workplaces: Not loaded on this demonstration yet.' })
    expect(workplaceResults([])).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
  })

  it('asks from the second character, after a pause, a page at a time, never past the server\'s ceiling', () => {
    expect(WORKPLACE_SEARCH.from).toBe(2)
    expect(WORKPLACE_SEARCH.pauseMs).toBeGreaterThan(0)
    expect(WORKPLACE_SEARCH.size).toBeLessThanOrEqual(200)
    expect(WORKPLACE_SEARCH.maxLength).toBe(100)
  })
})

describe('the member\'s workplace from the answer for one member', () => {
  it('the lineage, a none, or not available', () => {
    expect(memberWorkplace({ id: 1, workplaceId: 3, workplace: HARBOUR })).toEqual({ workplace: HARBOUR })
    expect(memberWorkplace({ id: 1, workplaceId: null })).toEqual({ none: true })
    expect(memberWorkplace([])).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
    // An id and no lineage is not a member's answer as the server writes it.
    expect(memberWorkplace({ id: 1, workplaceId: 3 })).toEqual({ why: NOT_AVAILABLE.UNREADABLE })
  })

  it('a 404 is a read the server does not have yet -- and nothing else is (MD58, MV-53)', () => {
    expect(notHere({ kind: ERROR_KINDS.HTTP, status: 404 })).toBe(true)
    expect(notHere({ kind: ERROR_KINDS.HTTP, status: 503 })).toBe(false)
    expect(notHere({ kind: ERROR_KINDS.NETWORK, status: 0 })).toBe(false)
    expect(notHere(undefined)).toBe(false)
  })
})

/**
 * 🔑 THE OFFER'S RULE -- "where the names agree". The form's own options, matched trimmed and case aside, and never by
 * likeness; the form's spelling is what is filled; a field somebody chose is never offered.
 */
describe('the offer: which bank fields a workplace may fill, and with what', () => {
  const BANKS = ['Invented Bank', 'Other Invented Bank']
  const BRANCHES = { 'Invented Bank': ['Invented Head Office', 'Invented Harbour'], 'Other Invented Bank': ['Rosebank'] }
  const branchesFor = (bank) => BRANCHES[bank] ?? []
  const offer = (workplace, form = {}, free = { bankName: true, branch: true }) =>
    workplaceFill(workplace, { banks: BANKS, branchesFor, form: { bankName: '', branch: '', ...form }, free })

  it('both agree: both are offered, in the FORM\'s spelling rather than SASBO\'s', () => {
    expect(offer(HEAD_OFFICE)).toEqual({ bankName: 'Invented Bank', branch: 'Invented Head Office' })
  })

  it('an employer the form does not offer as a bank is not offered -- and nor is a branch of no bank', () => {
    // INVENTED HARBOUR's employer is INVENTED INSURER: no bank this form offers.
    expect(offer(HARBOUR)).toEqual({})
  })

  it('the employer as PRINTED is read where the workplace has no employer on SASBO\'s list', () => {
    const unlisted = { ...HEAD_OFFICE, employer: null, employerId: null, employerPrinted: '  invented bank ' }
    expect(offer(unlisted)).toEqual({ bankName: 'Invented Bank', branch: 'Invented Head Office' })
  })

  it('never by likeness: a near miss is simply not offered', () => {
    expect(offer({ ...HEAD_OFFICE, employer: 'INVENTED BANK LTD' })).toEqual({})
    expect(offer({ ...HEAD_OFFICE, name: 'INVENTED HEAD OFFICE 2' })).toEqual({ bankName: 'Invented Bank' })
  })

  it('🔴 a field the administrator chose is not offered, and the branch is judged against the bank the form HOLDS', () => {
    // Bank Where Employed chosen by hand: only the branch is offered, as that bank offers it.
    expect(offer(HEAD_OFFICE, { bankName: 'Invented Bank' }, { bankName: false, branch: true }))
      .toEqual({ branch: 'Invented Head Office' })
    // Another bank chosen by hand: it is not overwritten, and that bank has no such branch -- nothing is offered.
    expect(offer(HEAD_OFFICE, { bankName: 'Other Invented Bank' }, { bankName: false, branch: true })).toEqual({})
    // A branch chosen by hand is not overwritten either.
    expect(offer(HEAD_OFFICE, { branch: 'Invented Harbour' }, { bankName: true, branch: false }))
      .toEqual({ bankName: 'Invented Bank' })
  })

  it('the must-pass: a FREE field holding another value is offered, and one already agreeing is not offered again', () => {
    // Free because an earlier offer filled it: a newer workplace may fill it again.
    expect(offer(HEAD_OFFICE, { bankName: 'Invented Bank', branch: 'Invented Harbour' }))
      .toEqual({ branch: 'Invented Head Office' })
    expect(offer(HEAD_OFFICE, { bankName: 'Invented Bank', branch: 'Invented Head Office' })).toEqual({})
  })
})
