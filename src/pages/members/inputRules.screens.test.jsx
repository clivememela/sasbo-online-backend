// @vitest-environment jsdom -- types into Capture Member and Edit Member
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerHandlers } from '../../test/msw/memberRegister'
import { setupUser } from '../../test/user'

/**
 * INPUT VALIDATION ON BOTH MEMBER SCREENS -- `utils/inputRules.js`, wired.
 *
 * The owner, 2026-09-17: "Work Tel should not take letters -- only digits! Same for Home Tel, same for Bank
 * Account Number ... For Initials it would not make sense for numbers." The rules live in one module; these
 * tests are about the WIRING, and about four ways the first wiring was wrong:
 *   - a message with nowhere to render (no `error` prop), so it reached only the summary;
 *   - a HIDDEN field (Bank Account Number under Payroll) blocking a save nobody could fix;
 *   - Edit Member refusing a legacy value the administrator never touched;
 *   - a numbers-only keypad (`kind="identifier"`) on Employee Number and a passport number, both of which
 *     carry letters.
 */

let posted

beforeEach(() => {
  posted = undefined
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    ...pageFixtures,
    http.post('*/api/members', async ({ request }) => {
      posted = await request.json()
      return HttpResponse.json({ id: 1, ...posted })
    }),
  )
})

