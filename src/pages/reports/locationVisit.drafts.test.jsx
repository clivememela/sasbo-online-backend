// @vitest-environment jsdom -- the Location Visit form saved as a draft and submitted (October build)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { OrganizerReport } from './OrganizerReport'
import { PERMISSIONS } from '../../auth/permissions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { expectNoA11yViolations } from '../../test/assertions'
import { monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 💾 5 OCT SHOW & TELL: THE VISIT FORMS SAVING AND SUBMITTING. An organiser who may capture visits saves the form as a
 * draft -- the R/A number and what a visit keeps, under the server's names -- saves it again from the version they
 * hold, and submits it; the visit is then on record and the form read-only, to print. My visits lists their drafts and
 * submissions, to open, a page at a time. A refusal is said where it belongs, and a draft saved since in another tab is
 * offered as it now is; a draft not wanted is discarded, once asked. Nobody signed in keeps the one-step Save visit; a
 * signed-in user without the permission is told what they need. Every answer is the server's golden one or built from
 * it; nothing here is SASBO's.
 */

const CREATED = monthEndBody('POST /api/workplaces/visit-drafts')
const SUBMITTED = monthEndBody('POST /api/workplaces/visit-drafts/{id}/submit')
const MINE = monthEndBody('GET /api/workplaces/visit-drafts')

/** Every write the form made, in order: `{ method, path, body }`. */
let writes

const record = async (request) => {
  writes.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() })
}

/** The drafts' routes as the server answers them -- the draft numbered 1, its version moving with each save. */
const draftsAnswer = ({ mine = { ...MINE, drafts: [] } } = {}) => [
  http.get('*/api/workplaces/visit-drafts', () => HttpResponse.json(mine)),
  http.post('*/api/workplaces/visit-drafts', async ({ request }) => {
    await record(request)
    return HttpResponse.json({ ...CREATED, id: 1, version: 0 }, { status: 201 })
  }),
  http.put('*/api/workplaces/visit-drafts/:id', async ({ request }) => {
    await record(request)
    const body = writes.at(-1).body
    return HttpResponse.json({ ...CREATED, id: 1, recruited: body.recruited ?? null, version: body.version + 1 })
  }),
  http.post('*/api/workplaces/visit-drafts/:id/submit', async ({ request }) => {
    await record(request)
    return HttpResponse.json({ ...SUBMITTED, id: 1, version: writes.at(-1).body.version + 1 })
  }),
  // A discard has no body: the version it names is in the address.
  http.delete('*/api/workplaces/visit-drafts/:id', ({ request }) => {
    const url = new URL(request.url)
    writes.push({ method: 'DELETE', path: url.pathname, version: url.searchParams.get('version') })
    return new HttpResponse(null, { status: 204 })
  }),
]

const capturer = (ui) => <TestAuthProvider permissions={[PERMISSIONS.VISITS_CAPTURE]}>{ui}</TestAuthProvider>

const form = () => screen.getByRole('button', { name: 'Print form' }).closest('form')
const field = (label) => within(form()).getByLabelText(label)
const saidAt = (label) => document.getElementById(field(label).getAttribute('aria-describedby')?.split(' ').find((id) =>
  document.getElementById(id)?.getAttribute('role') === 'alert'))?.textContent

/**
 * Each value set in its field. The form's typing is not under test here (`locationVisit.save.test.jsx` types), and a
 * keystroke a character re-renders all 31 fields each time -- slow enough on a loaded runner to reach the timeout.
 */
function fill(values) {
  for (const [label, value] of Object.entries(values)) fireEvent.change(field(label), { target: { value } })
}

async function chooseToday(user) {
  await user.click(field('Date of Visit'))
  await user.click(screen.getByRole('button', { name: 'Today' }))
}

