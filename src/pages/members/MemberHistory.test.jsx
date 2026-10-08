// @vitest-environment jsdom -- renders the record's History tab
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MemberHistory from './MemberHistory'
import { fieldLabel } from './Members'
import { deferred } from '../../test/deferred'
import { monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * THE RECORD'S HISTORY TAB -- month end 03.6, as the owner approved its mock on 2026-09-30: one ruled row a change,
 * newest first, *When · Who* on the left and *What changed* with its From over its To on the right; *Not recorded* for
 * who until sign-in; four states, and a failure never read as an empty history.
 *
 * Every answer is the server's golden page (`month-end-examples.json`, MD48) with invented entries in its own shape --
 * the mock's own rows among them -- served by page and size as the server serves them. Every name, number and address
 * is invented. The labels are the record's own (`fieldLabel`, `Members.jsx`), so what a change is called here is what
 * the record calls the field.
 */
const GOLDEN = monthEndBody('GET /api/members/{id}/history')
const entry = (fields) => ({
  ...GOLDEN.content[0], reason: null, code: null, codeName: null, reasonUnderCode: null, note: null, ...fields,
})
const field = (id, what, from, to, saveId, fields = {}) => entry({ kind: 'FIELD', id, what, from, to, saveId, ...fields })

/** The mock's sheet: five rows for seven entries -- a status, a workplace, a save of three, a number kept masked, a capture. */
const MOCK = [
  entry({
    kind: 'STATUS', id: 1, changedAt: '2026-09-29T08:14:00Z', what: 'status', from: 'Active', to: 'Resigned',
    reason: 'DELETION_CODE', code: 2, codeName: 'Resigned Society', reasonUnderCode: 'INVENTED: MOVED AWAY', saveId: null,
    note: 'Invented: letter of resignation received by email.',
  }),
  field(6, 'workplace', '900002 INVENTED HEAD OFFICE', '900001 INVENTED MAIN STREET', 'save-6', { changedAt: '2026-09-14T13:02:00Z' }),
  field(5, 'phone', '+27 60 555 0101', '+27 60 555 0188', 'save-3', { changedAt: '2026-08-27T07:31:00Z' }),
  field(4, 'personalEmail', null, 'synthetic.member@example.invalid', 'save-3', { changedAt: '2026-08-27T07:31:00Z' }),
  field(3, 'address', '12 Invented Road, Invented Park', '4 Invented Lane, Invented Heights', 'save-3', { changedAt: '2026-08-27T07:31:00Z' }),
  field(2, 'identityNumber', '****4081', '****4087', 'save-2', { changedAt: '2026-08-05T09:47:00Z' }),
  field(1, 'captured', null, 'M-900417', 'save-1', { changedAt: '2026-08-04T06:52:00Z' }),
]

/** `count` single-field saves, newest first -- enough to page. */
const manyChanges = (count) => Array.from({ length: count }, (_, i) =>
  field(count - i, 'surname', `Invented ${count - i - 1}`, `Invented ${count - i}`, `save-${count - i}`, {
    changedAt: `2026-09-${String(28 - Math.floor(i / 2)).padStart(2, '0')}T08:00:00Z`,
  }))

const MEMBER = { id: 7, membershipNumber: 'M-900417', firstNames: 'Synthetic', surname: 'Testperson', status: 'Resigned' }

/**
 * A history served as the server serves it: a page and its size as asked, a page past the last refused
 * `HISTORY_PAGE_INVALID`, every page carrying the day the history began. `asked` records each read, page and size.
 */
const historyServes = (entries, { historyFrom = GOLDEN.historyFrom, asked = [], refuse = () => false } = {}) =>
  http.get('*/api/members/:id/history', ({ request, params }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page'))
    const size = Number(url.searchParams.get('size'))
    asked.push({ member: params.id, page, size })
    const content = entries.slice(page * size, (page + 1) * size)
    if (refuse({ page, size }) || (!content.length && page > 0)) {
      const refusal = monthEndExamples.refusals.HISTORY_PAGE_INVALID
      return HttpResponse.json(refusal.body, { status: refusal.status })
    }
    return HttpResponse.json({
      ...GOLDEN, content, page, size, totalElements: entries.length, totalPages: Math.ceil(entries.length / size), historyFrom,
    })
  })

const show = (member = MEMBER) => renderWithProviders(<MemberHistory member={member} labelOf={fieldLabel} />)
const sheet = () => screen.getByRole('region', { name: 'History of changes, newest first' })
/** The tab has settled in `state` -- waited for on the tab itself, so no element of the state before it is held. */
const settled = (state) => waitFor(() => expect(sheet()).toHaveAttribute('data-history', state))
const ready = () => settled('ready')
const rows = () => within(sheet()).getAllByRole('listitem')
/** What the sheet's foot says -- the pager's own line, not the loading line above the sheet. */
const pagerText = () => sheet().querySelector('footer > p')
/** The value a term of a change holds -- `From`, `To`, `Reason`, `Note` -- in the change named `what`. */
const valueOf = (row, what, term) => {
  const change = within(row).getByText(what).parentElement
  const dt = [...change.querySelectorAll('dt')].find((node) => node.textContent === term)
  return dt?.nextElementSibling
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('the History tab, ready -- the approved mock\'s sheet', () => {
  it('one ruled row a change, newest first -- five rows for seven entries, and the pager counting ENTRIES', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    expect(rows()).toHaveLength(5)
    // The head names all five, as the plan's sheet does.
    expect(within(sheet()).getByText('When · Who')).toBeInTheDocument()
    expect(within(sheet()).getByText('What changed · From · To')).toBeInTheDocument()
    expect(rows().map((row) => row.querySelector('time').textContent)).toEqual(
      ['2026/09/2910:14', '2026/09/1415:02', '2026/08/2709:31', '2026/08/0511:47', '2026/08/0408:52'],
    )
    expect(rows()[0].querySelector('time')).toHaveAttribute('datetime', '2026-09-29T08:14:00Z')
    expect(pagerText()).toHaveTextContent('Showing 1–7 of 7 · Page 1 of 1')
    expect(within(sheet()).getByRole('button', { name: /Previous/ })).toBeDisabled()
    expect(within(sheet()).getByRole('button', { name: /Next/ })).toBeDisabled()
  })

  it('WHO reads "Not recorded" until sign-in -- never "System"', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    for (const row of rows()) expect(row).toHaveTextContent('Who: Not recorded')
    expect(within(sheet()).queryByText(/system/i)).toBeNull()
  })

  it('a change of status: its chips, SASBO\'s code and name, the reason given under the code as that cell\'s second line, and the note beneath', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    const row = rows()[0]
    expect(within(row).getByText('Account Membership Status')).toBeInTheDocument()
    expect(valueOf(row, 'Account Membership Status', 'From')).toHaveTextContent(/^Active$/)
    expect(valueOf(row, 'Account Membership Status', 'To')).toHaveTextContent(/^Resigned$/)
    const reason = valueOf(row, 'Account Membership Status', 'Reason')
    expect(reason.firstChild.textContent).toBe('Code 2 · Resigned Society')
    const under = within(reason).getByText('INVENTED: MOVED AWAY')
    expect(under.tagName).toBe('SPAN')
    expect(under).toHaveClass('block')
    // The note is beneath the reason -- the next term after it.
    expect(reason.nextElementSibling).toHaveTextContent('Note')
    expect(valueOf(row, 'Account Membership Status', 'Note')).toHaveTextContent('Invented: letter of resignation received by email.')
  })

  it('a change of status with no reason under its code has no second line, and one with no code says its reason in words', async () => {
    server.use(historyServes([
      entry({ kind: 'STATUS', id: 2, what: 'status', from: 'Resigned', to: 'Active', reason: 'REINSTATED', saveId: null }),
      entry({ kind: 'STATUS', id: 1, what: 'status', from: 'Active', to: 'Suspended', reason: 'SUSPENSION_CODE', code: 13, codeName: 'DEBIT RETURNED', saveId: null }),
    ]))
    show()
    await ready()

    expect(valueOf(rows()[0], 'Account Membership Status', 'Reason')).toHaveTextContent(/^Reinstated$/)
    const coded = valueOf(rows()[1], 'Account Membership Status', 'Reason')
    expect(coded).toHaveTextContent(/^Code 13 · DEBIT RETURNED$/)
    expect(coded.querySelector('span')).toBeNull()
    expect(within(sheet()).queryByText('Note')).toBeNull()
  })

  it('a masked number: ••••1234 on screen, "ending 1234" to a screen reader, and why only four digits are kept', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    const row = rows()[3]
    const to = valueOf(row, 'Identity Number', 'To')
    expect(to).toHaveTextContent('••••ending 4087')
    expect(within(to).getByText('••••')).toHaveAttribute('aria-hidden', 'true')
    expect(within(to).getByText('ending')).toHaveClass('sr-only')
    expect(valueOf(row, 'Identity Number', 'From')).toHaveTextContent('••••ending 4081')
    expect(within(row).getAllByText('Only the last four digits are kept.')).toHaveLength(1)
    // Said once, under the new value.
    expect(within(to).getByText('Only the last four digits are kept.')).toBeInTheDocument()
    // Nothing else on the sheet says so -- the sentence belongs to a masked value.
    expect(within(sheet()).getAllByText('Only the last four digits are kept.')).toHaveLength(1)
  })

  it('a save of several fields is ONE row that says so, each field by the record\'s own label -- a missing value "Not on file"', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    const row = rows()[2]
    expect(row.querySelector('[data-history-save]')).toHaveTextContent('3 changes in one save')
    expect(within(row).getAllByRole('term').filter((dt) => dt.textContent === 'From')).toHaveLength(3)
    expect(valueOf(row, 'Cellphone Number', 'To')).toHaveTextContent('+27 60 555 0188')
    expect(valueOf(row, 'Personal Email', 'From')).toHaveTextContent(/^Not on file$/)
    expect(valueOf(row, 'Residential Address', 'To')).toHaveTextContent('4 Invented Lane, Invented Heights')
    // Tabular figures for a number, as the mock sets them -- never for words, where this font spaces out ":" and ".".
    expect(within(valueOf(row, 'Cellphone Number', 'To')).getByText('+27 60 555 0188')).toHaveClass('tabular-nums')
    expect(valueOf(row, 'Residential Address', 'To').querySelector('.tabular-nums')).toBeNull()
    expect(valueOf(rows()[0], 'Account Membership Status', 'Note').querySelector('.tabular-nums')).toBeNull()
    expect(valueOf(rows()[0], 'Account Membership Status', 'Note')).not.toHaveClass('tabular-nums')
    // A single field is a change, not a save.
    for (const other of [rows()[1], rows()[3]]) expect(other.querySelector('[data-history-save]')).toBeNull()
  })

  it('🔍 MO24: an e-mail address breaks after its "@" and its dots -- never mid-word', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    const value = valueOf(rows()[2], 'Personal Email', 'To')
    expect(value).toHaveTextContent(/^synthetic\.member@example\.invalid$/)
    // The opportunities a line may break at, marked: one after each "@" and ".", and none inside a word. jsdom lays no
    // line out -- at 390 the review saw "synthetic.member@exampl" over "e.invalid".
    const marked = [...value.querySelector('span').childNodes]
      .map((node) => (node.nodeName === 'WBR' ? '|' : node.textContent)).join('')
    expect(marked).toBe('synthetic.|member@|example.|invalid')
    // The must-pass: a value that is no address breaks only where its words do.
    expect(valueOf(rows()[2], 'Residential Address', 'To').querySelector('wbr')).toBeNull()
  })

  it('a workplace: its name over its R/A number, old and new', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    const from = valueOf(rows()[1], 'Workplace', 'From')
    const to = valueOf(rows()[1], 'Workplace', 'To')
    expect(from.firstChild.textContent).toBe('INVENTED HEAD OFFICE')
    expect(within(from).getByText('R/A 900002')).toHaveClass('block')
    expect(to.firstChild.textContent).toBe('INVENTED MAIN STREET')
    expect(within(to).getByText('R/A 900001')).toHaveClass('block')
    expect(within(sheet()).queryByText(/900001 INVENTED/)).toBeNull()
  })

  it('a capture is one line -- "Captured as" the membership number', async () => {
    server.use(historyServes(MOCK))
    show()
    await ready()

    expect(rows()[4]).toHaveTextContent('Captured as M-900417')
    expect(within(rows()[4]).queryByText('From')).toBeNull()
  })

  it('names each field as the record does -- the structure seats the trail now records among them', async () => {
    server.use(historyServes([
      field(4, 'isShopSteward', 'false', 'true', 'save-9'),
      field(3, 'shopStewardElectionDate', null, '2026-03-14', 'save-9'),
      field(2, 'becCommitteeCode', null, 'G01', 'save-9'),
      field(1, 'cosatuPositions', 'PEC', 'PEC,Treasurer', 'save-9'),
    ]))
    show()
    await ready()

    const row = rows()[0]
    expect(valueOf(row, 'Shop Steward', 'From')).toHaveTextContent(/^No$/)
    expect(valueOf(row, 'Shop Steward', 'To')).toHaveTextContent(/^Yes$/)
    expect(valueOf(row, 'Shop Steward Election Date', 'To')).toHaveTextContent(/^2026\/03\/14$/)
    expect(valueOf(row, 'Branch Executive Committee', 'To')).toHaveTextContent(/^G01$/)
    expect(valueOf(row, 'COSATU Positions', 'To')).toHaveTextContent(/^PEC, Treasurer$/)
  })

  it('the server\'s golden page, as it is: a field changed and the capture', async () => {
    server.use(historyServes(GOLDEN.content))
    show()
    await ready()

    expect(rows()).toHaveLength(2)
    expect(valueOf(rows()[0], 'Cell Tel', 'To')).toHaveTextContent('+27 60 555 0002')
    expect(rows()[1]).toHaveTextContent('Captured as M-900001')
    expect(pagerText()).toHaveTextContent('Showing 1–2 of 2 · Page 1 of 1')
  })
})

