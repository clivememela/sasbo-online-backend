// @vitest-environment jsdom -- what the branch dialog does when the bank it is showing is replaced
import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'

import BankBranchModal from './BankBranchModal'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * 🔴 WRITTEN BECAUSE A DELETION PROOF FOUND NOTHING WATCHING. `10.4`, batch 7.
 *
 * The dialog reseeds its form, leaves edit mode and clears its field errors whenever `selectedBank` is replaced --
 * after a save, `BankBranch.jsx` hands back a NEW object for the SAME bank. That was an effect; `10.4` moved it into
 * render. To prove the move preserved it, the reseed was deleted outright and the suite run: **every test stayed
 * green.** `BankBranchModal.a29` and `.busy` exercise opening and saving, and neither looks at what happens when the
 * bank underneath the dialog is replaced. So the behaviour being moved had no witness, and "the tests pass" would
 * have meant nothing about it.
 *
 * This is that witness. The branch carries a STRING address and `branch` (the DTO's spelling): what this file
 * measures is the reseed, and `BankBranchModal.a29.test.jsx` holds the API's own shape.
 */

const SANDTON = {
  id: 1,
  bankName: 'Synthetic Bank',
  branch: 'Sandton',
  branchCode: '632005',
  physicalAddress: '15 Alice Lane, Sandown, Sandton, 2196',
  postalAddress: 'PO Box 1, Sandton, 2146',
}

describe('the branch dialog, when the bank it shows is replaced', () => {
  it('leaves edit mode and shows the replacement, not the form it was editing', async () => {
    const user = setupUser()
    const { rerender } = renderWithProviders(<BankBranchModal selectedBank={SANDTON} onClose={() => {}} />)

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    expect(await screen.findByRole('dialog', { name: 'Edit Bank/Branch Details' })).toBeInTheDocument()

    // What `onUpdated` does in the app: the SAME bank, as a NEW object, as the server now has it. A
    // `rerender` rather than a button, because an open dialog hides everything outside it.
    rerender(<BankBranchModal selectedBank={{ ...SANDTON, branch: 'Sandton City' }} onClose={() => {}} />)

    const dialog = await screen.findByRole('dialog', { name: 'Bank Details' })
    expect(within(dialog).getByText('Sandton City')).toBeInTheDocument()
  })

  it('the must-pass: a bank that is NOT replaced stays in edit mode while you edit it', async () => {
    // 🔑 Without this, a dialog that dropped out of edit mode on EVERY render would pass above.
    const user = setupUser()
    renderWithProviders(<BankBranchModal selectedBank={SANDTON} onClose={() => {}} />)

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    await user.type(screen.getByLabelText(/^Branch \*/), ' North')

    expect(screen.getByRole('dialog', { name: 'Edit Bank/Branch Details' })).toBeInTheDocument()
    expect(screen.getByLabelText(/^Branch \*/)).toHaveValue('Sandton North')
  })
})
