import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * Debit Order Deductions -> Debits at `/admin/acb/debits` (the module once called A.C.B.; the screen keeps its name,
 * "Manage Debits") -- the Re-Debits screen: the latest
 * returned run's unpaids and their decisions, and a member search for anyone
 * outside that list.
 *
 * THE SEARCH IS THE SERVER'S (ACB `04.1`, R-SEC-B). The screen used to fetch
 * every member on mount and filter in the browser, which is why this object
 * once waited for `/api/members` before anything else: pressing Search before
 * that list arrived reported "0 members found" for a member who was right
 * there. The screen now asks `POST /api/acb/members/search` for one page when
 * Search is pressed, and states a count only once that answer has arrived --
 * so arrival is the heading, and a search waits for its OWN answer.
 *
 * The listener is registered BEFORE the click, because a listener attached
 * afterwards can miss a fast response. No fixed wait anywhere: the count's
 * visibility is the assertion that retries.
 */
export class AcbDebitsPage extends BasePage {
  constructor(page) {
    super(page, '/admin/acb/debits')
  }

  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Manage Debits' })).toBeVisible()
  }

  async search(surname) {
    await this.page.getByLabel('Surname', { exact: true }).fill(surname)
    const answered = this.page.waitForResponse(
      (response) => response.url().includes('/api/acb/members/search') && response.request().method() === 'POST',
    )
    await this.page.getByRole('button', { name: 'Search', exact: true }).click()
    const response = await answered
    expect(response.ok(), `the member search answered ${response.status()}`).toBe(true)
    await expect(this.resultCount()).toBeVisible()
    return this
  }

  resultCount() {
    return this.page.getByText(/\d+ members? found/)
  }

  /** How many members the screen says the search found -- the server's total, not one page's. */
  async reportedResultCount() {
    return Number(/(\d+) members? found/.exec(await this.resultCount().textContent())?.[1] ?? NaN)
  }

  emptyState() {
    return this.page.getByText('No members match your search.')
  }

  /** The unpaid debits of the latest run the bank has returned, by the table's own caption. */
  unpaidTable() {
    return this.page.getByRole('table', { name: 'Unpaid debits and their decisions' })
  }

  /** One unpaid's row, found by the member's membership number. */
  unpaidRow(membershipNumber) {
    return this.unpaidTable().getByRole('row').filter({ hasText: membershipNumber })
  }

  /** What the screen says once the server has recorded a decision -- a status, not an alert. */
  recordedNotice() {
    return this.page.getByRole('status').filter({ hasText: /^Recorded:/ })
  }
}
