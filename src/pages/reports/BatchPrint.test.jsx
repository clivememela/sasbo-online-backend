// @vitest-environment jsdom -- the organisers' batch print: what is typed, what is asked, what prints (October build)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { BatchPrint } from './BatchPrint'
import { PERMISSIONS } from '../../auth/permissions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { deferred } from '../../test/deferred'
import { expectNoA11yViolations } from '../../test/assertions'
import { monthEndBody } from '../../test/msw/monthEndExamples'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { locationService } from '../../services/locationService'
import { setupUser } from '../../test/user'
import { REPO_ROOT } from '../../test/apiContractScan.js'

/**
 * 🗣️ 5 OCT SHOW & TELL: "if I'm going to have these 20 Rs for the month, let me put all 20 numbers in at once, click
 * print, and then it prints everything. And not like 1 by one" -- and "separate them by bank ... if it's a new bank,
 * start it [on a new page]".
 *
 * So these hold: the numbers are read as typed, before anything is asked; ONE request carries them all, each once; a
 * number that is no workplace is named and the rest still print; the listings stand in sections a bank each, every
 * section beginning a new sheet with its own heading (the bank, the day, the organiser's area); the members under the
 * branches need MEMBERS_VIEW, say so otherwise, and are read in ONE request for the batch (October build review); and
 * Print waits until every branch is ready. Every answer is the server's golden one or built from its invented
 * workplaces -- nothing here is SASBO's.
 */

const GOLDEN = monthEndBody('GET /api/workplaces/listings')
const [MAIN_STREET, HARBOUR] = GOLDEN.listings

/** A second branch of the invented bank, so one bank can hold two branches. */
const HEAD_OFFICE = {
  workplace: { ...MAIN_STREET.workplace, id: 2, raNumber: 900002, name: 'INVENTED HEAD OFFICE' },
  lastVisited: [],
}

/** The three typed bank, insurer, bank -- two banks, the first with two branches. */
const TWO_BANKS = {
  asked: [900001, 900003, 900002], listings: [MAIN_STREET, HARBOUR, HEAD_OFFICE], unknown: [], workplacesServed: 'ALL',
  reason: null,
}

/** Every batch asked for, as the numbers it named; every members read, as the workplaces' ids it named. */
let asked
let membersAsked

/** The server's golden members of the invented main street, as one branch of a batch's answer. */
const GOLDEN_MEMBERS = monthEndBody('GET /api/workplaces/members')
const [MAIN_STREETS_MEMBERS] = GOLDEN_MEMBERS.branches

const listingsAnswer = (body = GOLDEN) => http.get('*/api/workplaces/listings', ({ request }) => {
  asked.push(new URL(request.url).searchParams.get('raNumbers'))
  return HttpResponse.json(body)
})

/**
 * The batch's members as the server answers them: each id asked, a branch with the golden branch's members -- or, for an
 * id in `outOfScope`, marked outside the caller's area with none. `surname` renames the member, to tell answers apart.
 */
const answerMembers = async (request, gate, { outOfScope = [], surname } = {}) => {
  const ids = new URL(request.url).searchParams.get('ids')
  membersAsked.push(ids)
  if (gate) await gate.promise
  const members = MAIN_STREETS_MEMBERS.members.map((member) => (surname ? { ...member, surname } : member))
  return HttpResponse.json({
    ...GOLDEN_MEMBERS,
    unknown: [],
    branches: ids.split(',').map(Number).map((workplaceId) => (outOfScope.includes(workplaceId)
      ? { workplaceId, outOfScope: true, members: [], totalElements: 0 }
      : { ...MAIN_STREETS_MEMBERS, workplaceId, members })),
  })
}

const membersAnswer = (gate, options) => http.get('*/api/workplaces/members', ({ request }) =>
  answerMembers(request, gate, options))

