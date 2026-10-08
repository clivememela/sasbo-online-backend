// @vitest-environment jsdom -- a workplace's visits, opened from its row on the workplaces' screen (month end A.1)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Workplaces from './Workplaces'
import { expectNoA11yViolations } from '../../test/assertions'
import { deferred } from '../../test/deferred'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * 💾 MONTH END A.1 (MO22) -- THE WORKPLACE SHOWS ITS VISITS, NEWEST FIRST, PAGED. A workplace's R/A number opens them
 * under its row, as a member's number opens the record: the row whole in one line, the visits by day -- or by MONTH
 * for SASBO's own history, which never knew the day -- with what each found, and one sentence for what the read keeps
 * back. Four states, and "none" only for none. Every answer is the server's golden one (MD48) or built on it.
 */

const PAGE = 'GET /api/workplaces/{id}/visits'
const GOLDEN = monthEndBody(PAGE)
const [HARBOUR, HEAD_OFFICE] = monthEndBody('GET /api/workplaces').content
const LIST_ROUTES = ['GET /api/reference/employers', 'GET /api/reference/areas', 'GET /api/reference/committees']
const WITHHELD = 'Who visited and the counts the organiser took are kept, and shown once sign-in exists.'

/** Every question put to the visits' route, as `id?page=&size=`. */
let asked

/** The visits' route answered by `answer(params, query)` -- the golden page unless told -- and its questions kept. */
const visitsAnswer = (answer = () => HttpResponse.json(GOLDEN)) =>
  http.get('*/api/workplaces/:id/visits', ({ params, request }) => {
    const { searchParams } = new URL(request.url)
    asked.push(`${params.id}?page=${searchParams.get('page')}&size=${searchParams.get('size')}`)
    return answer(params, Object.fromEntries(searchParams))
  })

const opener = (w) => screen.getByRole('button', { name: `${w.raNumber} — the visits to ${w.name}` })
const visitsOf = (w) => screen.queryByRole('region', { name: `Visits to ${w.name}` })
/** The visits as they read: each day -- or month -- with what it found. */
const readings = (w) => {
  const terms = within(visitsOf(w)).queryAllByRole('term')
  return terms.map((dt) => [dt.textContent, dt.nextElementSibling.textContent])
}

async function openHarbour(user) {
  renderWithProviders(<Workplaces />)
  await user.click(await screen.findByRole('button', { name: /^900003 — the visits to INVENTED HARBOUR$/ }))
}

beforeEach(() => {
  asked = []
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(monthEndAnswer('GET /api/workplaces'), ...LIST_ROUTES.map(monthEndAnswer))
})

describe('a workplace\'s visits -- opened from its row', () => {
  it('its R/A number opens them under its row, newest first; pressed again, they close', async () => {
    server.use(visitsAnswer())
    const user = setupUser()
    await openHarbour(user)

    expect(opener(HARBOUR)).toHaveAttribute('aria-expanded', 'true')
    expect(opener(HARBOUR).closest('tr')).toHaveAttribute('aria-current', 'true')
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    expect(asked).toEqual([`${HARBOUR.id}?page=0&size=20`])
    // A day, and SASBO's own history as a MONTH -- never "01 Jul 2026", a day nobody wrote down.
    expect(readings(HARBOUR)).toEqual([
      ['20 Aug 2026', 'Staff complement 40, changed from 38'],
      ['Jul 2026', 'From SASBO’s list'],
    ])
    expect(within(visitsOf(HARBOUR)).queryByText(/01 Jul 2026/)).not.toBeInTheDocument()
    // The row, whole: what the sheet's ladder may have hidden at this width.
    expect(within(visitsOf(HARBOUR)).getByText('INVENTED INSURER · Area B · Committee R01, INVENTED SOUTH COMMITTEE · Staff 15'))
      .toBeInTheDocument()
    expect(within(visitsOf(HARBOUR)).getByText(WITHHELD)).toBeInTheDocument()

    await user.click(opener(HARBOUR))
    expect(opener(HARBOUR)).toHaveAttribute('aria-expanded', 'false')
    expect(visitsOf(HARBOUR)).not.toBeInTheDocument()
  })

  it('the whole row is a pointer\'s target, as the register\'s are -- and one workplace is open at a time', async () => {
    server.use(visitsAnswer())
    const user = setupUser()
    renderWithProviders(<Workplaces />)
    await user.click(await screen.findByText('INVENTED HEAD OFFICE'))

    expect(opener(HEAD_OFFICE)).toHaveAttribute('aria-expanded', 'true')
    await waitFor(() => expect(visitsOf(HEAD_OFFICE)).toHaveAttribute('data-visits', 'ready'))

    await user.click(opener(HARBOUR))
    expect(visitsOf(HEAD_OFFICE)).not.toBeInTheDocument()
    expect(opener(HEAD_OFFICE)).toHaveAttribute('aria-expanded', 'false')
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    expect(asked).toEqual([`${HEAD_OFFICE.id}?page=0&size=20`, `${HARBOUR.id}?page=0&size=20`])
  })

  it('has no accessibility violations with a workplace\'s visits open', async () => {
    server.use(visitsAnswer())
    const user = setupUser()
    const { container } = renderWithProviders(<Workplaces />)
    await user.click(await screen.findByRole('button', { name: /^900003 — the visits to INVENTED HARBOUR$/ }))
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    await screen.findByRole('option', { name: 'INVENTED BANK' })

    await expectNoA11yViolations(container)
  })
})

