import { expect } from '@playwright/test'
import { MonthEndPage, monthName } from './MonthEndPage.js'

/**
 * The month's figures, on the Month end page (`/admin/monthly-menu`, month end `02.3`).
 *
 * 🗣️ THEY WERE THE FIRST SCREEN'S UNTIL THE 5 OCT SHOW & TELL, where SASBO looked for them on Month end: client
 * `oct/home` moved `HomeFigures` there as `MonthEndFigures`, and the first screen kept only the modules. This page
 * object is the first screen's figures object, moved with them -- the same strip, sheet and lines, read where they
 * are now.
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT
 *   - a section named "The month’s figures" (its heading; a typographic apostrophe), the page's first, under the month;
 *   - the strip inside it, a description list named "The month’s totals": six terms, each with its value and, once
 *     read, a line saying what it counted;
 *   - the criteria "Area" and "Grouped by", and the sheet "The figures by employer group" with its total;
 *   - 🔑 NO MONTH OF ITS OWN. The figures follow the page's one month, chosen at the top ("Month") or carried in the
 *     address (`?month=2026-09`), as a statistic's screen carries it back. Unasked, the page opens on the newest month
 *     TAKEN -- frozen figures -- so a scenario that counts a change names the open month, which the server says.
 *
 * 🔑 A FIGURE NOBODY READ IS NOT A NOUGHT. While the figures load, and when they could not be read at all, every value
 * in the strip is "—", with "not available" for a screen reader -- never 0. That is what the serial feature asserts
 * with the API stopped, and why this page object reads a figure's text rather than parsing it as a number it may not
 * be.
 */
export class MonthEndFiguresPage extends MonthEndPage {
  /** The figures' section: the strip, the criteria and the sheet. */
  figures() {
    return this.page.getByRole('region', { name: 'The month’s figures' })
  }

