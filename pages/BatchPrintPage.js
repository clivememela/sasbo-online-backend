import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * The organisers' batch print (`/reports/locations/print`, client `oct/reports-visits`; the 5 Oct Show & Tell: "let me
 * put all 20 numbers in at once, click print ... separate them by bank ... if it's a new bank, start it on a new page").
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT: the field "R/A numbers" (spaces, commas or new lines; up to fifty) and
 * "Show listings"; then a line "3 listings in 2 banks for 3 R/A numbers.", any number that is no workplace named
 * ("Not on record here: …"), and one section per bank -- named by the bank, in the order its first number was typed --
 * each headed by the bank, the day printed, the organiser's area and its count of branches, holding one listing per
 * branch (an article named "<branch> · R/A <number>"). "Print listings" is offered once every branch has its members,
 * or the sentence saying why not. On paper each bank after the first begins a new sheet (`break-before: page`).
 */
export class BatchPrintPage extends BasePage {
  constructor(page) {
    super(page, '/reports/locations/print')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Batch print' })).toBeVisible()
  }

  /** Type the numbers in one go, as a list pasted from a note is, and ask for their listings. */
  async printTogether(numbers) {
    await this.page.getByLabel(labelPattern('R/A numbers')).fill(numbers.join(', '))
    await this.page.getByRole('button', { name: 'Show listings', exact: true }).click()
    await expect(this.page.getByText(/^\d+ listings? in \d+ banks? for \d+ R\/A numbers?\.$/)).toBeVisible()
    return this
  }

  /** The banks' sections, in the order the page draws them: the regions that hold branch listings. */
  bankSections() {
    return this.page.getByRole('main').getByRole('region').filter({ has: this.page.getByRole('article') })
  }

  /** One branch's listing, by its R/A number, wherever it is. */
  listing(raNumber) {
    return this.page.getByRole('article', { name: new RegExp(`· R/A ${raNumber}$`) })
  }

  async expectListing(raNumber) {
    await expect(this.listing(raNumber)).toBeVisible()
  }

  /** The sections are these banks, in this order, each holding exactly the listings of its numbers, in typed order. */
  async expectGroupedByBank(banks) {
    await expect(this.bankSections()).toHaveCount(banks.length)
    for (const [index, { name, raNumbers }] of banks.entries()) {
      const section = this.bankSections().nth(index)
      await expect(section).toHaveAccessibleName(name)
      const articles = section.getByRole('article')
      await expect(articles).toHaveCount(raNumbers.length)
      for (const [at, raNumber] of raNumbers.entries()) {
        await expect(articles.nth(at)).toHaveAccessibleName(new RegExp(`· R/A ${raNumber}$`))
      }
    }
  }

  /** A bank's heading line: "Printed … · Organiser's area: L · 2 branches". */
  async expectHeading(index, { area, branches }) {
    const section = this.bankSections().nth(index)
    await expect(section).toContainText(`Organiser's area: ${area}`)
    await expect(section).toContainText(`${branches} branch${branches === 1 ? '' : 'es'}`)
  }

  /** Every branch is ready, so the page offers to print. */
  async expectReadyToPrint() {
    await expect(this.page.getByRole('button', { name: 'Print listings', exact: true })).toBeEnabled({ timeout: 30_000 })
  }

  /**
   * On paper, each bank after the first begins a new sheet: read from the page as the printer would lay it out --
   * printing emulated -- and put back for the screen after.
   */
  async expectEachBankAfterTheFirstOnANewPage() {
    await this.page.emulateMedia({ media: 'print' })
    try {
      const breaks = await this.bankSections().evaluateAll((sections) => sections.map((s) => getComputedStyle(s).breakBefore))
      expect(breaks.length, 'banks printed').toBeGreaterThan(1)
      expect(breaks[0], 'the first bank begins where the print begins').not.toBe('page')
      expect(breaks.slice(1), 'each later bank begins a new sheet').toEqual(breaks.slice(1).map(() => 'page'))
    } finally {
      await this.page.emulateMedia({ media: 'screen' })
    }
  }

  /** The line that names the numbers that are no workplace here. */
  async expectNotOnRecord(raNumber) {
    await expect(this.page.getByRole('status').filter({ hasText: /^Not on record here: / })).toContainText(String(raNumber))
  }
}
