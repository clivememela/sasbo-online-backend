// @vitest-environment jsdom -- the branch dialog while its save is running
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import BankBranchModal from './BankBranchModal'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 R2-F3 (P9's independent review): THE BRANCH DIALOG STAYED DISMISSIBLE WHILE ITS SAVE RAN.
 *
 * Save was `loading`, and Escape, the scrim, the X and Cancel all still worked. Closed mid-update, `BankBranch`'s
 * `onUpdated` then called `setSelectedBank(updated)` when the write landed -- and REOPENED the dialog on its own.
 *
 * Driven through Add; the primitive is the same `Modal` for Edit (A29's file drives Edit since that defect's fix).
 */

async function startSaving({ answer }) {
  server.use(http.post('*/api/banks/branches', answer))
  const onClose = vi.fn()
  const user = setupUser()
  renderWithProviders(<BankBranchModal onClose={onClose} onCreated={() => {}} />)
  const dialog = screen.getByRole('dialog', { name: 'Add Bank/Branch Details' })
  await user.type(within(dialog).getByLabelText(/^Bank Name/), 'Synthetic Bank')
  await user.type(within(dialog).getByLabelText(/^Branch \*/), 'Sample Branch')
  await user.click(within(dialog).getByRole('button', { name: 'Save' }))
  return { user, dialog, onClose }
}

describe('BankBranchModal, while a save runs (R2-F3)', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('Escape does not close it, and Cancel is disabled', async () => {
    const { user, dialog, onClose } = await startSaving({ answer: () => new Promise(() => {}) })

    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('the must-pass: after a refused save, Escape closes it again', async () => {
    const { user, dialog, onClose } = await startSaving({
      answer: () => HttpResponse.json({ status: 400, message: 'Branch code is required.' }, { status: 400 }),
    })

    expect(await within(dialog).findByText('This branch was not saved. Branch code is required.')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })
})
