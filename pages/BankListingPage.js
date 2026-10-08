import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * The read-only bank listing at `/banks/listing` -- bank name, abbreviation and
 * universal branch code, with three filters above it.
 *
 * WHY THIS SCREEN IS THE ONE THE ERROR SCENARIO USES
 * It is the only one of the two bank screens that distinguishes "no results"
 * from "the request failed". It renders three genuinely different states:
 *
 *   loading   "Loading banks…"             (one ellipsis character)
 *   failed    "Unable to load bank listing at the moment."
 *   empty     "No banks match these criteria"
 *
 * Before the design client its sibling at `/banks` had no error branch at all
 * -- a failed fetch left the list empty and it rendered "No banks match your
 * search.", telling an administrator there is no data when the backend is
 * down. That is why the backend-down scenario points here: this screen could
 * already tell the truth, so a test against it fails for the right reason.
 * The design client gives `/banks` an error state too.
 */
export class BankListingPage extends BasePage {
  constructor(page) {
    super(page, '/banks/listing')
  }

  /**
   * Overridden: assert on content, not on having arrived at a URL.
   *
   * 🔴 THE SECOND ASSERTION IS THE LOAD-BEARING ONE, and leaving it out cost a
   * failing test on the first real run. The old screen's "Showing 0-0 of 0
   * banks" summary rendered IMMEDIATELY -- `isLoading` guarded only the table
   * body, not the summary above it -- so a page object that read the count as
   * soon as the heading appeared read ZERO from a screen about to show 68. The
   * design client's pager says "Loading banks…" instead, and this wait holds on
   * either.
   *
   * The failure was a race, not a wrong number, and it presented as one: the
   * assertion said 0 while the captured page snapshot showed 68, because the
   * data arrived between the read and the screenshot.
   *
   * Waiting on the loading indicator to CLEAR is the condition-based fix.
   * A fixed wait here would also have "worked", and would have hidden exactly
   * this — which is why fixed waits are banned here, and grepped for.
   */
  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Bank Listing' })).toBeVisible()
    await expect(this.loadingState()).toHaveCount(0)
  }

  filter(label) {
    return this.page.getByLabel(label, { exact: true })
  }

  /**
   * The three states this screen genuinely distinguishes, as three locators.
   *
   * They are named separately and asserted separately because the whole point
   * of the backend-down scenario is that they are NOT interchangeable: an error
   * rendered as an empty table is the confident wrong answer, and only a test
   * that checks for the error AND the absence of the empty text can tell the
   * difference.
   */
  errorState() {
    return this.page.getByText('Unable to load bank listing at the moment.')
  }

  emptyState() {
    // The design client's empty copy. The old string had stopped existing, so every check
    // that the listing does NOT say this passed without looking at anything.
    return this.page.getByText('No banks match these criteria')
  }

  loadingState() {
    // One ellipsis CHARACTER, not three dots -- written as three dots, this matched nothing,
    // so the wait for loading to finish passed before loading had begun.
    return this.page.getByText('Loading banks…')
  }

  /**
   * The way out of the failed state.
   *
   * 🔴 THERE IS NO SUCH CONTROL ANYWHERE IN THE APPLICATION. This locator is
   * written to the standard the plans bind us to -- "loading, empty, and
   * failed-with-retry" -- so that the scenario asserting it reads as a
   * requirement rather than as a guess, and turns green the day one is added.
   * It is deliberately generous about the wording, because the assertion is
   * about the affordance existing, not about what it is called.
   */
  retry() {
    return this.page.getByRole('button', { name: /retry|try again|reload/i })
  }


  /** A row addressed by the bank name it shows. */
  row(bankName) {
    return this.page.getByText(bankName, { exact: true })
  }

  async filterBy(label, value) {
    await this.filter(label).fill(value)
    return this
  }


}