beforeEach(() => {
  writes = []
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(window, 'print').mockImplementation(() => {})
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-09-28T08:00:00+02:00') })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('saved as a draft, saved again, submitted (VISITS_CAPTURE)', () => {
  it('🔒 5 Oct: Save draft sends the R/A number and what a visit keeps, under the server\'s names -- never a person\'s details -- and rests until the form changes', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001', 'Organiser Name': 'INVENTED ORGANISER', Complement: '40',
      'Branch Rep Name': 'INVENTED REPRESENTATIVE' })
    await chooseToday(user)

    await user.click(screen.getByRole('button', { name: 'Save draft' }))

    expect(await screen.findByText('Draft saved for INVENTED MAIN STREET (R/A 900001).')).toBeInTheDocument()
    expect(writes).toEqual([{
      method: 'POST', path: '/api/workplaces/visit-drafts',
      body: { raNumber: 900001, visitedOn: '2026-09-28', organiser: 'INVENTED ORGANISER', complementFound: 40 },
    }])
    expect(JSON.stringify(writes)).not.toMatch(/INVENTED REPRESENTATIVE/)
    expect(screen.getByRole('button', { name: 'Save draft' })).toBeDisabled()
  })

  it('🔒 saved again, a draft names the version it was made from', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001' })
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText('Draft saved for INVENTED MAIN STREET (R/A 900001).')

    fill({ 'No of New Members Recruited': '2' })
    await user.click(screen.getByRole('button', { name: 'Save draft' }))

    await screen.findByText('Draft saved for INVENTED MAIN STREET (R/A 900001).')
    expect(writes[1]).toEqual({ method: 'PUT', path: '/api/workplaces/visit-drafts/1',
      body: { raNumber: 900001, recruited: 2, version: 0 } })
  })

  it('🔒 Submit visit submits the version saved -- and the form is read-only after, still to print', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001' })
    await chooseToday(user)
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText('Draft saved for INVENTED MAIN STREET (R/A 900001).')

    await user.click(screen.getByRole('button', { name: 'Submit visit' }))

    expect(await screen.findByText(/^Visit submitted for INVENTED MAIN STREET \(R\/A 900001\)\. It is on record now/))
      .toBeInTheDocument()
    expect(writes[1]).toEqual({ method: 'POST', path: '/api/workplaces/visit-drafts/1/submit', body: { version: 0 } })
    expect(field('R / A Number')).toHaveAttribute('readonly')
    expect(field('Date of Visit')).toHaveValue('28 Sep 2026')
    expect(screen.queryByRole('button', { name: 'Submit visit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start a new visit' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Print form' }))
    expect(window.print).toHaveBeenCalledTimes(1)
  })

  it('Submit visit straight from a new form saves it first, then submits it -- one draft, one submission', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001' })
    await chooseToday(user)

    await user.click(screen.getByRole('button', { name: 'Submit visit' }))

    await screen.findByText(/^Visit submitted for INVENTED MAIN STREET/)
    expect(writes.map(({ method, path }) => `${method} ${path}`)).toEqual([
      'POST /api/workplaces/visit-drafts', 'POST /api/workplaces/visit-drafts/1/submit'])
    expect(writes[1].body).toEqual({ version: 0 })
  })

  it('🔒 a draft with no day saves, but is not submitted -- said at the day, before anything is sent', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001' })

    await user.click(screen.getByRole('button', { name: 'Submit visit' }))

    expect(saidAt('Date of Visit')).toBe('Choose the day of the visit.')
    expect(writes).toEqual([])
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText(/^Draft saved for/)
    expect(writes[0].body).toEqual({ raNumber: 900001 })
  })
})

describe('refusals, said where they belong', () => {
  it('VISIT_INVALID at the field the server names; WORKPLACE_UNKNOWN at the R/A number -- the form kept as typed', async () => {
    const invalid = monthEndExamples.refusals.VISIT_INVALID
    server.use(http.get('*/api/workplaces/visit-drafts', () => HttpResponse.json({ ...MINE, drafts: [] })),
      http.post('*/api/workplaces/visit-drafts', () => HttpResponse.json(
        { ...invalid.body, message: 'A visit cannot be on a day still to come. Nothing was saved.',
          details: { field: 'visitedOn' } }, { status: 400 })))
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001', Complement: '40' })
    await chooseToday(user)

    await user.click(screen.getByRole('button', { name: 'Save draft' }))

    expect(await screen.findByText('A visit cannot be on a day still to come. Nothing was saved.')).toBeInTheDocument()
    expect(saidAt('Date of Visit')).toBe('A visit cannot be on a day still to come. Nothing was saved.')
    expect(field('Complement')).toHaveValue('40')

    server.use(http.post('*/api/workplaces/visit-drafts', () => HttpResponse.json({
      code: 'WORKPLACE_UNKNOWN', message: 'No workplace with that R/A number is on record here. Check the number.',
      details: { field: 'raNumber' } }, { status: 400 })))
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText('No workplace with that R/A number is on record here. Check the number.')
    expect(saidAt('R / A Number')).toBe('No workplace with that R/A number is on record here. Check the number.')
  })

  it('🔒 saved since in another tab: VISIT_DRAFT_STALE says so, writes nothing over it, and opens the draft as it is now', async () => {
    const stale = monthEndExamples.refusals.VISIT_DRAFT_STALE
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'R / A Number': '900001' })
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText(/^Draft saved for/)

    server.use(
      http.put('*/api/workplaces/visit-drafts/:id', () => HttpResponse.json(stale.body, { status: stale.status })),
      http.get('*/api/workplaces/visit-drafts/:id', () => HttpResponse.json({ ...CREATED, id: 1, organiser: 'INVENTED OTHER TAB',
        version: 3 })))
    fill({ 'Organiser Name': 'INVENTED ORGANISER' })
    await user.click(screen.getByRole('button', { name: 'Save draft' }))

    expect(await screen.findByText(stale.body.message, { exact: false })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Open it as it is now' }))

    expect(await screen.findByText('The draft is shown as it was last saved.')).toBeInTheDocument()
    expect(field('Organiser Name')).toHaveValue('INVENTED OTHER TAB')
  })
})

