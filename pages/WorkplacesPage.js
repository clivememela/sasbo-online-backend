import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * SASBO's workplaces (`/admin/maintenance/workplaces`, month end `01.5`), a page at a time.
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT: a search labelled "Workplace or R/A number" (two characters or more);
 * the number on record ("5 507 workplaces on record"); the sheet "SASBO’s workplaces" -- Workplace, R/A number,
 * Employer, Area, Committee, Staff; the pager ("Showing 1–50 of 5 507 · Page 1 of 111"); and beneath it, the lists
 * the workplaces were read from, each "SASBO’s …: …".
 */
export class WorkplacesPage extends BasePage {
  constructor(page) {
    super(page, '/admin/maintenance/workplaces')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Workplaces' })).toBeVisible()
  }

  sheet() {
    return this.page.getByRole('table', { name: 'SASBO’s workplaces' })
  }

  bodyRows() {
    return this.sheet().locator('tbody tr')
  }

  /**
   * The row of the workplace with this R/A number -- a number no other workplace has. The number is the row's control,
   * a button named by what it opens with the number first ("1234 — the visits to …"), so the cell's own name is that
   * sentence and never the bare number: the row is found by its control.
   */
  row(raNumber) {
    const number = String(raNumber).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return this.bodyRows().filter({ has: this.page.getByRole('button', { name: new RegExp(`^${number} — `) }) })
  }

  async openAndSearch(text) {
    await this.open()
    await this.page.getByLabel('Workplace or R/A number').fill(String(text))
    return this
  }

  /** The workplace is a row, with its R/A number, its employer, its area and its committee as the server has them. */
  async expectRowWithLineage(workplace) {
    const row = this.row(workplace.raNumber)
    await expect(row).toHaveCount(1)
    for (const part of [workplace.name, workplace.employer, workplace.area, workplace.committeeName ?? workplace.committee]) {
      await expect(row).toContainText(String(part).trim())
    }
  }

  async expectOnlyRow(raNumber) {
    await expect(this.row(raNumber)).toHaveCount(1)
    await expect(this.bodyRows()).toHaveCount(1)
  }

  /** One page, fewer than all; the number on record said, and the pager counting the same number. */
  async expectPaged() {
    const count = this.page.getByText(/^[\d\s]+ workplaces? on record$/)
    await expect(count).toBeVisible()
    const onRecord = Number((await count.innerText()).replace(/\D/g, ''))
    const pager = this.page.getByText(/^Showing [\d\s]+–[\d\s]+ of [\d\s]+ · Page 1 of \d+$/)
    await expect(pager).toBeVisible()
    const [, shown, total] = (await pager.innerText()).replace(/[\s ]/g, '').match(/Showing\d+–(\d+)of(\d+)/)
    expect(Number(total), 'the pager counts every workplace on record').toBe(onRecord)
    expect(Number(shown), 'one page, never all of them').toBeLessThan(onRecord)
    await expect(this.bodyRows()).toHaveCount(Number(shown))
  }

  /** Beneath the sheet, the lists the workplaces were read from: at least one line, "SASBO’s …: …". */
  async expectProvenance() {
    await expect(this.page.getByText(/^SASBO’s .+: .+/).first()).toBeVisible()
  }
}
