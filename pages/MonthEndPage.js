import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * The Month end page (`/admin/monthly-menu`, month end `04.1`), as the built client draws it:
 *
 *   the month       a native select labelled "Month", its values the server's months (`2026-08`), the
 *                   open one and each one taken -- with a sentence beneath saying which kind it is;
 *   Take the month  for the open month, and *Take it again* for a taken one: both disabled, with the
 *                   reason, because taking a month refuses every caller until sign-in;
 *   the statistics  one link each, to a screen of its own, its name and beneath it the old system's;
 *   the listings    four rows that wait for sign-in, and open nothing.
 *
 * The browser's clock decides nothing here: every month this page object names comes from the
 * server's own months, read through the API.
 */
export class MonthEndPage extends BasePage {
  constructor(page) {
    super(page, '/admin/monthly-menu')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Month end' })).toBeVisible()
  }

  /**
   * The statistics: every link in the sheet under the "Statistics" heading, wherever it leads.
   *
   * 🔑 Located by the heading and NOT by where the links point. Selecting the links that lead under
   * this page's address would make "no statistic opens the page it is on" true by construction --
   * a row that linked back here would never have been selected to be found.
   */
  statistics() {
    return this.page
      .getByRole('heading', { level: 2, name: 'Statistics', exact: true })
      .locator('xpath=following-sibling::*[1]')
      .getByRole('link')
  }

  /** Where each statistic leads, without its query -- read once the first has painted. */
  async statisticDestinations() {
    await expect(this.statistics().first()).toBeVisible()
    const hrefs = await this.statistics().evaluateAll((links) => links.map((link) => link.getAttribute('href')))
    return hrefs.map((href) => href.split('?')[0])
  }

  /** Each statistic leads to a screen under this page's address, and no two to the same one. */
  async expectEveryStatisticHasAPageOfItsOwn() {
    const destinations = await this.statisticDestinations()
    expect(destinations.filter((path) => !path.startsWith(`${this.path}/`)), 'statistics that lead elsewhere').toEqual([])
    expect(new Set(destinations).size, 'statistics sharing a page').toBe(destinations.length)
  }

  /** The defect this guards: every row of the old page was a link to the page it was on. */
  async expectNoStatisticOpensThisPage() {
    const destinations = await this.statisticDestinations()
    expect(destinations.filter((path) => path.replace(/\/+$/, '') === this.path), 'statistics that lead here').toEqual([])
  }

  /** The statistic named `name`, whose second line is the old system's name. */
  statistic(name) {
    return this.page.getByRole('link', { name: new RegExp(`^${escaped(name)}`) })
  }

  /** Each row of a table of `statistic` and `the old system's name`. */
  async expectStatisticsWithOldNames(rows) {
    for (const row of rows) {
      await this.expectStatisticWithOldName(row.statistic, row["the old system's name"])
    }
  }

  async expectStatisticWithOldName(name, oldName) {
    const link = this.statistic(name)
    await expect(link).toBeVisible()
    await expect(link.getByText(name, { exact: true })).toBeVisible()
    await expect(link.getByText(oldName, { exact: true })).toBeVisible()
  }

  /** The listings of members: the rows under their heading, each saying it waits for sign-in. */
  listingsHeading() {
    return this.page.getByRole('heading', { level: 2, name: 'Listings of members' })
  }

  async expectListingsWaitForSignIn() {
    await expect(this.listingsHeading()).toBeVisible()
    await expect(this.page.getByText('This waits until administrators sign in to the system. Nothing was produced.')).toBeVisible()
    await expect(this.page.getByText('Waits for sign-in', { exact: true })).toHaveCount(LISTINGS.length)
  }

  async expectNoListingCanBeOpened() {
    for (const listing of LISTINGS) {
      await expect(this.page.getByText(listing, { exact: true })).toBeVisible()
      await expect(this.page.getByRole('link', { name: new RegExp(escaped(listing)) })).toHaveCount(0)
    }
  }

  monthSelect() {
    return this.page.getByLabel('Month', { exact: true })
  }

  /** Open the page and choose a month the server named. */
  async openOn(month) {
    await this.open()
    await this.chooseMonth(month)
    return this
  }

  /** Choose a month the server named, by its value -- `2026-08`. */
  async chooseMonth(month) {
    await expect(this.monthSelect().locator(`option[value="${month}"]`)).toHaveCount(1)
    await this.monthSelect().selectOption(month)
  }

  /** The sentence beneath the month for a taken one: "August 2026: Taken on … by …. Its figures are …". */
  async expectTakenSaid(month) {
    const said = new RegExp(`^${monthName(month)}: Taken (again )?on .+\\. Its figures are the month as it stood then\\.$`)
    await expect(this.page.getByText(said)).toBeVisible()
  }

  /** "Take September 2026" -- shown for the open month, and disabled. */
  takeButton(month) {
    return this.page.getByRole('button', { name: `Take ${monthName(month)}`, exact: true })
  }

  async expectTakeOfferedAndRefused(month) {
    await expect(this.takeButton(month)).toBeVisible()
    await expect(this.takeButton(month)).toBeDisabled()
  }

  async expectTakeWaitsForSignIn() {
    await expect(
      this.page.getByText('Taking a month freezes its figures for good, so it waits until administrators sign in to this system.'),
    ).toBeVisible()
  }
}

/** The four listings the page names -- each refuses until sign-in, so each is a row and not a link. */
export const LISTINGS = [
  "The month's new members",
  "The month's deleted members",
  'The members a recruiter signed up',
  'The elected representatives',
]

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December']

/** `2026-08` as the screen writes it: "August 2026". */
export function monthName(month) {
  const [year, number] = month.split('-')
  return `${MONTHS[Number(number) - 1]} ${year}`
}

function escaped(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