describe('the History tab\'s other three states', () => {
  it('loading: the head stays, a line says so, three placeholder rows, and the pager says "Loading changes…"', async () => {
    const held = deferred()
    server.use(http.get('*/api/members/:id/history', async () => {
      await held.promise
      return HttpResponse.json({ ...GOLDEN, content: MOCK, totalElements: MOCK.length })
    }))
    show()
    try {
      expect(sheet()).toHaveAttribute('data-history', 'loading')
      expect(within(sheet()).getByText('When · Who')).toBeInTheDocument()
      expect(within(sheet()).getByRole('status')).toHaveTextContent('Loading…')
      expect(sheet().querySelectorAll('ol[aria-hidden="true"] > li')).toHaveLength(3)
      expect(pagerText()).toHaveTextContent('Loading changes…')
      expect(within(sheet()).getByRole('button', { name: /Next/ })).toBeDisabled()
    } finally {
      held.resolve()
    }
    await ready()
    expect(rows()).toHaveLength(5)
  })

  it('FAILED is never an empty history: the approved sentence, Try again, the Details -- and trying again reads it again', async () => {
    let asks = 0
    server.use(http.get('*/api/members/:id/history', () => {
      asks += 1
      return asks === 1 ? HttpResponse.error() : HttpResponse.json({ ...GOLDEN, content: MOCK, totalElements: MOCK.length })
    }))
    const user = setupUser()
    show()

    const alert = await within(sheet()).findByRole('alert')
    expect(sheet()).toHaveAttribute('data-history', 'error')
    expect(alert).toHaveTextContent('The history could not be loaded.')
    expect(alert).toHaveTextContent('The server did not answer, so this is not an empty history.')
    expect(within(alert).getByText('Details')).toBeInTheDocument()
    expect(within(sheet()).queryByText(/No changes recorded/)).toBeNull()
    expect(within(sheet()).queryAllByRole('listitem')).toHaveLength(0)
    expect(pagerText()).toHaveTextContent('Could not load changes')

    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    await ready()
    expect(asks).toBe(2)
    expect(rows()).toHaveLength(5)
  })

  it('a server that failed says so in its own words, with its details and a retry', async () => {
    server.use(http.get('*/api/members/:id/history', () => HttpResponse.json(
      { timestamp: '2026-09-30T08:00:00', status: 500, error: 'Internal Server Error', message: 'Something went wrong on our side. Quote reference abc12345.', path: '/api/members/7/history' },
      { status: 500 },
    )))
    show()

    const alert = await within(sheet()).findByRole('alert')
    expect(alert).toHaveTextContent('The history could not be loaded.')
    expect(alert).toHaveTextContent('Something went wrong on our side. Quote reference abc12345.')
    expect(alert).toHaveTextContent('HTTP 500')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('MEMBER_NOT_FOUND in the server\'s sentence, and no retry -- the member is gone, and asking again will not bring them back', async () => {
    server.use(monthEndRefusal('MEMBER_NOT_FOUND', 'GET /api/members/{id}/history'))
    show()

    const alert = await within(sheet()).findByRole('alert')
    expect(alert).toHaveTextContent('The history could not be loaded.')
    expect(alert).toHaveTextContent(monthEndExamples.refusals.MEMBER_NOT_FOUND.body.message)
    expect(within(alert).queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('an answer of the wrong shape -- the shared fixture\'s [] -- and a server with no such route are NOT AVAILABLE, never empty', async () => {
    server.use(http.get('*/api/members/:id/history', () => HttpResponse.json([])))
    const { unmount } = show()
    let alert = await within(sheet()).findByRole('alert')
    expect(alert).toHaveTextContent('The history is not available from this server yet.')
    expect(within(alert).queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(within(sheet()).queryByText(/No changes recorded/)).toBeNull()
    unmount()

    server.use(http.get('*/api/members/:id/history', () => HttpResponse.json(
      { timestamp: '2026-09-30T08:00:00', status: 404, error: 'Not Found', path: '/api/members/7/history' }, { status: 404 },
    )))
    show()
    alert = await within(sheet()).findByRole('alert')
    expect(alert).toHaveTextContent('The history is not available from this server yet.')
    expect(within(alert).queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('EMPTY: "No changes recorded since" the day the history began (ME-7) -- no head, no pager, and nothing that reads as a failure', async () => {
    server.use(historyServes([], { historyFrom: '2026-09-01' }))
    show()

    await settled('empty')
    const empty = within(sheet()).getByRole('status')
    expect(empty).toHaveTextContent('No changes recorded since 01 Sep 2026')
    expect(empty).toHaveTextContent('Changes made before then were not recorded.')
    expect(within(sheet()).queryByText('When · Who')).toBeNull()
    expect(sheet().querySelector('footer')).toBeNull()
    expect(within(sheet()).queryByRole('alert')).toBeNull()
  })

  it('EMPTY with no day the history began: "No changes recorded yet"', async () => {
    server.use(historyServes([], { historyFrom: null }))
    show()

    await settled('empty')
    expect(within(sheet()).getByRole('status')).toHaveTextContent('No changes recorded yet')
  })

  it('a member older than the history also reads "History begins" above the sheet; a member captured since does not', async () => {
    server.use(historyServes(GOLDEN.content, { historyFrom: '2026-09-01' }))
    const { unmount } = show({ ...MEMBER, createdAt: [2019, 4, 1, 9, 0, 0] })
    await ready()
    const begins = within(sheet()).getByText('History begins 01 Sep 2026')
    // Above the sheet: before its head.
    expect(begins.compareDocumentPosition(within(sheet()).getByText('When · Who')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    unmount()

    show({ ...MEMBER, createdAt: '2026-09-01T10:00:00' })
    await ready()
    expect(within(sheet()).queryByText(/History begins/)).toBeNull()
  })
})

describe('paging', () => {
  /** The looks past a page's edge -- each a page of ONE entry, whose page number is its place in the history. */
  const looks = (asked) => asked.filter(({ size }) => size === 1).map(({ page }) => page)
  const pages = (asked) => asked.filter(({ size }) => size === 50).map(({ page }) => page)

  it('turns page by page, the pager counting entries, and the keyboard\'s place moves to the top of the sheet', async () => {
    const asked = []
    server.use(historyServes(manyChanges(53), { asked }))
    const user = setupUser()
    show()
    await ready()
    expect(rows()).toHaveLength(50)
    expect(pagerText()).toHaveTextContent('Showing 1–50 of 53 · Page 1 of 2')
    expect(within(sheet()).getByRole('button', { name: /Previous/ })).toBeDisabled()

    await user.click(within(sheet()).getByRole('button', { name: /Next/ }))
    await waitFor(() => expect(pagerText()).toHaveTextContent('Showing 51–53 of 53 · Page 2 of 2'))
    expect(rows()).toHaveLength(3)
    expect(sheet()).toHaveFocus()
    expect(within(sheet()).getByRole('button', { name: /Next/ })).toBeDisabled()

    await user.click(within(sheet()).getByRole('button', { name: /Previous/ }))
    await waitFor(() => expect(pagerText()).toHaveTextContent('Showing 1–50 of 53 · Page 1 of 2'))
    // Pages of fifty, asked by number; and past each edge where the row there is a save, the one entry beyond it.
    expect(pages(asked)).toEqual([0, 1, 0])
    await waitFor(() => expect(looks(asked)).toEqual([50, 49, 50]))
    // None of these saves runs past an edge, so no page says one does.
    expect(within(sheet()).queryByText(/(next|previous) page\./)).toBeNull()
  })

  it('a page past the last goes back to the first page, and says so -- in the server\'s words, with no Try again', async () => {
    const asked = []
    // The history the first page describes has a second page; asked for, the server says it does not exist.
    server.use(historyServes(manyChanges(53), { asked, refuse: ({ page, size }) => page === 1 && size === 50 }))
    const user = setupUser()
    show()
    await ready()

    await user.click(within(sheet()).getByRole('button', { name: /Next/ }))
    await waitFor(() => expect(within(sheet()).getByText(/The first page is shown instead\./)).toBeInTheDocument())
    await ready()
    expect(within(sheet()).getByText(/The first page is shown instead\./))
      .toHaveTextContent(`${monthEndExamples.refusals.HISTORY_PAGE_INVALID.body.message} The first page is shown instead.`)
    expect(pagerText()).toHaveTextContent('Showing 1–50 of 53 · Page 1 of 2')
    expect(within(sheet()).queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(within(sheet()).queryByRole('alert')).toBeNull()
    expect(pages(asked)).toEqual([0, 1, 0])
  })

  it('a save split across two pages says so on each -- and a save that ends at the edge says nothing', async () => {
    // 52 changes; the 50th and 51st (the last of page one, the first of page two) are one save's two fields.
    const entries = manyChanges(52).map((change, index) =>
      (index === 49 || index === 50 ? { ...change, saveId: 'straddling', changedAt: '2026-09-02T08:00:00Z' } : change))
    const asked = []
    server.use(historyServes(entries, { asked }))
    const user = setupUser()
    show()
    await ready()

    await waitFor(() => expect(rows().at(-1)).toHaveTextContent('Continues on the next page.'))
    expect(rows().at(-1).querySelector('[data-history-save]')).toHaveTextContent('1 change in one save')
    expect(rows()[0]).not.toHaveTextContent(/page\./)
    expect(looks(asked)).toEqual([50])

    await user.click(within(sheet()).getByRole('button', { name: /Next/ }))
    await waitFor(() => expect(pagerText()).toHaveTextContent('Showing 51–52 of 52 · Page 2 of 2'))
    await waitFor(() => expect(rows()[0]).toHaveTextContent('Continued from the previous page.'))
    expect(rows()[0].querySelector('[data-history-save]')).toHaveTextContent('1 change in one save')
    expect(rows()[1]).not.toHaveTextContent(/page\./)
    expect(asked.map(({ page, size }) => `${page}/${size}`)).toEqual(['0/50', '50/1', '1/50', '49/1'])
  })

  it('the rows are shown as soon as the page arrives -- and nothing is said past its edge until that has been read', async () => {
    const entries = manyChanges(52).map((change, index) =>
      (index === 49 || index === 50 ? { ...change, saveId: 'straddling' } : change))
    const held = deferred()
    server.use(historyServes(entries))
    server.use(http.get('*/api/members/:id/history', async ({ request }) => {
      if (new URL(request.url).searchParams.get('size') !== '1') return undefined
      await held.promise
      return undefined
    }))
    show()
    try {
      await ready()
      expect(rows()).toHaveLength(50)
      // The save at the edge is one field on this page, and until the entry beyond is read it is not called a save.
      expect(rows().at(-1).querySelector('[data-history-save]')).toBeNull()
      expect(rows().at(-1)).not.toHaveTextContent(/page\./)
    } finally {
      held.resolve()
    }
    await waitFor(() => expect(rows().at(-1)).toHaveTextContent('Continues on the next page.'))
  })

  it('a save at the edge whose neighbour could not be read MAY run on -- the tab does not claim what it could not check', async () => {
    server.use(historyServes(manyChanges(52)))
    // `server.use` prepends: the look past the edge -- a page of one -- fails, and every other read passes on to the history.
    server.use(http.get('*/api/members/:id/history', ({ request }) => (
      new URL(request.url).searchParams.get('size') === '1' ? HttpResponse.error() : undefined)))
    show()
    await ready()

    await waitFor(() => expect(rows().at(-1)).toHaveTextContent('This save may continue on the next page.'))
    expect(rows().at(-1).querySelector('[data-history-save]')).toHaveTextContent('1 change in one save')
    expect(within(sheet()).queryByRole('alert')).toBeNull()
  })
})

