// @vitest-environment jsdom -- the member's record: its workplace read, and changed
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { monthEndAnswer, monthEndBody, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * MONTH END `01.3` ON THE RECORD.
 *
 * The register's list carries three scalars of a member's workplace -- its id, name and R/A number -- and never its
 * lineage (MR-PERF-9); the answer for ONE member carries the lineage, and the record reads it for the area and the
 * committee. Editing, the record offers capture's control, and its save names a workplace only by `workplaceId`, only
 * when the edit changed it (BR66, MD58). Every member and workplace here is invented.
 */

const [HARBOUR, HEAD_OFFICE] = monthEndBody('GET /api/workplaces').content

const AT_HARBOUR = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Harbour',
  initials: 'S',
  identityType: 'I.D. Number',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
  bankName: 'Invented Insurer',
  branch: 'Rosebank',
  workplaceId: HARBOUR.id,
  workplaceName: HARBOUR.name,
  workplaceRaNumber: HARBOUR.raNumber,
}
const NOWHERE = {
  ...AT_HARBOUR, id: 8, membershipNumber: 'M0008', surname: 'Nowhere', identityNumber: '8506155000084',
  workplaceId: null, workplaceName: null, workplaceRaNumber: null,
}

let asked
let saved
/**
 * The record is open. Since A10 a row OPENS by reading the member (`GET /api/members/{id}`), the register's own kit
 * answering it; `asked` counts the reads after that -- the Employment tab's read of the workplace's lineage.
 */
let opened

beforeEach(() => {
  asked = []
  saved = undefined
  opened = false
  // The screen reports a failure through the one sink as well as showing it.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(...pageFixtures)
  server.use(
    http.get('*/api/members/:id', ({ params }) => {
      // The read that opens the record is the kit's, beneath.
      if (!opened) return undefined
      asked.push(params.id)
      return HttpResponse.json({ ...AT_HARBOUR, workplace: HARBOUR })
    }),
    monthEndAnswer('GET /api/reference/status'),
    monthEndAnswer('GET /api/reference/employers'),
    http.get('*/api/workplaces', ({ request }) => {
      const search = (new URL(request.url).searchParams.get('search') ?? '').toLowerCase()
      const content = [HEAD_OFFICE].filter((row) => row.name.toLowerCase().includes(search))
      return HttpResponse.json({ content, page: 0, size: 8, totalElements: content.length, totalPages: 1, reason: null })
    }),
    http.put('*/api/members/:id', async ({ request }) => {
      saved = await request.json()
      return HttpResponse.json({ ...saved, workplace: HEAD_OFFICE })
    }),
    // Last, so the handlers above answer first.
    ...registerHandlers([AT_HARBOUR, NOWHERE]),
  )
})

const panel = () => within(screen.getByTestId('member-panel'))

async function openEmployment(user, surname) {
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: new RegExp(`open the record for.*${surname}`, 'i') }))
  // The tabs are there once the record has been read: from here a read is the record's own.
  const employment = await panel().findByRole('button', { name: /Employment & Bank/i })
  opened = true
  await user.click(employment)
}

const workplaceGroup = () => panel().getByText('Workplace', { selector: 'legend' }).closest('fieldset')

