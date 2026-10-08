import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * The subscriptions reference table at `/admin/maintenance/subscriptions` --
 * one row per bank, edited in place by clicking it.
 *
 * 🔑 THIS IS THE ONE SCREEN IN THE APPLICATION THAT RENDERS ALL THREE STATES
 * PROPERLY, and it is worth saying so rather than leaving it as a coincidence
 * of which screen a scenario happened to pick:
 *
 *   loading   "Retrieving subscriptions…"             (one ellipsis character)
 *   failed    "We could not load the subscriptions."  with a retry
 *   empty     "No subscriptions match your search."
 *
 * Before the design client the member register had none of the three (A27)
 * and the bank/branch screen had no failure branch at all, so this is where
 * the empty state and the data state could first be told apart honestly.
 *
 * ⚠️ BEFORE THE DESIGN CLIENT THERE WAS NO RETRY: the failure was a bare
 * "Error: ..." banner with no way out of it, the half of the standards'
 * "failed-with-retry" the application met nowhere. The design client adds a
 * retry here; nothing in this suite asserts it yet.
 *
 * ⚑ AN OBSERVATION THIS SUITE DOES NOT ASSERT, recorded here so that it is not
 * lost with the code that carried it. The in-place edit controls on this table
 * -- the debit-day inputs -- carry NO label of any kind: no `<label>`, no
 * `aria-label`, no placeholder. They can only be addressed positionally.
 *
 * Same class as A28 and A35 in the register, different instance, and it has no
 * row of its own. The UI edit path that documented this was removed by the
 * closing refactor because nothing called it -- the debit-day scenario changes
 * the value through the API instead -- and a page-object method no scenario
 * uses is not coverage. Filing it as a register row, and writing the scenario
 * that would assert it, is remediation work rather than a refactor.
 */
export class SubscriptionsPage extends BasePage {
  constructor(page) {
    super(page, '/admin/maintenance/subscriptions')
  }

  /**
   * Overridden: arrival means the rows are on screen.
   *
   * Waiting for the loading text to CLEAR is what makes this condition-based
   * rather than a race -- the table's `<thead>` renders immediately, so a page
   * object that waits for the table waits for nothing.
   */
  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Subscriptions', exact: true })).toBeVisible()
    // One ellipsis CHARACTER. Written as three dots this matched nothing, so the wait passed
    // before the subscriptions had loaded.
    await expect(this.page.getByText('Retrieving subscriptions…')).toHaveCount(0)
    return this
  }

  /** One row, addressed by the bank it is for. */
  row(bankName) {
    return this.rows().filter({ hasText: bankName })
  }

  /** The cell a row shows in one column, addressed by the column heading. */
  async cellText(bankName, columnHeading) {
    const headings = await this.page.locator('thead th').allTextContents()
    const index = headings.findIndex((heading) => heading.trim() === columnHeading)
    expect(index, `no column headed ${JSON.stringify(columnHeading)}`).toBeGreaterThanOrEqual(0)
    return (await this.row(bankName).locator('td').nth(index).textContent())?.trim()
  }

  /** Every bank name the table shows, in order, blanks included. */
  async bankNamesShown() {
    return (await this.rows().locator('td').first().allTextContents()).map((text) => text.trim())
  }

  emptyState() {
    return this.page.getByText('No subscriptions match your search.')
  }

  /**
   * What the register says to a caller nobody signed in, in place of a way to change an amount: the permission
   * notice (client `oct/bugs`, only staff change amounts) -- "Sign in to change subscription amounts." and a link.
   */
  changeNeedsSignIn() {
    return this.page.getByRole('status').filter({ hasText: /^Sign in to change subscription amounts\./ })
  }

  /** The rows' own way to change one -- each named for the bank it edits, and offered only to staff. */
  editControls() {
    return this.page.getByRole('button', { name: /^Edit the subscription for / })
  }

  /** Open one row for editing, by the bank it is for. */
  async editRow(bankName) {
    await this.page.getByRole('button', { name: `Edit the subscription for ${bankName}`, exact: true }).click()
  }

  /** The edited row's debit order day -- "Debit order day for <bank>" since the rename, "ACB debit day for" before it. */
  dayField(bankName) {
    return this.page.getByLabel(new RegExp(`^(ACB debit|Debit order) day for ${escaped(bankName)}$`))
  }

  async saveRow(bankName) {
    await this.page.getByRole('button', { name: `Save changes to ${bankName}`, exact: true }).click()
  }

  async cancelEdit() {
    await this.page.getByRole('button', { name: 'Discard these changes', exact: true }).click()
  }

  /** What is said under the day it refuses (client `oct/bugs`, the user manual's reading of Maintenance, 6 Oct). */
  dayRefusal() {
    return this.page.getByRole('alert').filter({ hasText: /^Debit (order )?day must be between 1 and 31\.$/ })
  }

  /** The sentence above the table saying what a row's ACTIVE decides (the same reading). */
  activeMeaning() {
    return this.page.getByText(/^Debit orders are collected at a bank only while its row is ACTIVE\./)
  }

  async filterByBank(bankName) {
    // A combobox labelled "Bank" in the design client. `exact`, because "Core banking"
    // beside it also contains "bank".
    await this.page.getByRole('combobox', { name: 'Bank', exact: true }).click()
    await this.page.getByRole('option', { name: bankName, exact: true }).click()
    return this
  }
}

function escaped(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
