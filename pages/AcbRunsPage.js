import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * Debit Order Deductions -> Runs at `/admin/acb/runs` -- preparing a month's debit order run, and what became of it.
 *
 * 🗣️ HEADED "DEBIT ORDER RUNS" SINCE CLIENT `oct/home`: the module once called A.C.B. is Debit Order Deductions (the
 * 5 Oct Show & Tell), its screens keep their names, and "ACB" becomes "Debit Order" (Stephne, DOD) -- so "A.C.B. Runs"
 * became "Debit Order Runs". The address is unchanged.
 *
 * PREPARING IS A JOB. The server answers 202 with the run `PREPARING`, and the screen checks on it by
 * itself until it settles; it then says "The <Month YYYY> run is prepared: <n> debits, <R total>." That
 * sentence is the arrival signal -- written only once the run's own state came back PREPARED -- so the
 * wait below is on it, never on time.
 */
export class AcbRunsPage extends BasePage {
  constructor(page) {
    super(page, '/admin/acb/runs')
  }

  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Debit Order Runs' })).toBeVisible()
  }

  /** Prepares `monthLabel` ("October 2026"), confirming the dialog; resolves once the request was answered. */
  async prepare(monthValue, monthLabel) {
    await this.page.getByLabel('Action month', { exact: true }).selectOption(monthValue)
    await this.page.getByRole('button', { name: `Prepare ${monthLabel}`, exact: true }).click()
    const dialog = this.page.getByRole('dialog', { name: `Prepare the ${monthLabel} run?` })
    await expect(dialog).toBeVisible()
    const answered = this.page.waitForResponse(
      (response) => /\/api\/acb\/runs$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST',
    )
    await dialog.getByRole('button', { name: 'Prepare', exact: true }).click()
    return answered
  }

  /** What the screen says once the run has settled PREPARED. */
  prepared(monthLabel) {
    return this.page.getByText(new RegExp(`^The ${monthLabel} run is prepared: `))
  }

  /** What the screen says when the month already has a live run. */
  alreadyExists(monthLabel) {
    return this.page.getByText(new RegExp(`^The ${monthLabel} run already exists and is `))
  }

  runsTable() {
    return this.page.getByRole('table', { name: 'Debit order runs' })
  }

  /** The list's rows for one month, newest first -- a cancelled run stays on record beside its replacement. */
  runRows(monthLabel) {
    return this.runsTable().getByRole('row').filter({ has: this.page.getByRole('button', { name: `Open the ${monthLabel} run` }) })
  }

  async openRun(monthLabel) {
    await this.runRows(monthLabel).first().getByRole('button', { name: `Open the ${monthLabel} run` }).click()
    await expect(this.page.getByRole('heading', { name: `${monthLabel} run`, exact: true })).toBeVisible()
  }

  /** A fact from the open run's sheet -- "Control total", "Debits". */
  fact(term) {
    return this.page.locator('dl div').filter({ has: this.page.locator('dt', { hasText: new RegExp(`^${term}$`) }) }).locator('dd')
  }

  exclusionsTable() {
    return this.page.getByRole('table', { name: 'Members left out of this run' })
  }

  async cancelOpenRun(monthLabel) {
    await this.page.getByRole('button', { name: 'Cancel this run', exact: true }).click()
    const dialog = this.page.getByRole('dialog', { name: `Cancel the ${monthLabel} run?` })
    await dialog.getByRole('button', { name: 'Cancel run', exact: true }).click()
    await expect(dialog).toBeHidden()
  }
}
