import { expect } from '@playwright/test'
import { Then, When } from '../support/fixtures.js'
import { HomePage } from '../pages/HomePage.js'

/**
 * The first screen's journeys (`/home`): every module of the system, as cards, a list or a table (client `oct/home`,
 * the 5 Oct Show & Tell). One-liners that delegate, as everywhere in this directory.
 *
 * Every scenario starts in a browser of its own -- Playwright gives each one a fresh context -- so "a browser that has
 * chosen nothing" is simply the scenario's first visit, and "the choice is remembered" is the same browser coming back.
 */

When('an administrator opens the first screen', async ({ page, world }) => {
  world.home = await new HomePage(page).open()
})

When('they open the first screen again', async ({ page, world }) => {
  world.home = await new HomePage(page).open()
})

/**
 * 🗣️ LIGHT, EVEN WHERE THE COMPUTER PREFERS DARK (the 5 Oct Show & Tell; client `1829932`). The browser is made to
 * prefer dark BEFORE the page loads, so a screen that read the preference would open dark -- and this one must not.
 */
When('an administrator whose computer prefers dark opens the first screen', async ({ page, world }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  world.home = await new HomePage(page).open()
})

/*
 * "as cards", "as a list", "as a table": the switch's three words, written as a sentence says them. Regular
 * expressions, because a Cucumber `{word}` would take the "a" of "a table" for the view.
 */
When(/^they show the modules as (?:a )?(cards|list|table)$/, async ({ world }, view) => world.home.chooseView(VIEW_WORD[view]))

Then(/^the modules are shown as (?:a )?(cards|list|table)$/, async ({ world }, view) => world.home.expectView(VIEW_WORD[view]))

Then(/^the modules are still shown as (?:a )?(cards|list|table)$/, async ({ world }, view) =>
  world.home.expectView(VIEW_WORD[view]))

// Each name a parameter of its own: playwright-bdd holds a step's function to one argument per `{string}`.
Then('the first screen shows the modules {string}, {string} and {string}', async ({ world }, first, second, third) => {
  for (const name of [first, second, third]) await expect(world.home.moduleLink(name)).toBeVisible()
})

Then('{string} is not one of them', async ({ world }, name) => expect(world.home.moduleLink(name)).toHaveCount(0))

Then('the table says what {string} is for, and where it sits in the menu', async ({ world }, name) =>
  world.home.expectTableRow(name, TABLE_ROWS[name]))

Then('the first screen shows no figures', async ({ page }) => {
  // The figures' section is Month end's now (`MonthEndFiguresPage`), and the first screen's own landmark went with it.
  await expect(page.getByRole('region', { name: /figures/i })).toHaveCount(0)
})

Then('the first screen is light, and its appearance control says so', async ({ page, world }) => {
  await expect(world.home.appearance()).toHaveAccessibleName('Appearance · Light')
  await expect(world.home.appearance()).toHaveAttribute('aria-pressed', 'false')
  await expect(page.locator('html')).not.toHaveClass(/(^|\s)dark(\s|$)/)
})

/** A view as a sentence says it -> the switch's own word. */
const VIEW_WORD = { cards: 'Cards', list: 'List', table: 'Table' }

/**
 * What the table says about a module a scenario names: the line under the module, and a path to it in the rail as the
 * rail prints each step. Read from the built client (`homeModules.js` on `oct/home`).
 */
const TABLE_ROWS = {
  'Debit Order Deductions': {
    for: 'The monthly debit order run, re-debits, unpaids and letters',
    inTheMenu: 'Admin Menu › Debit Orders',
  },
}