describe('the record -- the member\'s workplace, read', () => {
  it('🔑 shows its name, R/A number, area and committee -- the lineage only the answer for ONE member carries', async () => {
    const user = setupUser()
    await openEmployment(user, 'Harbour')

    expect(await panel().findByText('R/A 900003 · Area B · Committee R01, INVENTED SOUTH COMMITTEE')).toBeInTheDocument()
    expect(panel().getByText('INVENTED HARBOUR')).toBeInTheDocument()
    expect(panel().getByText(/From SASBO’s list of workplaces\./)).toBeInTheDocument()
    expect(asked, 'read for this member, once').toEqual(['7'])
  })

  it('no workplace on record is a value, said as one -- not an error, and nothing is asked', async () => {
    const user = setupUser()
    await openEmployment(user, 'Nowhere')

    expect(await panel().findByText('No workplace on record')).toBeInTheDocument()
    expect(panel().queryByRole('alert')).not.toBeInTheDocument()
    expect(asked).toEqual([])
  })

  it('a read the server does not have yet -- a 404 -- is not available (MD58, MV-53)', async () => {
    server.use(http.get('*/api/members/:id', () => (opened ? HttpResponse.json({ status: 404 }, { status: 404 }) : undefined)))
    const user = setupUser()
    await openEmployment(user, 'Harbour')

    // One paragraph, "Not available." in its strong (the mock's ⑦), the reason beside it.
    const said = (_, node) => node?.tagName === 'P'
      && node.textContent === 'Not available. This server does not answer for SASBO’s workplaces yet.'
    expect(await panel().findByText(said)).toBeInTheDocument()
    expect(panel().queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('a read that failed says so, with Try again -- and Try again reads it', async () => {
    let calls = 0
    server.use(http.get('*/api/members/:id', () => {
      if (!opened) return undefined
      calls += 1
      return calls === 1
        ? HttpResponse.json({ status: 503 }, { status: 503 })
        : HttpResponse.json({ ...AT_HARBOUR, workplace: HARBOUR })
    }))
    const user = setupUser()
    await openEmployment(user, 'Harbour')

    expect(await panel().findByText('The member’s workplace could not be loaded.')).toBeInTheDocument()
    await user.click(panel().getByRole('button', { name: 'Try again' }))

    expect(await panel().findByText('R/A 900003 · Area B · Committee R01, INVENTED SOUTH COMMITTEE')).toBeInTheDocument()
    expect(calls).toBe(2)
  })
})

async function editAndChooseHeadOffice(user) {
  await openEmployment(user, 'Harbour')
  await user.click(screen.getByRole('button', { name: /Edit Member/i }))
  const employer = await panel().findByRole('combobox', { name: /^Employer/ })
  await waitFor(() => expect(employer).toBeEnabled())
  await user.type(employer, 'INVEN')
  await user.click(await screen.findByRole('option', { name: 'INVENTED BANK' }))
  await user.type(panel().getByLabelText(/^Find the workplace/), 'head')
  await user.click(await panel().findByRole('button', { name: /^INVENTED HEAD OFFICE/ }))
}

describe('the record -- the member\'s workplace, changed', () => {
  it('🔑 a changed workplace is confirmed by NAME and saved by its ID -- and the bank fields the record holds stay', async () => {
    const user = setupUser()
    await editAndChooseHeadOffice(user)

    expect(await panel().findByText('R/A 900002 · Area A · Committee Q01, INVENTED NORTH COMMITTEE')).toBeInTheDocument()
    // The record's own Bank Where Employed and Bank Branch are values somebody chose: nothing is offered over them.
    expect(panel().queryByText(/from this workplace\?$/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    // Changed by its id, read by its name and R/A number: two of SASBO's workplaces may share a name.
    expect(within(dialog).getByText('Workplace: INVENTED HARBOUR (R/A 900003) → INVENTED HEAD OFFICE (R/A 900002)'))
      .toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved.workplaceId).toBe(HEAD_OFFICE.id)
    expect(saved, 'the answers the list carries are not sent back as inputs').not.toHaveProperty('workplaceName')
    expect(saved).not.toHaveProperty('workplace')
    expect(saved.bankName).toBe('Invented Insurer')
    expect(saved.branch).toBe('Rosebank')
  })

  it('🔴 a workplace that shares its name with the one on record is still a change -- the id decides, not the name', async () => {
    // An invented twin: another workplace called INVENTED HEAD OFFICE, at another R/A number.
    const twin = { ...HEAD_OFFICE, id: 99, raNumber: 900099 }
    // The record is read from the answer for one member since A10, so it carries the twin's scalars as the row does.
    const atTwin = { ...AT_HARBOUR, workplaceId: twin.id, workplaceName: twin.name, workplaceRaNumber: twin.raNumber }
    server.use(
      http.get('*/api/members/:id', () => HttpResponse.json({ ...atTwin, workplace: twin })),
      ...registerHandlers([atTwin]),
    )
    const user = setupUser()
    await editAndChooseHeadOffice(user)

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    expect(within(dialog).getByText('Workplace: INVENTED HEAD OFFICE (R/A 900099) → INVENTED HEAD OFFICE (R/A 900002)'))
      .toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved.workplaceId).toBe(HEAD_OFFICE.id)
  })

  it('the must-pass: an edit that leaves the workplace alone does not name it -- so no switch can refuse it', async () => {
    const user = setupUser()
    await openEmployment(user, 'Harbour')
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))
    await user.click(panel().getByRole('button', { name: /Personal Information/i }))
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved.initials).toBe('Q')
    expect(saved).not.toHaveProperty('workplaceId')
  })

  it('🔴 a save refused WORKPLACE_UNKNOWN is said at the Workplace field, in the server\'s words -- the edit kept', async () => {
    // The server's own refusal of a workplace it does not have (month-end-examples.json).
    server.use(monthEndRefusal('WORKPLACE_UNKNOWN', 'PUT /api/members/{id}'))
    const sentence = 'That workplace is not on record here. Choose another.'
    const user = setupUser()
    await editAndChooseHeadOffice(user)
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(within(workplaceGroup()).getByRole('alert')).toHaveTextContent(sentence))
    // Still editing, the chosen workplace still there to be changed, and the notice says what the server said.
    expect(screen.getByRole('button', { name: /^Save changes$/ })).toBeInTheDocument()
    expect(within(workplaceGroup()).getByText('INVENTED HEAD OFFICE')).toBeInTheDocument()
    expect(panel().getByText('The changes were not saved')).toBeInTheDocument()
    // The notice carries the server's sentence too, and says that nothing was written (the manual stream's ⓑ).
    expect(panel().getByText(`${sentence} Nothing was written.`)).toBeInTheDocument()
  })

  it('🔍 MO24 ⑫: in the record\'s panel the employer stands over the search, in the panel\'s one column', async () => {
    // jsdom lays nothing out: the column is held by the class that makes it, and was measured at 1280 in a browser.
    const user = setupUser()
    await openEmployment(user, 'Harbour')
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))
    const employer = await panel().findByRole('combobox', { name: /^Employer/ })
    let pair = panel().getByLabelText(/^Find the workplace/)
    while (!pair.contains(employer)) pair = pair.parentElement

    expect(pair).toHaveClass('grid')
    expect(pair, 'two columns in a 424px panel squeezed both controls').not.toHaveClass('grid-cols-2')
  })
})