const ORGANISER = {
  id: 7, username: 'invented.organiser', displayName: 'Invented Organiser',
  position: { code: 'ORGANISER', name: 'Organiser' }, permissions: [PERMISSIONS.MEMBERS_VIEW, PERMISSIONS.REPORTS_PRINT],
  scope: { type: 'AREA', codes: ['L'] }, mustChangePassword: false,
}

const signedIn = (ui) => (
  <TestAuthProvider permissions={ORGANISER.permissions} user={ORGANISER}>{ui}</TestAuthProvider>
)

const box = () => screen.getByLabelText('R/A numbers')

/** The numbers pasted, as an organiser pastes a list -- and Show listings. */
async function showListingsFor(user, typed) {
  await user.click(box())
  await user.paste(typed)
  await user.click(screen.getByRole('button', { name: 'Show listings' }))
}

beforeEach(() => {
  asked = []
  membersAsked = []
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(window, 'print').mockImplementation(() => {})
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-10-07T08:00:00+02:00') })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('the numbers, read as typed -- before anything is asked', () => {
  it('says how many will print, which were typed twice, and what is not a number; nothing is asked until Show listings', async () => {
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    expect(screen.getByRole('button', { name: 'Show listings' })).toBeDisabled()
    await user.click(box())
    await user.paste('900001, 900003 900001\nabc')

    expect(screen.getByText(/2 R\/A numbers to print · 900001 typed more than once, printed once · “abc” is not an R\/A number/))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show listings' })).toBeEnabled()
    expect(asked).toEqual([])
  })

  it('🔒 more than fifty is said at the box, and nothing is asked', async () => {
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    await user.click(box())
    await user.paste(Array.from({ length: 51 }, (_, i) => String(1000 + i)).join(' '))

    expect(screen.getByText('Up to 50 R/A numbers can be printed at a time; 51 are typed.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show listings' })).toBeDisabled()
    expect(asked).toEqual([])
  })
})

