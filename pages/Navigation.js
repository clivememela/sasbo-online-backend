import { expect } from '@playwright/test'
import { CLIENT_URL } from '../support/env.js'

/**
 * The two ways to a screen that every page shares: the rail (`nav` "Main") and the Admin menu's
 * cards -- and the trail in the top bar (`nav` "Breadcrumb") that says where a page is.
 *
 * The rail is a list of areas, each a link with its own disclosure button beside it ("Expand
 * Admin Menu"); an area's screens are links beneath it once it is expanded. The Admin menu is a
 * page of cards, each a link carrying its description -- which is how a card is told apart from
 * the rail's link of the same title.
 */
export class Navigation {
  /** @param {import('@playwright/test').Page} page */
  constructor(page) {
    this.page = page
  }

  rail() {
    return this.page.getByRole('navigation', { name: 'Main' })
  }

  /** The Admin menu's page, with Month end's card on it. */
  async openAdminMenu() {
    await this.page.goto(`${CLIENT_URL}/admin`)
    await expect(this.monthEndCard()).toBeVisible()
    return this
  }

  /** Its line since the 5 Oct Show & Tell moved the month's figures onto it (client `oct/home`). */
  monthEndCard() {
    return this.page.getByRole('link', { name: /^Month end\s*The month’s figures, its statistics and its pack of workbooks/ })
  }

  async chooseMonthEndCard() {
    await this.monthEndCard().click()
  }

  /** Expand an area of the rail from the first screen, as a person arriving would. */
  async openRailSection(title) {
    await this.page.goto(`${CLIENT_URL}/home`)
    const expand = this.rail().getByRole('button', { name: `Expand ${title}` })
    await expect(expand).toBeVisible()
    await expand.click()
    return this
  }

  async chooseRailLeaf(title) {
    await this.rail().getByRole('link', { name: title, exact: true }).click()
  }

  /** The trail ends at the current page, preceded by the area it is in. */
  async expectTrail(area, current) {
    const trail = this.page.getByRole('navigation', { name: 'Breadcrumb' })
    await expect(trail.getByRole('link', { name: area, exact: true })).toBeVisible()
    await expect(trail.getByText(current, { exact: true })).toHaveAttribute('aria-current', 'page')
  }
}
