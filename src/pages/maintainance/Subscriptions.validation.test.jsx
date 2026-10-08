// @vitest-environment jsdom -- Subscriptions: an emptied debit day, and what ACTIVE decides
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Subscriptions from './Subscriptions'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'

/**
 * THE MANUAL'S READING OF MAINTENANCE (6 Oct), TWO FINDINGS.
 *
 * ① An emptied debit day was `parseInt('')` -- NaN -- which JSON writes as null, which the server's whole-number field
 *   took as 0 and SAVED; the row then read "NaN". The field is held as typed and checked before anything is sent.
 * ② ACTIVE decides whether the debit order run collects at that bank, and nothing on the screen said so.
 */
const SUB = {
  id: 3, bankName: 'Synthetic Bank', acbDebitDay: 25, subscriptionAmount: 174, isCoreBanking: false,
  status: 'ACTIVE', registrationDate: '2026-01-15',
}

let sent

async function editTheRow() {
  const user = setupUser()
  renderWithProviders(
    <TestAuthProvider permissions={[PERMISSIONS.SUBSCRIPTIONS_MAINTAIN]}><Subscriptions /></TestAuthProvider>,
  )
  await user.click(await screen.findByRole('button', { name: 'Edit the subscription for Synthetic Bank' }))
  return user
}

describe('Subscriptions -- a row is saved only with what the debit order run can use', () => {
  beforeEach(() => {
    sent = undefined
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(
      http.get('*/api/subscriptions', () => HttpResponse.json([SUB])),
      http.get('*/api/banks', () => HttpResponse.json([{ name: 'Synthetic Bank' }])),
      http.put('*/api/subscriptions/3', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(sent)
      }),
    )
  })

  it('🔴 an emptied debit day is refused in words under the field, and nothing is sent', async () => {
    const user = await editTheRow()
    await user.clear(screen.getByLabelText('Debit order day for Synthetic Bank'))
    await user.click(screen.getByRole('button', { name: 'Save changes to Synthetic Bank' }))

    expect(await screen.findByText('Debit day must be between 1 and 31.')).toBeInTheDocument()
    expect(screen.getByLabelText('Debit order day for Synthetic Bank')).toHaveAttribute('aria-invalid', 'true')
    expect(sent, 'no request with a day of 0').toBeUndefined()
    expect(screen.queryByText('NaN')).toBeNull()
  })

  it('a day past the 31st and an amount of R0 are refused the same way', async () => {
    const user = await editTheRow()
    const day = screen.getByLabelText('Debit order day for Synthetic Bank')
    await user.clear(day)
    await user.type(day, '32')
    const amount = screen.getByLabelText('Subscription amount for Synthetic Bank')
    await user.clear(amount)
    await user.type(amount, '0')
    await user.click(screen.getByRole('button', { name: 'Save changes to Synthetic Bank' }))

    expect(await screen.findByText('Debit day must be between 1 and 31.')).toBeInTheDocument()
    expect(screen.getByText('Subscription amount must be more than R0.')).toBeInTheDocument()
    expect(sent).toBeUndefined()
  })

  it('the must-pass: a corrected day is sent as a number, and the row shows it', async () => {
    const user = await editTheRow()
    const day = screen.getByLabelText('Debit order day for Synthetic Bank')
    await user.clear(day)
    await user.type(day, '28')
    await user.click(screen.getByRole('button', { name: 'Save changes to Synthetic Bank' }))

    await waitFor(() => expect(sent).toBeDefined())
    expect(sent).toMatchObject({ acbDebitDay: 28, subscriptionAmount: 174 })
    const row = (await screen.findByText('Synthetic Bank')).closest('tr')
    expect(within(row).getByText('28')).toBeInTheDocument()
  })

  it('🔴 says what ACTIVE decides: the run collects at the bank only while its row is ACTIVE', async () => {
    await editTheRow()

    expect(screen.getByText(/Debit orders are collected at a bank only while its row is/)).toHaveTextContent(
      'Debit orders are collected at a bank only while its row is ACTIVE. Any other status leaves that bank’s members '
      + 'out of the debit order run, and a bank with two ACTIVE rows stops the run until one is changed.')
    expect(screen.getByLabelText('Status for Synthetic Bank')).toHaveAccessibleDescription(/only while its row is ACTIVE/)
  })
})