describe('a workplace\'s visits -- the four states', () => {
  it('loading: it says so -- no visit, and no "No visits on record"', async () => {
    const held = deferred()
    server.use(visitsAnswer(() => held.promise.then(() => HttpResponse.json(GOLDEN))))
    const user = setupUser()
    try {
      await openHarbour(user)
      expect(await within(visitsOf(HARBOUR)).findByText('Loading visits…')).toBeInTheDocument()
      expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'loading')
      expect(readings(HARBOUR)).toEqual([])
      expect(screen.queryByText('No visits on record')).not.toBeInTheDocument()
    } finally {
      held.resolve()
    }
    // The must-pass: the answer replaces it.
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
  })

  it('failed: the visits could not be loaded, with Try again -- never "No visits on record" -- and Try again reads them', async () => {
    let calls = 0
    server.use(visitsAnswer(() => {
      calls += 1
      return calls === 1 ? HttpResponse.json({ status: 503 }, { status: 503 }) : HttpResponse.json(GOLDEN)
    }))
    const user = setupUser()
    await openHarbour(user)

    expect(await within(visitsOf(HARBOUR)).findByText('The visits could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText('No visits on record')).not.toBeInTheDocument()
    expect(screen.queryByText(WITHHELD)).not.toBeInTheDocument()
    await user.click(within(visitsOf(HARBOUR)).getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    expect(calls).toBe(2)
  })

  it('empty: "No visits on record" -- an answer, with what the read keeps back still said', async () => {
    server.use(visitsAnswer(() => HttpResponse.json({ ...GOLDEN, visits: [], totalElements: 0, totalPages: 0 })))
    const user = setupUser()
    await openHarbour(user)

    expect(await within(visitsOf(HARBOUR)).findByText('No visits on record')).toBeInTheDocument()
    expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'empty')
    expect(within(visitsOf(HARBOUR)).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(within(visitsOf(HARBOUR)).getByText(WITHHELD)).toBeInTheDocument()
  })

  it('MV-36: an answer that is not a page of visits is not available -- never an empty list, and no Try again', async () => {
    server.use(visitsAnswer(() => HttpResponse.json([])))
    const user = setupUser()
    await openHarbour(user)

    expect(await within(visitsOf(HARBOUR)).findByText('The visits are not available from this server yet.')).toBeInTheDocument()
    expect(screen.queryByText('No visits on record')).not.toBeInTheDocument()
    expect(within(visitsOf(HARBOUR)).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('WORKPLACE_UNKNOWN is the server\'s sentence, and no retry that could not work', async () => {
    server.use(monthEndRefusal('WORKPLACE_UNKNOWN', PAGE))
    const user = setupUser()
    await openHarbour(user)

    expect(await within(visitsOf(HARBOUR)).findByText(monthEndExamples.refusals.WORKPLACE_UNKNOWN.body.message))
      .toBeInTheDocument()
    expect(within(visitsOf(HARBOUR)).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})

describe('a workplace\'s visits -- what the read keeps back, and its pages', () => {
  it('the must-pass: where the read keeps nothing back, no sentence says it does', async () => {
    server.use(visitsAnswer(() => HttpResponse.json({ ...GOLDEN, withheld: [] })))
    const user = setupUser()
    await openHarbour(user)

    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    expect(screen.queryByText(WITHHELD)).not.toBeInTheDocument()
  })

  it('one page has no pager; more than one is paged on the server, page by page', async () => {
    const visit = (id, day) => ({ ...GOLDEN.visits[0], id, visitedOn: `2026-0${day}-15`, complementBefore: null })
    server.use(visitsAnswer((params, { page }) => HttpResponse.json({
      ...GOLDEN, workplaceId: Number(params.id), page: Number(page), totalElements: 21, totalPages: 2,
      visits: page === '0' ? [visit(9, 9)] : [visit(1, 1)],
    })))
    const user = setupUser()
    await openHarbour(user)
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))
    expect(readings(HARBOUR)).toEqual([['15 Sep 2026', 'Staff complement 40']])

    await user.click(within(visitsOf(HARBOUR)).getByRole('button', { name: /Next/ }))

    await waitFor(() => expect(readings(HARBOUR)).toEqual([['15 Jan 2026', 'Staff complement 40']]))
    expect(asked).toEqual([`${HARBOUR.id}?page=0&size=20`, `${HARBOUR.id}?page=1&size=20`])
    expect(within(visitsOf(HARBOUR)).getByText(/Page 2 of 2/)).toBeInTheDocument()
  })

  it('the must-pass: a single page of visits carries no pager', async () => {
    server.use(visitsAnswer())
    const user = setupUser()
    await openHarbour(user)
    await waitFor(() => expect(visitsOf(HARBOUR)).toHaveAttribute('data-visits', 'ready'))

    expect(within(visitsOf(HARBOUR)).queryByRole('button', { name: /Next/ })).not.toBeInTheDocument()
  })
})
