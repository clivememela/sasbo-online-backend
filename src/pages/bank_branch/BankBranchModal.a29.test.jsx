// @vitest-environment jsdom -- renders the branch dialog inside the route's error boundary
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { flushSync } from 'react-dom'
import { http, HttpResponse } from 'msw'

import BankBranchModal from './BankBranchModal'
import { bankService } from '../../services/bankService.js'
import ErrorBoundary from '../../components/common/ErrorBoundary'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * A29, FIXED: editing a bank branch crashed the screen.
 *
 * The dialog used the API's record AS its form. `GET /api/banks/branches` answers the ENTITY -- the branch's name as
 * `branchName`, the physical address as an OBJECT -- so *Edit* handed the object to `AddressAutocomplete`, whose
 * `.trim()` threw, and the screen fell to its error page (the boundary beside the outlet kept the shell standing).
 * The Branch field read `branch`, which the record does not have, so even a form that had opened would have been
 * refused for a missing branch. Now `branchForm.js` converts the record once on the way in and the form once on the
 * way out.
 *
 * 🔑 THE FIXTURE IS THE API'S SHAPE, NOT A GUESS: `branchName`, and the address as the entity embeds it, its suburb
 * and city as `{id, name}` rows. The boundary is rendered here the way `Main.jsx` renders it, so a crash would show
 * as the page a user sees -- and no test below may meet it.
 */
const BRANCH = {
  id: 1,
  bankName: 'Absa Bank Limited',
  branchName: 'Sandton',
  branchCode: '632005',
  physicalAddress: {
    complexOrBuilding: null,
    streetNumber: '15',
    streetName: 'Alice Lane',
    suburb: { id: 3, name: 'Sandown' },
    city: { id: 2, name: 'Sandton' },
    province: null,
    postalCode: '2196',
  },
  postalAddress: 'PO Box 1, Sandton, 2146',
  tableCode: 12,
  branchStaffComplement: 40,
}

function renderBranch({ onUpdated = () => {} } = {}) {
  const errors = []
  renderWithProviders(
    <ErrorBoundary onError={(error) => errors.push(error)}>
      <BankBranchModal selectedBank={BRANCH} onClose={() => {}} onUpdated={onUpdated} />
    </ErrorBoundary>,
  )
  return errors
}

async function openEdit(user) {
  await user.click(await screen.findByRole('button', { name: 'Edit' }))
  return screen.findByRole('dialog', { name: 'Edit Bank/Branch Details' })
}

