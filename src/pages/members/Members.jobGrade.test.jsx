// @vitest-environment jsdom -- renders the member record panel
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, waitFor, within } from '@testing-library/react'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * THE JOB GRADE ON A MEMBER'S RECORD -- shown, kept, and cleared only when the bank changes.
 *
 * 🔴 WHAT MAIN'S e56dbe4 DID. "Reset job grade when bank changes in forms" moved the reset to
 * the top of the effect that watches `editForm.bankName`. An effect cannot tell WHY a value
 * changed, and OPENING a record copies the member into `editForm` -- `bankName` goes from
 * nothing to the member's bank, and the reset blanks the grade. The record panel renders the
 * grade from `editForm`, so every member's Job Grade read "—".
 *
 * ⚠️ WHAT IT DID NOT DO, and this file first claimed it did. Pressing Edit Member copies the
 * member into the form AGAIN with the same bank, so the effect does not re-fire and the grade
 * comes back; a save keeps the stored value. The first version of this test asserted a loss
 * on that path and passed against the broken code -- which is how it was learned that the
 * defect is the DISPLAY, not the save. The automation arc read it the same way independently.
 *
 * Three cases, because a reset has two ways to be wrong:
 *   ① the red one -- the read-only record shows the saved grade;
 *   ② must-pass -- opening Edit Member keeps it;
 *   ③ must-pass -- CHANGING the bank still clears it, which is what main set out to do.
 *      Without ③, deleting the reset outright would pass ① and ② and quietly undo main's fix.
 */
const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Gradeholder',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
  bankName: 'ABSA',
  jobGrade: 'G7',
}

const GRADES = [
  { value: 'G7', label: 'G7' },
  { value: 'G8', label: 'G8' },
]

async function openEmployment(user) {
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  await user.click(await screen.findByRole('button', { name: /Employment & Bank/i }))
}

describe('the job grade on a member record', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
    server.use(...pageFixtures)
    server.use(
      ...registerHandlers([MEMBER]),
      http.get('*/api/job-grades', () => HttpResponse.json(GRADES)),
      http.get('*/api/banks', () => HttpResponse.json([{ name: 'ABSA' }, { name: 'Capitec' }])),
    )
  })

  it('① shows the saved grade on the read-only record', async () => {
    const user = setupUser()
    await openEmployment(user)

    const panel = await screen.findByTestId('member-panel')
    const label = await within(panel).findByText('Job Grade')
    await waitFor(() =>
      expect(
        label.nextElementSibling,
        "the record shows no job grade for a member who has one -- a reset meant for a " +
          'CHANGE of bank is firing when the member is loaded into the form',
      ).toHaveTextContent('G7'),
    )
  })

  it('② keeps the saved grade when Edit Member opens', async () => {
    const user = setupUser()
    await openEmployment(user)
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))

    const grade = await screen.findByLabelText(/^Job Grade/i)
    // Wait for the grade list to arrive first, so the value is read after the effect has run.
    await within(grade).findByRole('option', { name: 'G8' })
    expect(grade).toHaveValue('G7')
  })

  it('③ still clears the grade when the bank is changed', async () => {
    const user = setupUser()
    await openEmployment(user)
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))

    const bank = await screen.findByLabelText(/^Bank Where Employed/i)
    await within(bank).findByRole('option', { name: 'Capitec' })
    await user.selectOptions(bank, 'Capitec')

    await waitFor(() => expect(screen.getByLabelText(/^Job Grade/i)).toHaveValue(''))
  })
})