describe('My visits', () => {
  it('lists my drafts newest first, and opens one into the form; a submitted visit opens read-only', async () => {
    // Its own number: the golden draft and the golden submission are the one draft, 999001.
    const submittedList = { ...MINE, status: 'SUBMITTED',
      drafts: [{ ...SUBMITTED, id: 999002, organiser: 'INVENTED SUBMITTER' }] }
    // First, so it answers before the drafts' own list does: MSW takes the first handler that matches.
    server.use(http.get('*/api/workplaces/visit-drafts', ({ request }) => HttpResponse.json(
      new URL(request.url).searchParams.get('status') === 'SUBMITTED' ? submittedList : MINE)),
    ...draftsAnswer({ mine: MINE }))
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))

    const mine = await screen.findByRole('region', { name: 'My visits' })
    await user.click(await within(mine).findByRole('button', { name: 'Open: INVENTED MAIN STREET, R/A 900001' }))

    expect(field('R / A Number')).toHaveValue('900001')
    expect(field('Organiser Name')).toHaveValue('INVENTED ORGANISER')
    expect(screen.getByText('Opened the draft for INVENTED MAIN STREET (R/A 900001).')).toBeInTheDocument()

    await user.click(within(mine).getByRole('button', { name: 'Submitted' }))
    await user.click(await within(mine).findByRole('button', { name: 'Open: INVENTED MAIN STREET, R/A 900001' }))

    expect(field('Organiser Name')).toHaveValue('INVENTED SUBMITTER')
    expect(field('Organiser Name')).toHaveAttribute('readonly')
  })

  it('asks before opening a draft over changes not yet saved, and keeps them on Keep editing', async () => {
    server.use(...draftsAnswer({ mine: MINE }))
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    fill({ 'Organiser Name': 'INVENTED TYPING' })

    const mine = await screen.findByRole('region', { name: 'My visits' })
    await user.click(await within(mine).findByRole('button', { name: /^Open: INVENTED MAIN STREET/ }))
    expect(screen.getByRole('alert')).toHaveTextContent('The form has changes that are not saved.')

    await user.click(screen.getByRole('button', { name: 'Keep editing' }))
    expect(field('Organiser Name')).toHaveValue('INVENTED TYPING')
  })
})

