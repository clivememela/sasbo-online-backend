// @vitest-environment jsdom -- Capture Member's Workplace field, driven: the offer both ways, and what a save sends
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { monthEndAnswer, monthEndBody, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { setupUser } from '../../test/user'

/**
 * MONTH END `01.3` ON CAPTURE -- the Workplace field above *Bank Where Employed*, *Bank Branch* and *IBT No.*, which
 * stay exactly as they were.
 *
 * 🔑 THE OFFER IS A QUESTION, NEVER A FILL. Choosing a workplace whose names agree with what the form offers ASKS
 * whether to fill the two bank fields: accepted, both fill and say where from (the derivation state, DERIVED); declined,
 * both stay as they were. A field the administrator chose is never offered -- the S4 rule every derived field here
 * obeys. Each is driven through the screen, against the server's golden answers and invented banks.
 */

const [, HEAD_OFFICE] = monthEndBody('GET /api/workplaces').content

// Invented banks as `/api/banks` and `/api/banks/branches` serve them. The form's spelling is NOT SASBO's
// ("Invented Bank", "INVENTED BANK"): the offer must fill the form's own.
const BANKS = [{ id: 1, name: 'Invented Bank' }, { id: 2, name: 'Other Invented Bank' }]
const HEAD_OFFICE_BRANCH = {
  id: 11, bankName: 'Invented Bank', branchName: 'Invented Head Office', branchCode: '900222',
  physicalAddress: '1 Invented Main Street, Invented City',
}
const PLAZA_BRANCH = { id: 12, bankName: 'Other Invented Bank', branchName: 'Invented Plaza', branchCode: '900333' }

let posted

beforeEach(() => {
  posted = undefined
  // The screen reports a failure through the one sink as well as showing it.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(...pageFixtures)
  server.use(
    http.get('*/api/banks', () => HttpResponse.json(BANKS)),
    http.get('*/api/banks/branches', () => HttpResponse.json([HEAD_OFFICE_BRANCH, PLAZA_BRANCH])),
    monthEndAnswer('GET /api/reference/status'),
    monthEndAnswer('GET /api/reference/employers'),
    http.get('*/api/workplaces', ({ request }) => {
      const search = (new URL(request.url).searchParams.get('search') ?? '').toLowerCase()
      const content = [HEAD_OFFICE].filter((row) => row.name.toLowerCase().includes(search))
      return HttpResponse.json({ content, page: 0, size: 8, totalElements: content.length, totalPages: 1, reason: null })
    }),
    http.post('*/api/members', async ({ request }) => {
      posted = await request.json()
      return HttpResponse.json({ id: 1, ...posted })
    }),
  )
})

const openEmployment = async (user) => user.click(await screen.findByRole('button', { name: /^Employment & Bank/ }))

/** A searchable field: clear any held choice, type into its own input, take the option by name. */
async function chooseFrom(user, labelText, typed, optionName) {
  const clear = screen.queryByRole('button', { name: new RegExp(`^Clear ${labelText}`) })
  if (clear) await user.click(clear)
  const input = await screen.findByRole('combobox', { name: new RegExp(`^${labelText}`) })
  await waitFor(() => expect(input).toBeEnabled())
  await user.type(input, typed)
  await user.click(await screen.findByRole('option', { name: optionName }))
}

/** The employer, then the workplace, as a person finds one. */
async function chooseHeadOffice(user) {
  await chooseFrom(user, 'Employer', 'INVEN', 'INVENTED BANK')
  await user.type(screen.getByLabelText(/^Find the workplace/), 'head')
  await user.click(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ }))
}

const bankField = () => screen.getByRole('combobox', { name: /^Bank Where Employed/ })
const branchField = () => screen.getByRole('combobox', { name: /^Bank Branch/ })
const theOffer = () => screen.queryByText(/from this workplace\?$/)

