// @vitest-environment jsdom -- renders the member record's Employment & Bank tab in edit mode
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { BRANCH_OPTIONS } from '../../config/memberConstants'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * EDIT MEMBER'S BANK BRANCH FOLLOWS THE SAME RULE AS CAPTURE MEMBER'S.
 *
 * One rule on both screens, decided 2026-09-17: the chosen bank's own branches together with the fourteen names, and
 * a branch the new bank does not offer is cleared when the bank changes. Two screens holding the same member with two
 * different branch rules is the shape of A6 and A7 -- the vocabulary drifting between Capture and Edit.
 */

const ABSA = { id: 1, bankName: 'Absa Bank Limited', name: 'Absa Bank Limited', branchName: 'Sandton City', branchCode: '632005', universalBranchCode: '632005' }
const CAPITEC = { id: 2, bankName: 'Capitec Bank Limited', name: 'Capitec Bank Limited', branchName: 'Menlyn Maine', branchCode: '470010', universalBranchCode: '470010' }

const MEMBER = {
  id: 7, membershipNumber: 'M0007', firstNames: 'Synthetic', surname: 'Alpha', initials: 'S',
  identityType: 'I.D. Number', identityNumber: '9001010000080', status: 'Active', paymentType: 'Payroll Deduction',
  bankName: 'Absa Bank Limited', branch: 'Sandton City',
}

let saved

beforeEach(() => {
  saved = undefined
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    ...registerHandlers([MEMBER]),
    http.get('*/api/banks', () => HttpResponse.json([ABSA, CAPITEC])),
    http.get('*/api/banks/branches', () => HttpResponse.json([ABSA, CAPITEC])),
    http.put('*/api/members/:id', async ({ request }) => {
      saved = await request.json()
      return HttpResponse.json(saved)
    }),
    ...pageFixtures,
  )
})

const panel = () => within(screen.getByTestId('member-panel'))

async function editEmployment(user) {
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  await user.click(await screen.findByRole('button', { name: /^Employment/i }))
}

const optionTexts = (select) => [...select.querySelectorAll('option')].map((o) => o.textContent.trim())

describe('Edit Member · Bank Branch', () => {
  it("offers the member's bank's own branches together with the fourteen names, and not another bank's", async () => {
    const user = setupUser()
    await editEmployment(user)

    const branch = await panel().findByLabelText(/^Bank Branch/)
    await waitFor(() => expect(optionTexts(branch)).toContain('Sandton City'))
    expect(optionTexts(branch)).toEqual(expect.arrayContaining(BRANCH_OPTIONS))
    expect(optionTexts(branch)).not.toContain('Menlyn Maine')
  })

  it('changing the bank clears a branch the new bank does not offer -- in what is SAVED, not only on screen', async () => {
    const user = setupUser()
    await editEmployment(user)
    await waitFor(() => expect(optionTexts(panel().getByLabelText(/^Bank Branch/))).toContain('Sandton City'))

    await user.selectOptions(panel().getByLabelText(/^Bank Where Employed/), 'Capitec Bank Limited')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({ bankName: 'Capitec Bank Limited', branch: '' })
  })

  it('the must-pass: a branch the new bank DOES offer is kept', async () => {
    const user = setupUser()
    server.use(...registerHandlers([{ ...MEMBER, branch: 'Rosebank' }]))
    await editEmployment(user)
    await waitFor(() => expect(optionTexts(panel().getByLabelText(/^Bank Branch/))).toContain('Sandton City'))

    await user.selectOptions(panel().getByLabelText(/^Bank Where Employed/), 'Capitec Bank Limited')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({ bankName: 'Capitec Bank Limited', branch: 'Rosebank' })
  })
})