const label = (text) => new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`)

describe('Capture Member', () => {
  const field = (text) => screen.getByLabelText(label(text))
  const openTab = (user, name) => user.click(screen.getByRole('button', { name }))
  const retype = async (user, text, value) => {
    await user.clear(field(text))
    await user.type(field(text), value)
  }
  const fillValidPayrollMember = async (user) => {
    await retype(user, 'First Name(s)', 'Synthetic')
    await retype(user, 'Surname', 'Testperson')
    await retype(user, 'Personal Email', 'synthetic@example.test')
    await user.selectOptions(field('Identity Type'), 'I.D. Number')
    await retype(user, 'I.D. Number', '9001010000080')
  }

  it('phones refuse letters and Initials refuse digits as they are typed; Employee Number keeps its letters', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await screen.findByLabelText(/^Work Tel/)

    await retype(user, 'Work Tel', '011abc555 0001')
    await retype(user, 'Home Tel', '+27 (11) x555-0002')
    await retype(user, 'Initials', 'T2S')
    expect(field('Work Tel')).toHaveValue('011555 0001')
    expect(field('Home Tel')).toHaveValue('+27 (11) 555-0002')
    expect(field('Initials')).toHaveValue('TS')

    await openTab(user, /Employment & Bank/i)
    await retype(user, 'Employee Number', 'EMP-10001')
    expect(field('Employee Number'), 'an employee number is a reference, not a quantity').toHaveValue('EMP-10001')
  })

  it('🔴 a Work Tel too short to dial is refused at save, with the message under the field itself', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await screen.findByLabelText(/^Work Tel/)
    await fillValidPayrollMember(user)
    await openTab(user, /Employment & Bank/i)
    await user.selectOptions(field('Payment Type'), 'Payroll Deduction')
    await openTab(user, /Personal Information/i)
    await retype(user, 'Work Tel', '0821')

    await user.click(screen.getByRole('button', { name: /save member/i }))

    expect(posted).toBeUndefined()
    await waitFor(() => expect(field('Work Tel')).toHaveAttribute('aria-invalid', 'true'))
    expect(screen.getAllByText(/too short/i).length, 'under the field AND in the summary').toBeGreaterThanOrEqual(2)
  })

  it('🔴 a Bank Account Number hidden by Payroll never blocks the save', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await screen.findByLabelText(/^Work Tel/)
    await fillValidPayrollMember(user)
    await openTab(user, /Employment & Bank/i)
    await user.selectOptions(field('Payment Type'), 'Debit Order')
    await retype(user, 'Bank Account Number', '123')
    await user.selectOptions(field('Payment Type'), 'Payroll Deduction')

    await user.click(screen.getByRole('button', { name: /save member/i }))
    const sendBtn = await screen.findByRole('button', { name: /send welcome letter/i })
    await user.click(sendBtn)

    await waitFor(() => expect(posted, 'a stale value in a field nobody can see blocked the save').toBeDefined())
  })

  it('a numbers-only keypad is offered for an I.D. Number, never for a passport number or an employee number', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await user.selectOptions(await screen.findByLabelText(/^Identity Type/), 'I.D. Number')
    expect(field('I.D. Number')).toHaveAttribute('inputmode', 'numeric')

    await user.selectOptions(field('Identity Type'), 'Passport Number')
    expect(field('Passport Number')).not.toHaveAttribute('inputmode', 'numeric')

    await openTab(user, /Employment & Bank/i)
    expect(field('Employee Number')).not.toHaveAttribute('inputmode', 'numeric')
  })
})

describe('Capture Member · names start with a capital (SASBO, 18 Sep 2026)', () => {
  const field = (text) => screen.getByLabelText(label(text))

  it('🗣️ a name is given its capital as the field is LEFT, never under the caret', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await screen.findByLabelText(/^First Name\(s\)/)

    await user.type(field('First Name(s)'), 'zola thabo')
    expect(field('First Name(s)'), 'nothing moves while it is being typed').toHaveValue('zola thabo')
    await user.tab()
    expect(field('First Name(s)')).toHaveValue('Zola Thabo')
    expect(field('Initials'), 'the derived initials were capitals already').toHaveValue('ZT')

    await user.type(field('Surname'), 'janse van rensburg')
    await user.tab()
    expect(field('Surname')).toHaveValue('Janse van Rensburg')
  })

  it('🔑 and what was typed with its own capitals is left exactly as typed', async () => {
    // The automation suite's surnames are read back and cleaned up by exact match; a real name like McDonald is its
    // own. Raising first letters is all the rule may do.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await screen.findByLabelText(/^Surname/)

    await user.type(field('Surname'), 'ZZ-AT-member-abq')
    await user.tab()
    expect(field('Surname')).toHaveValue('ZZ-AT-member-abq')

    await user.clear(field('Surname'))
    await user.type(field('Surname'), 'McDonald')
    await user.tab()
    expect(field('Surname')).toHaveValue('McDonald')
  })
})

describe('Edit Member', () => {
  const ALPHA = {
    id: 7, membershipNumber: 'M0007', firstNames: 'Synthetic', surname: 'Alpha', initials: 'S',
    identityType: 'I.D. Number', identityNumber: '9001010000080', status: 'Active', paymentType: 'Payroll Deduction',
    // A legacy value no rule written today would accept.
    homeTel: '123',
  }
  const BRAVO = { ...ALPHA, id: 8, membershipNumber: 'M0008', surname: 'Bravo', identityNumber: '8506155000084' }
  const panel = () => within(screen.getByTestId('member-panel'))

  async function editAlpha(user) {
    renderWithProviders(<MembersPage />)
    await user.click(await screen.findByRole('button', { name: /open the record for.*Alpha/i }))
    await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  }

  beforeEach(() => {
    server.use(
      ...registerHandlers([ALPHA, BRAVO]),
      http.put('*/api/members/:id', async ({ request }) => HttpResponse.json(await request.json())),
    )
  })

  it('🔑 a legacy value nobody touched does not stop a different change being saved', async () => {
    const user = setupUser()
    await editAlpha(user)
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    expect(await screen.findByRole('dialog', { name: 'Save these changes?' })).toBeInTheDocument()
  })

  it('🔴 a Home Tel the administrator changes to something too short is refused, under the field', async () => {
    const user = setupUser()
    await editAlpha(user)
    const homeTel = await panel().findByLabelText(/^Home Tel/)
    await user.clear(homeTel)
    await user.type(homeTel, '0821abc')
    expect(homeTel, 'letters refused as typed').toHaveValue('0821')

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument()
    await waitFor(() => expect(panel().getByLabelText(/^Home Tel/)).toHaveAttribute('aria-invalid', 'true'))
  })

  it('🗣️ a name is given its capital as the field is left -- the same rule as capture (SASBO, 18 Sep 2026)', async () => {
    const user = setupUser()
    await editAlpha(user)
    const surname = await panel().findByLabelText(/^Surname/)
    await user.clear(surname)
    await user.type(surname, 'van der merwe')
    expect(surname, 'nothing moves under the person typing').toHaveValue('van der merwe')

    await user.tab()
    expect(surname).toHaveValue('Van der Merwe')
  })

  it('the must-pass: leaving a name that already has its capital is not an edit', async () => {
    // The record counts an edit by serialising the form against its baseline, so the rule must leave a proper name
    // byte-for-byte alone -- a rule that trimmed, collapsed or re-cased anything would turn every focus-and-leave into
    // "Save these changes?" for a change nobody made.
    const user = setupUser()
    await editAlpha(user)
    await user.click(await panel().findByLabelText(/^Surname/))
    await user.tab()

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    expect(await screen.findByText('Nothing to save')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument()
  })
})