describe('Capture Member · Workplace -- the offer to fill the two bank fields', () => {
  it('🔑 accepted: both fill with the form\'s OWN options, each marked with where it came from', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseHeadOffice(user)

    expect(await screen.findByText('Fill "Bank Where Employed" and "Bank Branch" from this workplace?')).toBeInTheDocument()
    expect(screen.getByText('Invented Bank · Invented Head Office')).toBeInTheDocument()
    // Asked, not done: nothing is filled until the administrator says so.
    expect(bankField()).toHaveValue('')

    await user.click(screen.getByRole('button', { name: 'Fill' }))

    await waitFor(() => expect(bankField()).toHaveValue('Invented Bank'))
    expect(branchField()).toHaveValue('Invented Head Office')
    // The derivation state each now has -- DERIVED, and from which workplace (utils/derivation.js).
    expect(screen.getAllByText('Filled in from INVENTED HEAD OFFICE.')).toHaveLength(2)
    // What follows from a branch follows from this one too: IBT No. is the branch's code, as a person's choice gives.
    expect(screen.getByLabelText(/^IBT No\./)).toHaveValue('900222')
    expect(theOffer(), 'answered, the question goes').not.toBeInTheDocument()
  })

  it('declined: both stay exactly as they were, and the question does not come back for that workplace', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseHeadOffice(user)
    await screen.findByText(/from this workplace\?$/)

    await user.click(screen.getByRole('button', { name: 'No thanks' }))

    await waitFor(() => expect(theOffer()).not.toBeInTheDocument())
    expect(bankField()).toHaveValue('')
    expect(branchField()).toHaveValue('')
    expect(screen.queryByText(/^Filled in from INVENTED/)).not.toBeInTheDocument()
    // The workplace itself is still the member's.
    expect(screen.getByText('R/A 900002 · Area A · Committee Q01, INVENTED NORTH COMMITTEE')).toBeInTheDocument()
  })

  it('🔴 a bank the administrator chose is never overwritten -- and nothing is offered where its branches disagree', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Other', 'Other Invented Bank')
    await chooseHeadOffice(user)

    // The workplace's employer is Invented Bank, but the form holds the administrator's Other Invented Bank, whose
    // branches have no Invented Head Office: there is nothing the offer may fill.
    await screen.findByText('R/A 900002 · Area A · Committee Q01, INVENTED NORTH COMMITTEE')
    expect(theOffer()).not.toBeInTheDocument()
    expect(bankField()).toHaveValue('Other Invented Bank')
  })

  it('the must-pass beside it: with the agreeing bank chosen by hand, only the branch is offered -- and the bank keeps no note', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Inv', 'Invented Bank')
    await chooseHeadOffice(user)

    expect(await screen.findByText('Fill "Bank Branch" from this workplace?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Fill' }))

    await waitFor(() => expect(branchField()).toHaveValue('Invented Head Office'))
    expect(bankField()).toHaveValue('Invented Bank')
    expect(screen.getAllByText('Filled in from INVENTED HEAD OFFICE.'), 'the branch was filled; the bank is the administrator\'s')
      .toHaveLength(1)
  })
})

/** The smallest capture the form accepts, after whatever the test did on Employment & Bank: Payroll needs no bank. */
async function captureTheRest(user) {
  const field = (text) => screen.getByLabelText(new RegExp(`^${text}`))
  await user.selectOptions(field('Payment Type'), 'Payroll Deduction')
  await user.click(screen.getByRole('button', { name: /^Personal Information/ }))
  await user.type(field('First Name\\(s\\)'), 'Synthetic')
  await user.type(field('Surname'), 'Testperson')
  await user.type(field('Personal Email'), 'synthetic@example.test')
  await user.selectOptions(field('Identity Type'), 'I.D. Number')
  await user.type(field('I.D. Number'), '9001010000080')
  await user.click(screen.getByRole('button', { name: /save member/i }))
  await user.click(await screen.findByRole('button', { name: /send welcome letter/i }))
}

describe('Capture Member · Workplace -- what a save sends, and a refusal', () => {
  it('🔑 the chosen workplace reaches the server by its id, and by nothing else (BR66)', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseHeadOffice(user)
    await captureTheRest(user)

    await waitFor(() => expect(posted).toBeDefined())
    expect(posted.workplaceId).toBe(HEAD_OFFICE.id)
    expect(posted).not.toHaveProperty('workplace')
    expect(posted).not.toHaveProperty('workplaceName')
  })

  it('the must-pass: a capture with no workplace sends null for it -- which the server reads as none', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await captureTheRest(user)

    await waitFor(() => expect(posted).toBeDefined())
    expect(posted.workplaceId).toBeNull()
  })

  it('🔴 a save refused WORKPLACE_UNKNOWN is said at the Workplace field, in the server\'s words -- the form kept', async () => {
    // The server's own answer to a save naming a workplace it does not have (month-end-examples.json).
    server.use(monthEndRefusal('WORKPLACE_UNKNOWN', 'POST /api/members'))
    const sentence = 'That workplace is not on record here. Choose another.'
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseHeadOffice(user)
    await captureTheRest(user)

    // Said in the dialog it happened in first...
    const dialog = await screen.findByRole('dialog', { name: 'Welcome Letter Preview' })
    expect(await within(dialog).findByText(sentence)).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    // ...and then at the field, on the tab that holds it, with everything typed still there.
    const workplace = screen.getByRole('group', { name: 'Workplace' })
    expect(within(workplace).getByRole('alert')).toHaveTextContent(sentence)
    expect(within(workplace).getByText('INVENTED HEAD OFFICE')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Employment & Bank/ })).toHaveTextContent('(has errors)')
    await user.click(screen.getByRole('button', { name: /^Personal Information/ }))
    expect(screen.getByLabelText(/^Surname/)).toHaveValue('Testperson')

    // Removing it clears the refusal with it: a member may be captured with no workplace.
    await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
    await user.click(screen.getByRole('button', { name: 'Remove INVENTED HEAD OFFICE' }))
    expect(screen.queryByText(sentence)).not.toBeInTheDocument()
  })
})