describe('one request, every number once -- the unknown said, the rest printed', () => {
  it('🔒 5 Oct: asks ONCE for all the numbers, each once, in the order typed -- and names the one not on record', async () => {
    server.use(listingsAnswer())
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    await showListingsFor(user, '900001 900003 900001 900099')

    expect(await screen.findByText('Not on record here: 900099. The other listings are below.')).toBeInTheDocument()
    expect(asked).toEqual(['900001,900003,900099'])
    expect(screen.getAllByRole('article').map((article) => within(article).getByRole('heading', { level: 3 }).textContent))
      .toEqual(['INVENTED MAIN STREET · R/A 900001', 'INVENTED HARBOUR · R/A 900003'])
  })

  it('the workplaces not loaded here say so -- not "none of these numbers"', async () => {
    server.use(listingsAnswer({ asked: [900001], listings: [], unknown: [], workplacesServed: 'NONE', reason: 'NOT_LOADED' }))
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    await showListingsFor(user, '900001')

    expect(await screen.findByText('SASBO’s workplaces are not on record here')).toBeInTheDocument()
    expect(screen.getByText('Not loaded on this demonstration yet.')).toBeInTheDocument()
  })

  it('a search that failed says so, and Try again asks once more', async () => {
    let calls = 0
    server.use(http.get('*/api/workplaces/listings', () => {
      calls += 1
      return calls === 1 ? HttpResponse.json({ message: 'boom' }, { status: 500 }) : HttpResponse.json(GOLDEN)
    }))
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    await showListingsFor(user, '900001 900003')
    expect(await screen.findByText('We could not find those branches.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Try again/ }))
    expect(await screen.findAllByRole('article')).toHaveLength(2)
  })
})

describe('the print layout -- a bank a page', () => {
  it('🔒 5 Oct: a section a bank, in the order typed, each with its heading: the bank, the day printed, the organiser\'s area', async () => {
    server.use(listingsAnswer(TWO_BANKS), membersAnswer())
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    await showListingsFor(user, '900001 900003 900002')
    await screen.findAllByRole('article')

    const banks = [...document.querySelectorAll('.print-area .print-bank')]
    expect(banks.map((bank) => within(bank).getByRole('heading', { level: 2 }).textContent))
      .toEqual(['INVENTED BANK', 'INVENTED INSURER'])
    expect(within(banks[0]).getAllByRole('article').map((article) => within(article).getByRole('heading', { level: 3 })
      .textContent)).toEqual(['INVENTED MAIN STREET · R/A 900001', 'INVENTED HEAD OFFICE · R/A 900002'])
    expect(banks[0].querySelector('.print-bank-header')).toHaveTextContent(
      'Printed 07 Oct 2026 · Organiser\'s area: L · 2 branches')
    expect(banks[1].querySelector('.print-bank-header')).toHaveTextContent('INVENTED INSURER')
  })

  it('🔒 each bank after the first begins a new sheet, on A4 -- the print rules the sections stand on', () => {
    const css = readFileSync(join(REPO_ROOT, 'src', 'assets', 'styles', 'index.css'), 'utf8')
    const print = css.slice(css.indexOf('@media print'))

    expect(print).toMatch(/\.print-bank \+ \.print-bank\s*\{\s*break-before:\s*page;\s*\}/)
    expect(print).toMatch(/\.print-batch\s*\{\s*page:\s*batch-print;\s*\}/)
    expect(print).toMatch(/@page batch-print\s*\{\s*size:\s*A4;/)
  })

  it('without the organiser\'s own area, the heading names the areas of the branches printed', async () => {
    server.use(listingsAnswer(TWO_BANKS))
    const user = setupUser()
    renderWithProviders(<BatchPrint />)

    await showListingsFor(user, '900001 900003 900002')
    await screen.findAllByRole('article')

    expect(document.querySelector('.print-bank .print-bank-header')).toHaveTextContent('Printed 07 Oct 2026 · Area: A')
  })
})

describe('the members at each branch -- MEMBERS_VIEW, or a sentence', () => {
  it('🔒 signed out: every branch says "Sign in to see the members at this branch", nothing is asked, and Print is ready', async () => {
    server.use(listingsAnswer())
    const user = setupUser()
    renderWithProviders(<BatchPrint />, { initialEntries: ['/reports/locations/print'] })

    await showListingsFor(user, '900001 900003')
    await screen.findAllByRole('article')

    const notices = screen.getAllByText(/Sign in to see the members at this branch\./)
    expect(notices).toHaveLength(2)
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0])
      .toHaveAttribute('href', '/login?next=%2Freports%2Flocations%2Fprint')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    const print = await screen.findByRole('button', { name: 'Print listings' })
    expect(print).toBeEnabled()
    await user.click(print)
    expect(window.print).toHaveBeenCalledTimes(1)
    expect(membersAsked).toEqual([])
  })

  it('🔒 signed in with MEMBERS_VIEW: the members under each branch, masked -- and Print waits until they are in', async () => {
    const gate = deferred()
    server.use(listingsAnswer(), membersAnswer(gate))
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    await showListingsFor(user, '900001 900003')
    expect(await screen.findByRole('button', { name: 'Preparing the listings…' })).toBeDisabled()

    gate.resolve()
    expect(await screen.findByRole('button', { name: 'Print listings' })).toBeEnabled()
    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(2)
    expect(within(tables[0]).getByText('Example')).toBeInTheDocument()
    expect(within(tables[0]).getByText('****0042')).toBeInTheDocument()
    expect(screen.queryByText(/Sign in to see the members/)).not.toBeInTheDocument()
    expect(screen.getAllByText('Membership:')[0].nextSibling).toHaveTextContent('1')
  })

  it('🔒 review: ONE members read for the whole batch -- every branch\'s id in it, never a request a branch', async () => {
    server.use(listingsAnswer(TWO_BANKS), membersAnswer())
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    await showListingsFor(user, '900001 900003 900002')
    await waitFor(() => expect(screen.getAllByRole('table')).toHaveLength(3))

    expect(membersAsked).toEqual(['1,3,2'])
  })

  it('a branch outside the organiser\'s area says so, with no Try again -- and still lets the batch print', async () => {
    server.use(listingsAnswer(), membersAnswer(null, { outOfScope: [3] }))
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    await showListingsFor(user, '900001 900003')

    expect(await screen.findByText('This branch is outside your region or area.')).toBeInTheDocument()
    expect(screen.getAllByRole('table')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: /Try again/ })).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Print listings' })).toBeEnabled()
  })

  it('review: the members failing for another reason say so under each branch -- Try again asks once more, and the batch still prints', async () => {
    let calls = 0
    server.use(listingsAnswer(), http.get('*/api/workplaces/members', ({ request }) => {
      calls += 1
      return calls === 1 ? HttpResponse.json({ message: 'boom' }, { status: 500 }) : answerMembers(request)
    }))
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    await showListingsFor(user, '900001 900003')

    expect(await screen.findAllByText('The members at this branch could not be loaded.')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Print listings' })).toBeEnabled()
    await user.click(screen.getAllByRole('button', { name: /Try again/ })[0])

    await waitFor(() => expect(screen.getAllByRole('table')).toHaveLength(2))
    expect(calls).toBe(2)
  })

  it('🔒 review: an answer for listings since replaced is dropped -- never shown under the listings shown now', async () => {
    const first = deferred()
    const second = deferred()
    let calls = 0
    server.use(listingsAnswer(), http.get('*/api/workplaces/members', ({ request }) => {
      calls += 1
      return calls === 1 ? answerMembers(request, first, { surname: 'STALE-ANSWER' }) : answerMembers(request, second)
    }))
    const reads = vi.spyOn(locationService, 'getMembersOf')
    const user = setupUser()
    renderWithProviders(signedIn(<BatchPrint />))

    try {
      await showListingsFor(user, '900001 900003')
      await screen.findByRole('button', { name: 'Preparing the listings…' })
      await user.click(screen.getByRole('button', { name: 'Show listings' }))
      await waitFor(() => expect(membersAsked).toHaveLength(2))

      second.resolve()
      await waitFor(() => expect(screen.getAllByRole('table')).toHaveLength(2))
      first.resolve()
      // The first read settled, and the page has had its turn to take it: awaited after the page's own await of it.
      await reads.mock.results[0].value
      await screen.findByRole('button', { name: 'Print listings' })

      expect(screen.getAllByText('Example')).toHaveLength(2)
      expect(screen.queryByText('STALE-ANSWER')).not.toBeInTheDocument()
    } finally {
      first.resolve()
      second.resolve()
    }
  })

  it('🔒 review: signed in without REPORTS_PRINT, the page says what is needed -- and asks nothing', () => {
    server.use(listingsAnswer())
    renderWithProviders(
      <TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW]} user={{ ...ORGANISER, permissions: [PERMISSIONS.MEMBERS_VIEW] }}>
        <BatchPrint />
      </TestAuthProvider>,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Batch print' })).toBeInTheDocument()
    expect(screen.getByText(/You need permission to print organisers' reports\./)).toBeInTheDocument()
    expect(screen.queryByLabelText('R/A numbers')).not.toBeInTheDocument()
    expect(asked).toEqual([])
  })

  it('a11y: the printed listings, signed out, have no axe violations', async () => {
    server.use(listingsAnswer(TWO_BANKS))
    const user = setupUser()
    const { container } = renderWithProviders(<BatchPrint />)
    await showListingsFor(user, '900001 900003 900002')
    await screen.findByRole('button', { name: 'Print listings' })

    await expectNoA11yViolations(container)
  })

  it('a11y: the printed listings with their members, signed in, have no axe violations', async () => {
    server.use(listingsAnswer(TWO_BANKS), membersAnswer())
    const user = setupUser()
    const { container } = renderWithProviders(signedIn(<BatchPrint />))
    await showListingsFor(user, '900001 900003 900002')
    await waitFor(() => expect(screen.getAllByRole('table')).toHaveLength(3))

    await expectNoA11yViolations(container)
  })
})