describe('Discard draft (October build review)', () => {
  async function aSavedDraft(user) {
    fill({ 'R / A Number': '900001' })
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await screen.findByText('Draft saved for INVENTED MAIN STREET (R/A 900001).')
  }

  it('🔒 review: asks first, then discards the draft at the version the form holds -- and the form starts again', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    expect(screen.queryByRole('button', { name: 'Discard draft' })).not.toBeInTheDocument()
    await aSavedDraft(user)

    await user.click(screen.getByRole('button', { name: 'Discard draft' }))
    expect(screen.getByText(/Discard the draft for INVENTED MAIN STREET \(R\/A 900001\)\?/)).toBeInTheDocument()
    expect(writes).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Discard it' }))

    expect(await screen.findByText('Draft discarded for INVENTED MAIN STREET (R/A 900001).')).toBeInTheDocument()
    expect(writes[1]).toEqual({ method: 'DELETE', path: '/api/workplaces/visit-drafts/1', version: '0' })
    expect(field('R / A Number')).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Discard draft' })).not.toBeInTheDocument()
  })

  it('Keep the draft discards nothing; a draft submitted elsewhere is refused, said, and offered as it now is', async () => {
    server.use(monthEndRefusal('VISIT_ALREADY_SUBMITTED', 'DELETE /api/workplaces/visit-drafts/{id}'), ...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))
    await aSavedDraft(user)

    await user.click(screen.getByRole('button', { name: 'Discard draft' }))
    await user.click(screen.getByRole('button', { name: 'Keep the draft' }))
    expect(screen.queryByRole('button', { name: 'Discard it' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Discard draft' }))
    await user.click(screen.getByRole('button', { name: 'Discard it' }))

    expect(await screen.findByText(monthEndExamples.refusals.VISIT_ALREADY_SUBMITTED.body.message, { exact: false }))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open it as it is now' })).toBeInTheDocument()
    expect(field('R / A Number')).toHaveValue('900001')
    expect(writes.filter((write) => write.method === 'DELETE')).toEqual([])
  })
})

describe('My visits, every state (October build review)', () => {
  it('review: failing to load says so, and Try again asks once more', async () => {
    let calls = 0
    server.use(http.get('*/api/workplaces/visit-drafts', () => {
      calls += 1
      return calls === 1 ? HttpResponse.json({ message: 'boom' }, { status: 500 }) : HttpResponse.json(MINE)
    }), ...draftsAnswer({ mine: MINE }))
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))

    const mine = await screen.findByRole('region', { name: 'My visits' })
    expect(await within(mine).findByText('Your visits could not be loaded.')).toBeInTheDocument()
    await user.click(within(mine).getByRole('button', { name: /Try again/ }))

    expect(await within(mine).findByRole('button', { name: 'Open: INVENTED MAIN STREET, R/A 900001' })).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('review: an empty list says which -- no drafts saved, no visits submitted', async () => {
    server.use(...draftsAnswer())
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))

    const mine = await screen.findByRole('region', { name: 'My visits' })
    expect(await within(mine).findByText('No drafts saved yet.')).toBeInTheDocument()
    await user.click(within(mine).getByRole('button', { name: 'Submitted' }))
    expect(await within(mine).findByText('No visits submitted yet.')).toBeInTheDocument()
  })

  it('🔒 review: past the newest twenty, a pager -- Next asks for the next page, and every visit can be opened', async () => {
    const pages = []
    const harbour = { ...MINE.drafts[0], id: 999003, workplaceName: 'INVENTED HARBOUR', raNumber: 900003 }
    server.use(http.get('*/api/workplaces/visit-drafts', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page'))
      pages.push(page)
      return HttpResponse.json({ ...MINE, page, size: 20, totalElements: 21, totalPages: 2,
        drafts: page === 0 ? MINE.drafts : [harbour] })
    }), ...draftsAnswer({ mine: MINE }))
    const user = setupUser()
    renderWithProviders(capturer(<OrganizerReport />))

    const mine = await screen.findByRole('region', { name: 'My visits' })
    await within(mine).findByRole('button', { name: 'Open: INVENTED MAIN STREET, R/A 900001' })
    expect(within(mine).getByText(/Page 1 of 2/)).toBeInTheDocument()
    await user.click(within(mine).getByRole('button', { name: /Next/ }))

    expect(await within(mine).findByRole('button', { name: 'Open: INVENTED HARBOUR, R/A 900003' })).toBeInTheDocument()
    expect(within(mine).getByText(/Page 2 of 2/)).toBeInTheDocument()
    expect(pages).toEqual([0, 1])
  })
})

describe('who may save', () => {
  it('🔒 nobody signed in: the one-step Save visit as before, a line saying signing in brings drafts -- and no drafts asked for', () => {
    renderWithProviders(<OrganizerReport />, { initialEntries: ['/reports/organizer-report'] })

    expect(screen.getByRole('button', { name: 'Save visit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'My visits' })).not.toBeInTheDocument()
    expect(screen.getByText(/Sign in to save visits as drafts and submit them\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?next=%2Freports%2Forganizer-report')
  })

  it('🔒 signed in without VISITS_CAPTURE: no save at all, told what is needed -- and the form still prints', () => {
    renderWithProviders(<TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW]}><OrganizerReport /></TestAuthProvider>)

    expect(screen.queryByRole('button', { name: 'Save visit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument()
    expect(screen.getByText(/You need permission to capture visits\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print form' })).toBeInTheDocument()
  })

  it('a11y: the form with My visits, signed in, has no axe violations', async () => {
    server.use(...draftsAnswer({ mine: MINE }))
    const { container } = renderWithProviders(capturer(<OrganizerReport />))
    await screen.findByRole('button', { name: /^Open: INVENTED MAIN STREET/ })
    fireEvent.change(field('R / A Number'), { target: { value: '900001' } })

    await expectNoA11yViolations(container)
  })
})
