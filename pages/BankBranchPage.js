import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * The bank/branch screen at `/banks` -- the full branch table, its four
 * filters, the "Add Bank / Branch" button, and the modal a row opens.
 *
 * ⚠️ BEFORE THE DESIGN CLIENT THIS SCREEN COULD NOT REPORT A FAILURE.
 * `getBanks()` was called with no `.catch`, so a failed request left the list
 * empty and the screen rendered "No banks match your search." — the *confident
 * wrong answer*: it told an administrator there is no data when the backend is
 * down. That is why the failure scenario belongs on the listing screen, which
 * already had a real error state. The design client gives this screen one too
 * ("We could not load the bank branches."); nothing here asserts it yet.
 *
 * THE MODAL IS THREE SCREENS WEARING ONE COMPONENT, and the heading is the only
 * reliable way to tell which one you are on:
 *
 *   "Bank Details"                 read-only, opened by clicking a row
 *   "Edit Bank/Branch Details"     after pressing Edit
 *   "Add Bank/Branch Details"      after pressing + Add Bank/Branch
 *
 * The buttons differ too -- Close/Edit in the first, Cancel/Save Changes in the
 * second, Cancel/Save in the third -- so a helper that assumed one set would
 * work in exactly one of the three.
 */
export class BankBranchPage extends BasePage {
  constructor(page) {
    super(page, '/banks')
  }

  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Bank / Branch', exact: true })).toBeVisible()
  }




  /**
   * Open one branch's record from the register.
   *
   * 🔴 THE RANDOM ACCESS NUMBER IS A FILTER NOW, NOT A CELL. The design client lists Random
   * Access Number, Manager, Phone, Fax and Email only in a row's expanded detail on a narrow
   * screen, so on a desk the number cannot be seen and cannot be clicked. What CAN be clicked
   * is the bank name, a real button named "<bank> — open the <branch> record". The filter
   * narrows the register; the button, named by both bank AND branch, picks the one row -- the
   * filter matches as a substring, so the name is what makes the choice exact.
   *
   * @param {{ bankName: string, branchName: string, randomAccessNumber: string }} branch
   */
  async openBranch(branch) {
    await this.page.getByLabel('Random access number', { exact: true }).fill(branch.randomAccessNumber)
    const open = this.page.getByRole('button', {
      name: `${branch.bankName} — open the ${branch.branchName} record`,
      exact: true,
    })
    await expect(open).toHaveCount(1)
    await open.click()
    await expect(this.page.getByRole('heading', { name: 'Bank Details' })).toBeVisible()
    return this
  }

  /**
   * A labelled value inside the read-only modal.
   *
   * The read-only view is NOT a form -- it renders `<label>` beside a `<p>`,
   * with no control between them, so `getByLabel` finds nothing. The value has
   * to be located through the label's own container instead. Worth stating,
   * because the obvious call fails in a way that reads as a missing element.
   */
  detail(label) {
    return this.page.locator('div').filter({ has: this.page.getByText(label, { exact: true }) }).last().locator('p')
  }

  async expectDetail(label, value) {
    await expect(this.detail(label)).toHaveText(String(value))
    return this
  }

  /**
   * Press Edit on the open record. It asserts nothing itself: `expectEditForm` says what came of it.
   *
   * ✅ Was A29. Until the October build what came of it was a crash -- the record's address OBJECT reached a text field
   * that called `.trim()` on it, and the screen fell to its error page while the shell around it stood. Client
   * `oct/bugs` (5bc3fc1) converts the record to the form's strings, and Edit opens the form.
   */
  async clickEdit() {
    await this.page.getByRole('dialog', { name: 'Bank Details' }).getByRole('button', { name: 'Edit', exact: true }).click()
    return this
  }

  /** The dialog in edit mode, by the title it carries then. */
  editForm() {
    return this.page.getByRole('dialog', { name: 'Edit Bank/Branch Details' })
  }

  /** A field of the edit form, by the label above it -- "Bank Name *" is "Bank Name" here (`labelPattern`). */
  formField(label) {
    return this.editForm().getByLabel(labelPattern(label))
  }

  /**
   * The edit form is open and holds the branch it was opened on -- its bank, and its branch, which the old form left
   * empty for every branch (it read `branch` where the record says `branchName`) -- and the screen is standing.
   *
   * 🔴 AND NOTHING IS BEING SAVED: Save Changes is ready, not busy. Until client `oct/bugs` e6499ba, pressing Edit
   * submitted the form it opened, unchanged (React had turned the pressed Edit button into the submit button), and the
   * answer shut the form again -- with whatever had been typed meanwhile. The merged gate of 7 Oct (run 37680224612)
   * saw it as two PUTs of the old manager, neither from Save, and a manager "not kept".
   */
  async expectEditForm(branch) {
    await expect(this.editForm()).toBeVisible()
    await expect(this.editForm().getByRole('button', { name: 'Save Changes', exact: true })).toBeEnabled()
    await expect(this.formField('Bank Name')).toHaveValue(branch.bankName)
    await expect(this.formField('Branch')).toHaveValue(branch.branchName)
    await expect(this.page.getByRole('alert').filter({ hasText: 'This page could not be displayed.' })).toHaveCount(0)
    return this
  }

  /**
   * Correct one field on the edit form and save it. Resolves once the dialog is back on the record's details -- a
   * saved edit -- and throws with what the dialog said if it refused instead, rather than waiting out the clock.
   */
  async saveFieldOnForm(label, value) {
    await this.formField(label).fill(String(value))
    await this.editForm().getByRole('button', { name: 'Save Changes', exact: true }).click()
    const saved = this.page.getByRole('dialog', { name: 'Bank Details' })
    const refused = this.editForm().getByRole('alert')
    await expect(saved.or(refused)).toBeVisible()
    if (await refused.isVisible()) throw new Error(`The branch screen refused the change: ${await refused.textContent()}`)
    return this
  }

  // (`expectRowContains` removed: the Manager column is no longer on a desk -- see `openBranch`.)

  /** Open the create form. Nothing is persisted until Save is pressed. */
  async startAdding() {
    await this.page.getByRole('button', { name: 'Add Bank / Branch', exact: true }).click()
    await expect(this.page.getByRole('heading', { name: 'Add Bank/Branch Details' })).toBeVisible()
    return this
  }

  async save() {
    await this.page.getByRole('button', { name: 'Save', exact: true }).click()
    return this
  }

  async expectFormError(message) {
    await expect(this.page.getByText(message)).toBeVisible()
    return this
  }


}
