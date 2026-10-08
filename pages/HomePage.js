import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * The first screen (`/home`): every module of the system, and nothing else.
 *
 * 🗣️ SINCE THE 5 OCT SHOW & TELL (client `oct/home`) the month's figures are Month end's (`MonthEndFiguresPage`), and
 * this screen shows the modules three ways -- as cards, as a list or as a table -- by a switch in its head: a group
 * named "Show the modules as", three buttons "Cards", "List" and "Table", the one chosen `aria-pressed`. The choice is
 * kept in the browser (`localStorage`), and a browser that has chosen nothing sees the cards.
 *
 * WHAT EACH VIEW IS, READ FROM THE BUILT CLIENT
 *   cards, list   one link per module, named by the module and the line under it ("Members The membership register
 *                 across every bank"); the two views draw the same links, laid out differently;
 *   table         a table captioned "The modules of SASBO Online" -- Module (the link, named by the module alone),
 *                 What it is for, In the menu.
 * So which view is on screen is read from the switch, the one thing that says so, and each view's own shape is
 * asserted beside it.
 *
 * 🔑 IT ASKS THE SERVER FOR NOTHING. The modules are the rail's, read across, so with the API stopped this screen is
 * the same screen -- which is what the serial feature asserts.
 */
export class HomePage extends BasePage {
  constructor(page) {
    super(page, '/home')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Welcome to SASBO Online' })).toBeVisible()
  }

  /** The switch, and one of its three choices by the word it shows. */
  viewSwitch() {
    return this.page.getByRole('group', { name: 'Show the modules as' })
  }

  viewButton(word) {
    return this.viewSwitch().getByRole('button', { name: word, exact: true })
  }

  async chooseView(word) {
    await this.viewButton(word).click()
    await this.expectView(word)
  }

  /** The view on screen: its button pressed, the other two not -- and the view's own shape drawn. */
  async expectView(word) {
    for (const choice of VIEWS) {
      await expect(this.viewButton(choice)).toHaveAttribute('aria-pressed', String(choice === word))
    }
    if (word === 'Table') {
      await expect(this.moduleTable()).toBeVisible()
    } else {
      await expect(this.moduleTable()).toHaveCount(0)
      await expect(this.moduleLink('Members')).toBeVisible()
    }
  }

  moduleTable() {
    return this.page.getByRole('table', { name: 'The modules of SASBO Online' })
  }

  /**
   * A module's way in, in whichever view is drawn. In the cards and the list it is a link named by the module and
   * its line; in the table, by the module alone -- so the name is matched from its start. Scoped to the page's main
   * content: the rail lists the same areas under the same titles.
   */
  moduleLink(name) {
    return this.page.getByRole('main').getByRole('link', { name: moduleLinkName(name) })
  }

  /**
   * In the table, a module's row says what the module is for and where it sits in the menu.
   *
   * 🔴 A `has:` LOCATOR IS SEARCHED INSIDE EACH ROW, NOT FROM THE PAGE, so it names the link alone. `moduleLink`'s
   * `main` is not inside any row, and with it the filter matched no row at all (the merged gate's red of 7 Oct, run
   * 37680224612). The table is inside `main`, which is what keeps the rail's links out here.
   */
  async expectTableRow(name, { for: purpose, inTheMenu }) {
    const row = this.moduleTable().getByRole('row').filter({ has: this.page.getByRole('link', { name: moduleLinkName(name) }) })
    await expect(row).toHaveCount(1)
    await expect(row).toContainText(purpose)
    await expect(row).toContainText(inTheMenu)
  }

  /** The rail's appearance control, which says which appearance is on. */
  appearance() {
    return this.page.getByRole('navigation', { name: 'Main' }).getByRole('button', { name: /^Appearance · / })
  }
}

/** The switch's three words, in the order it offers them. */
const VIEWS = ['Cards', 'List', 'Table']

/** A module's link by its name, matched from its start: the cards and the list name it with its line after. */
function moduleLinkName(name) {
  return new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(\\s|$)`)
}
