// @vitest-environment jsdom -- the Location Visit form's Save visit: what it sends, and what it says back (month end A.1)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { OrganizerReport } from './OrganizerReport'
import { monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 💾 MONTH END A.1 (MO22) -- A VISIT CAPTURED IS A VISIT ON RECORD. The Location Visit form printed and forgot (A60);
 * Save visit finds the workplace by the form's R/A number -- the workplaces' search, the row whose number IS it -- and
 * records the visit against it, each field under the server's name. Every answer is the server's golden one (MD48) or
 * built from its invented workplaces; nothing here is SASBO's.
 *
 * The clock is faked for `Date` alone, so the calendar's Today is 10 September 2026 -- the golden visit's own day.
 */

const RECORD = 'POST /api/workplaces/{id}/visits'
const [, HEAD_OFFICE] = monthEndBody('GET /api/workplaces').content

/** Every question put to the workplaces' search, and every visit posted -- `{ workplace, body }`. */
let searched
let posted

/** The workplaces' search as the server answers it: the name containing the term, or the R/A number equal to it. */
const workplacesSearched = (rows = [HEAD_OFFICE]) => http.get('*/api/workplaces', ({ request }) => {
  const query = Object.fromEntries(new URL(request.url).searchParams)
  searched.push(query)
  const term = (query.search ?? '').toLowerCase()
  const content = rows.filter((row) => row.name.toLowerCase().includes(term) || String(row.raNumber) === term)
  return HttpResponse.json({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1, reason: null })
})

/** The visit recorded: the golden answer, with whatever a test says the server found. */
const visitRecorded = (fields = {}) => http.post('*/api/workplaces/:id/visits', async ({ request, params }) => {
  posted.push({ workplace: params.id, body: await request.json() })
  return HttpResponse.json({ ...monthEndBody(RECORD), ...fields }, { status: 201 })
})

const form = () => screen.getByRole('button', { name: 'Save visit' }).closest('form')
const field = (label) => within(form()).getByLabelText(label)
/** The sentence said under a field -- its error, read through the field's own description. */
const saidAt = (label) => document.getElementById(field(label).getAttribute('aria-describedby')?.split(' ').find((id) =>
  document.getElementById(id)?.getAttribute('role') === 'alert'))?.textContent

/**
 * Each value set in its field, whole. Typed a key at a time, every character re-rendered the form's 31 fields, and a
 * test that fills a whole visit ran past the 20 s timeout on a loaded runner (seen 7 Oct on a contended box, main's own
 * form and this file alike) -- a flake by construction. What a key does to a field is `TextInput`'s to test, not this.
 */
async function fill(user, values) {
  for (const [label, value] of Object.entries(values)) fireEvent.change(field(label), { target: { value } })
}

async function chooseToday(user) {
  await user.click(field('Date of Visit'))
  await user.click(screen.getByRole('button', { name: 'Today' }))
}

/** A whole visit, as an organiser fills it in, and one person's details that must never be sent. */
async function fillAVisit(user) {
  await fill(user, {
    'Organiser Name': 'INVENTED ORGANISER',
    'R / A Number': '900002',
    Complement: '40',
    Membership: '12',
    'No of Non-Members': '3',
    'No of New Members Recruited': '2',
    'No of Non-Members Interviewed': '5',
    'Branch Rep Name': 'INVENTED REPRESENTATIVE',
  })
  fireEvent.change(within(form()).getAllByLabelText('Cell Phone & E-Mail')[0], { target: { value: 'rep@example.invalid' } })
  // A native time control, which takes a time whole rather than a key at a time.
  fireEvent.change(field('Time In'), { target: { value: '09:30' } })
  fireEvent.change(field('Time Out'), { target: { value: '11:00' } })
  await chooseToday(user)
}

beforeEach(() => {
  searched = []
  posted = []
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-09-10T08:00:00+02:00') })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('Save visit -- what it sends (A.1)', () => {
  it('finds the workplace by its R/A number and records the visit: each field by the server\'s name, the counts whole numbers', async () => {
    server.use(workplacesSearched(), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(await screen.findByText('Visit saved for INVENTED HEAD OFFICE (R/A 900002).')).toBeInTheDocument()
    expect(searched).toEqual([{ search: '900002', page: '0', size: '50' }])
    expect(posted).toEqual([{
      workplace: String(HEAD_OFFICE.id),
      body: {
        visitedOn: '2026-09-10', timeIn: '09:30', timeOut: '11:00', organiser: 'INVENTED ORGANISER',
        complementFound: 40, membershipFound: 12, nonMembers: 3, recruited: 2, nonMembersInterviewed: 5,
      },
    }])
    // A person's details are printed with the form and never sent.
    expect(JSON.stringify(posted)).not.toMatch(/INVENTED REPRESENTATIVE|rep@example/)
  })

  it('only the day is needed: an empty field is left out, never sent as "" or 0', async () => {
    server.use(workplacesSearched(), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fill(user, { 'R / A Number': '900002' })
    await chooseToday(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    await screen.findByText(/^Visit saved for INVENTED HEAD OFFICE/)
    expect(posted[0].body).toEqual({ visitedOn: '2026-09-10' })
  })

  it('a visit that found another complement says from what to what -- the server keeps what it was', async () => {
    server.use(workplacesSearched(), visitRecorded({ complementFound: 40, complementBefore: 38 }))
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(await screen.findByText(
      'Visit saved for INVENTED HEAD OFFICE (R/A 900002). Staff complement changed from 38 to 40.',
    )).toBeInTheDocument()
  })

  it('one visit is one save: Save visit rests once it has saved, until something on the form changes', async () => {
    server.use(workplacesSearched(), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)
    await user.click(screen.getByRole('button', { name: 'Save visit' }))
    await screen.findByText(/^Visit saved for/)

    expect(screen.getByRole('button', { name: 'Save visit' })).toBeDisabled()
    await user.type(field('Membership'), '3')
    expect(screen.getByRole('button', { name: 'Save visit' })).toBeEnabled()
    expect(posted).toHaveLength(1)
  })
})

describe('Save visit -- what it says when it cannot', () => {
  it('a count that is not a whole number, no day and no R/A number: each said at its field, and NOTHING is sent', async () => {
    server.use(workplacesSearched(), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fill(user, { Complement: 'forty', 'No of Non-Members': '1.5', Membership: '12' })

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(saidAt('Complement')).toBe('A whole number, or leave it empty.')
    expect(saidAt('No of Non-Members')).toBe('A whole number, or leave it empty.')
    expect(saidAt('Date of Visit')).toBe('Choose the day of the visit.')
    expect(saidAt('R / A Number')).toBe('The R/A number says which workplace was visited.')
    // The must-pass: a whole number is not refused.
    expect(saidAt('Membership')).toBeUndefined()
    expect(searched).toEqual([])
    expect(posted).toEqual([])
  })

  it('no workplace with that R/A number: said at the R/A number, and nothing is recorded', async () => {
    // The search finds a workplace by a name CONTAINING the term, too: "9000" answers INVENTED PLAZA 9000, whose number
    // is another -- and a visit goes only to the workplace whose R/A number IS the one typed.
    const plaza = { ...HEAD_OFFICE, id: 77, raNumber: 911111, name: 'INVENTED PLAZA 9000' }
    server.use(workplacesSearched([HEAD_OFFICE, plaza]), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fill(user, { 'R / A Number': '9000' })
    await chooseToday(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    await waitFor(() => expect(saidAt('R / A Number')).toBe('No workplace with that R/A number is on record here.'))
    expect(searched).toHaveLength(1)
    expect(posted).toEqual([])
  })

  it('🔴 VISIT_INVALID is said at the field the server names, in its words -- the form kept as typed', async () => {
    server.use(workplacesSearched(), monthEndRefusal('VISIT_INVALID', RECORD))
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    const refusal = monthEndExamples.refusals.VISIT_INVALID.body
    expect(refusal.details.field).toBe('visitedOn')
    await waitFor(() => expect(saidAt('Date of Visit')).toBe(refusal.message))
    expect(field('Organiser Name')).toHaveValue('INVENTED ORGANISER')
    expect(field('Complement')).toHaveValue('40')
    expect(screen.queryByText(/^Visit saved/)).not.toBeInTheDocument()
  })

  it('WORKPLACE_UNKNOWN is said at the R/A number, in the server\'s words', async () => {
    server.use(workplacesSearched(), monthEndRefusal('WORKPLACE_UNKNOWN', RECORD))
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    await waitFor(() => expect(saidAt('R / A Number')).toBe(monthEndExamples.refusals.WORKPLACE_UNKNOWN.body.message))
    expect(field('R / A Number')).toHaveValue('900002')
  })

  it('🔴 a write whose answer never came may have landed: said so, and never "not saved"', async () => {
    server.use(workplacesSearched(), http.post('*/api/workplaces/:id/visits', () =>
      HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })))
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(await screen.findByText(
      'The visit could not be confirmed. Reload the workplace’s visits on the Workplaces screen and check before trying again.',
    )).toBeInTheDocument()
    expect(screen.queryByText(/visit was not saved/)).not.toBeInTheDocument()
    expect(field('Organiser Name')).toHaveValue('INVENTED ORGANISER')
  })

  it('the must-pass: a refusal the form has no field for says it was NOT saved, in the server\'s words', async () => {
    server.use(workplacesSearched(), monthEndRefusal('REQUEST_MALFORMED', RECORD))
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(await screen.findByText(`The visit was not saved. ${monthEndExamples.refusals.REQUEST_MALFORMED.body.message}`))
      .toBeInTheDocument()
  })

  it('a search for the workplace that failed sent no visit -- and says that nothing was saved', async () => {
    server.use(
      http.get('*/api/workplaces', () => HttpResponse.json({ status: 503 }, { status: 503 })),
      visitRecorded(),
    )
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Save visit' }))

    expect(await screen.findByText(/^The workplace could not be looked up, so nothing was saved\./)).toBeInTheDocument()
    expect(posted).toEqual([])
  })
})

describe('the form around Save visit', () => {
  it('says beside the person fields what is saved and what is only printed -- on screen, not on the paper', () => {
    renderWithProviders(<OrganizerReport />)

    const sentence = screen.getByText(
      'The day, who visited, the times and the counts are saved with the visit. The contact details are printed with the form and not saved.',
    )
    expect(sentence).toHaveClass('no-print')
    // Printing is untouched: the form itself is not taken off the paper.
    expect(form()).not.toHaveClass('no-print')
    expect(form().closest('.no-print')).toBeNull()
  })

  it('🔴 Clear form clears the form, not only its boxes -- so no visit is saved from values nobody can see', async () => {
    server.use(workplacesSearched(), visitRecorded())
    const user = setupUser()
    renderWithProviders(<OrganizerReport />)
    await fillAVisit(user)

    await user.click(screen.getByRole('button', { name: 'Clear form' }))
    expect(field('R / A Number')).toHaveValue('')
    expect(field('Organiser Name')).toHaveValue('')

    await user.click(screen.getByRole('button', { name: 'Save visit' }))
    expect(saidAt('R / A Number')).toBe('The R/A number says which workplace was visited.')
    expect(posted).toEqual([])
  })
})