describe('editing a branch whose record arrives in the API\'s shape (A29)', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('the read-only view names the branch and prints the address as one line', async () => {
    renderBranch()
    const dialog = await screen.findByRole('dialog', { name: 'Bank Details' })

    // `branch` was read, which the record does not carry: the view said "N/A" for every branch.
    expect(within(dialog).getByText('Sandton')).toBeInTheDocument()
    expect(within(dialog).getByText('15, Alice Lane, Sandown, Sandton, 2196')).toBeInTheDocument()
  })

  it('🔴 Edit opens a form: the address as editable text, the branch by its name', async () => {
    const user = setupUser()
    const errors = renderBranch()

    const dialog = await openEdit(user)

    expect(within(dialog).getByRole('combobox', { name: 'Physical Address' })).toHaveValue('15, Alice Lane, Sandown, Sandton, 2196')
    expect(within(dialog).getByLabelText(/^Branch \*/)).toHaveValue('Sandton')
    expect(within(dialog).getByLabelText(/^Table Code/)).toHaveValue(12)
    expect(screen.queryByText('This page could not be displayed.')).toBeNull()
    expect(errors).toEqual([])
  })

  /*
   * 🔴 EDIT SAVED THE BRANCH AS IT WAS, AND SHUT THE FORM AGAIN -- found by the merged gate's journey (run 37680224612:
   * two PUTs of the old manager, the first sent by Edit, neither by Save). Edit (`type="button"`) and Save Changes
   * (`type="submit"`, the form's by `form=`) stood in one place in the footer, so React kept ONE `<button>` and changed
   * its type. A browser settles React's update between a click's listeners and the button's own default action (a
   * microtask checkpoint after each listener), so by then the button pressed as Edit was the submit button of a form
   * now on screen: Edit sent the record unchanged, and the answer put the dialog back to Bank Details, dropping what
   * had been typed meanwhile. jsdom runs the listeners and the default action in one go, so the test settles React
   * where the browser does -- in a listener after React's own.
   */
  it('🔴 Edit opens the form and saves nothing; the manager then typed is the one saved and shown', async () => {
    const sent = []
    server.use(http.put('*/api/banks/branches/1', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({ ...BRANCH, managerName: sent.at(-1).managerName })
    }))
    const update = vi.spyOn(bankService, 'updateBank')
    const settleAsABrowserDoes = () => flushSync(() => {})
    document.addEventListener('click', settleAsABrowserDoes)
    try {
      const user = setupUser()
      renderBranch()
      await user.click(await screen.findByRole('button', { name: 'Edit' }))

      expect(update, 'a save sent by pressing Edit').not.toHaveBeenCalled()
      const dialog = await screen.findByRole('dialog', { name: 'Edit Bank/Branch Details' })
      expect(within(dialog).getByRole('button', { name: 'Save Changes' })).toBeEnabled()

      await user.type(within(dialog).getByLabelText(/^Manager Name/), 'Synthetic Manager')
      await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

      const details = await screen.findByRole('dialog', { name: 'Bank Details' })
      expect(within(details).getByText('Synthetic Manager')).toBeInTheDocument()
      expect(sent.map((body) => body.managerName)).toEqual(['Synthetic Manager'])
    } finally {
      document.removeEventListener('click', settleAsABrowserDoes)
    }
  })

  it('Save Changes sends the shape the server reads: the branch by name, numbers as numbers, the address left out when unchanged', async () => {
    let sent
    server.use(http.put('*/api/banks/branches/1', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json({ ...BRANCH, managerName: 'Synthetic Manager' })
    }))
    const onUpdated = vi.fn()
    const user = setupUser()
    renderBranch({ onUpdated })
    const dialog = await openEdit(user)

    await user.type(within(dialog).getByLabelText(/^Manager Name/), 'Synthetic Manager')
    await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

    expect(await screen.findByRole('dialog', { name: 'Bank Details' })).toBeInTheDocument()
    expect(sent).toMatchObject({
      bankName: 'Absa Bank Limited', branch: 'Sandton', branchCode: '632005', managerName: 'Synthetic Manager',
      tableCode: 12, branchStaffComplement: 40, subsValueBranch: null, postalAddress: 'PO Box 1, Sandton, 2146',
    })
    // The server keeps the stored address -- its suburb and city rows -- for a save that names none.
    expect(sent).not.toHaveProperty('physicalAddress')
    expect(sent).not.toHaveProperty('branchName')
    expect(onUpdated).toHaveBeenCalledWith(expect.objectContaining({ id: 1, branchName: 'Sandton', managerName: 'Synthetic Manager' }))
  })

  it('a changed address is sent as the line typed', async () => {
    let sent
    server.use(http.put('*/api/banks/branches/1', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json({ ...BRANCH, physicalAddress: { streetName: '1 Synthetic Street' } })
    }))
    const user = setupUser()
    renderBranch()
    const dialog = await openEdit(user)

    const address = within(dialog).getByRole('combobox', { name: 'Physical Address' })
    await user.clear(address)
    await user.type(address, '1 Synthetic Street')
    await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

    expect(await screen.findByRole('dialog', { name: 'Bank Details' })).toBeInTheDocument()
    expect(sent.physicalAddress).toBe('1 Synthetic Street')
  })

  it('a refused save says why in a sentence, keeps the form, and marks the field', async () => {
    server.use(http.put('*/api/banks/branches/1', () => HttpResponse.json({
      status: 409,
      code: 'BRANCH_CODE_TAKEN',
      message: 'Another Absa Bank Limited branch already has branch code 632005. Each branch of a bank has its own code.',
    }, { status: 409 })))
    const user = setupUser()
    renderBranch()
    const dialog = await openEdit(user)

    await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The change to this branch was not saved. Another Absa Bank Limited branch already has branch code 632005. '
      + 'Each branch of a bank has its own code.')
    expect(within(dialog).getByLabelText(/^Branch \*/), 'still the form, as it was typed').toHaveValue('Sandton')
  })

  it('a field the server refuses is named under that field and in the sentence', async () => {
    server.use(http.put('*/api/banks/branches/1', () => HttpResponse.json({
      status: 400,
      message: 'Validation failed for the incoming request payload.',
      validationErrors: { branchCode: 'Branch Code is at most 6 characters.' },
    }, { status: 400 })))
    const user = setupUser()
    renderBranch()
    const dialog = await openEdit(user)

    await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

    expect(await within(dialog).findByText('The change to this branch was not saved. Branch Code is at most 6 characters.'))
      .toBeInTheDocument()
    expect(within(dialog).getByLabelText(/^Branch Code/)).toHaveAccessibleDescription('Branch Code is at most 6 characters.')
  })

  it('a save that cannot be confirmed says so, and to check before trying again', async () => {
    server.use(http.put('*/api/banks/branches/1', () => HttpResponse.json({ status: 500 }, { status: 500 })))
    const user = setupUser()
    renderBranch()
    const dialog = await openEdit(user)

    await user.click(within(dialog).getByRole('button', { name: 'Save Changes' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The change to this branch could not be confirmed. Reload the branch list and check before trying again.')
  })
})