  /**
   * The strip of six figures. Named "The month’s totals" since client `oct/home` 7db86d9 (the review: one name for the
   * section, its heading and its list was read three times over) -- "The month's figures" before it.
   */
  strip() {
    return this.figures().getByLabel(/^The month[’']s (totals|figures)$/)
  }

  /** Every value in the strip -- the first line under each term. */
  values() {
    return this.strip().locator('dt + dd')
  }

  /**
   * Open the page on a month the server named -- `2026-09` -- by the address, as a statistic's screen comes back to it,
   * and wait until the figures are that month's. The address reaches a month the control does not offer: a past month
   * that was not taken.
   */
  async openAt(month) {
    await this.page.goto(`${this.baseURL}${this.path}?month=${encodeURIComponent(month)}`)
    await this.expectLoaded()
    await this.expectJoinedFigureOf(month)
    return this
  }

  /**
   * Choose a month the page offers -- the open month, or one taken -- by its control, and wait until the figures are
   * that month's: the joined figure says whose Date Joined it counted, and says it only once the answer has come.
   */
  async chooseMonth(month) {
    await super.chooseMonth(month)
    await this.expectJoinedFigureOf(month)
  }

  async expectJoinedFigureOf(month) {
    await expect(this.definition('Joined this month')).toHaveText(`Date Joined in ${monthName(month)}`)
  }

  /** What the figures' section says when they could not be read: the failure, as an alert. */
  failure() {
    return this.figures().getByRole('alert')
  }

  /** The figures could not be read: every figure in the strip says so, and the sheet says why. */
  async expectFiguresNotAvailable({ timeout } = {}) {
    await expect(this.failure()).toBeVisible({ timeout })
    await expect(this.strip().getByText('not available', { exact: true })).toHaveCount(6)
  }

  /** No figure in the strip reads nought, and the sheet does not say that nobody is on record. */
  async expectNoFigureReadsNought() {
    const values = (await this.values().allInnerTexts()).map((text) => text.trim())
    expect(values, 'the strip has its six figures').toHaveLength(6)
    expect(values.filter((value) => /^0$/.test(value)), 'figures that read nought').toEqual([])
    await expect(this.figures().getByText('No members are on record yet.')).toHaveCount(0)
  }

  /** Open the page and wait until its figures have said they could not be read. */
  async openWithFiguresNotAvailable() {
    await this.open()
    await this.expectFiguresNotAvailable({ timeout: 90_000 })
    return this
  }

  /** The figures' own way out -- scoped to their section: with the API down the month control offers one too. */
  retry() {
    return this.figures().getByRole('button', { name: 'Try again' })
  }

  async expectRetryOffered() {
    await expect(this.retry()).toBeVisible()
  }

  async tryAgain() {
    await this.retry().click()
  }

  /** The figures are the union's: the failure is gone and members on record has a number. */
  async expectFiguresShown() {
    await expect(this.failure()).toHaveCount(0)
    await expect(this.figure('Members on record')).toHaveText(/\d/)
  }

  /** One figure's value, by the term the strip shows above it -- "Members on record", "Suspended". */
  figure(term) {
    return this.figureCell(term).locator('dt + dd')
  }

  /** What a figure says it counted: the last line under its term, shown once the figure was read. */
  definition(term) {
    return this.figureCell(term).locator('dd').last()
  }

  figureCell(term) {
    return this.strip().locator('div').filter({ has: this.page.getByText(term, { exact: true }) })
  }

  /** A figure as a number: its digits, the spaces that group them taken out. */
  async readNumber(term) {
    await expect(this.figure(term)).toHaveText(/\d/)
    return Number((await this.figure(term).innerText()).replace(/\D/g, ''))
  }

  /** Open the page on a month the server named and read the figures named -- `{ 'Members on record': 2418, … }`. */
  async openAndRead(month, terms) {
    await this.openAt(month)
    return this.readNumbers(terms)
  }

  /** The joined figure of each month given, `{ '2026-09': 41, … }`, each month opened in turn. */
  async readJoinedFor(months) {
    const read = {}
    for (const month of months) {
      await this.openAt(month)
      read[month] = await this.readNumber('Joined this month')
    }
    return read
  }

  /** Several figures at once, by their terms. */
  async readNumbers(terms) {
    const read = {}
    for (const term of terms) read[term] = await this.readNumber(term)
    return read
  }

  groupingSelect() {
    return this.figures().getByLabel('Grouped by', { exact: true })
  }

  /** Choose a grouping by its name -- "Area" -- and wait until the sheet is grouped by it. */
  async chooseGrouping(label) {
    await this.groupingSelect().selectOption({ label })
    await expect(this.sheet(label)).toBeVisible()
  }

  /** The sheet, named by its grouping: "The figures by area". */
  sheet(label) {
    return this.figures().getByRole('table', { name: `The figures by ${label.toLowerCase()}` })
  }

  async groupings() {
    return (await this.groupingSelect().locator('option').allInnerTexts()).map((text) => text.trim())
  }

  /**
   * One column of the sheet's total -- the row the sheet ends on -- as a number. The columns are the table's own keys:
   * `paying` (Members), `suspended`, `joined`, `left`, `staff`.
   */
  async total(column) {
    const cell = this.figures().locator(`tfoot td[data-col="${column}"]`)
    await expect(cell).toHaveText(/\d/)
    return Number((await cell.innerText()).replace(/\D/g, ''))
  }

  /** Each figure says what it counted -- rows of `figure` and `what it counted`. */
  async expectDefinitions(rows) {
    for (const row of rows) {
      await expect(this.definition(row.figure)).toHaveText(row['what it counted'])
    }
  }

  /**
   * The sheet, by the grouping named, adds up to the figures above it: its total's members are the strip's paying
   * members, and its suspended the strip's suspended -- one answer, so they cannot differ unless a row is lost.
   */
  async expectSheetAddsUp(label = 'Employer group') {
    if (label !== 'Employer group') await this.chooseGrouping(label)
    await expect(this.sheet(label)).toBeVisible()
    expect(await this.total('paying'), `the sheet by ${label.toLowerCase()}'s members`).toBe(await this.readNumber('Paying members'))
    expect(await this.total('suspended'), `the sheet by ${label.toLowerCase()}'s suspended`).toBe(await this.readNumber('Suspended'))
  }

  /** In each of the six groupings, the members whose workplace is not on record are one row. */
  async expectNoWorkplaceRowInEveryGrouping() {
    for (const label of await this.groupings()) {
      await this.chooseGrouping(label)
      await expect(this.noWorkplaceRow(), `the row of no workplace, by ${label.toLowerCase()}`).toHaveCount(1)
    }
  }

  /** The figures are the month named: the joined and the left figures each say whose month they counted. */
  async expectMonthShown(name) {
    await expect(this.definition('Joined this month')).toHaveText(`Date Joined in ${name}`)
    await expect(this.definition('Left this month')).toHaveText(`left in ${name}, with a deletion code`)
  }

  /** The row of the members whose workplace is not on record, as the server names it. */
  noWorkplaceRow() {
    return this.figures().getByRole('row', { name: /^No workplace on record/ })
  }

  /** A line beneath the sheet, whole or by pattern. */
  note(text) {
    return this.figures().getByText(text, typeof text === 'string' ? { exact: true } : undefined)
  }
}
