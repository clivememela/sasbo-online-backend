import { Given, When, Then } from '../support/fixtures.js'
import { expectSignInRequired } from '../api/AuthApi.js'
import { MonthEndApi } from '../api/MonthEndApi.js'
import { MonthEndPage } from '../pages/MonthEndPage.js'
import { Navigation } from '../pages/Navigation.js'

/**
 * The Month end page's journeys (month end `04.1`).
 *
 * One-liners that delegate, as everywhere in this directory. They read and choose; none of them
 * writes, because taking a month refuses every caller until sign-in. A month a scenario names comes
 * from the server's own months -- `MonthEndApi` -- and never from the browser's clock.
 */

When('an administrator opens the Admin menu', async ({ page }) => new Navigation(page).openAdminMenu())

When('they choose Month end', async ({ page }) => new Navigation(page).chooseMonthEndCard())

When("an administrator opens the navigation's Admin Menu", async ({ page }) => new Navigation(page).openRailSection('Admin Menu'))

When('they choose Month end there', async ({ page }) => new Navigation(page).chooseRailLeaf('Month end'))

Then('the Month end page is shown', async ({ page }) => new MonthEndPage(page).expectLoaded())

Then('the page says where it is in the application', async ({ page }) => new Navigation(page).expectTrail('Admin Menu', 'Month end'))

When('an administrator opens the Month end page', async ({ page, world }) => {
  world.monthEnd = await new MonthEndPage(page).open()
})

Then('every statistic opens a page of its own', async ({ world }) => world.monthEnd.expectEveryStatisticHasAPageOfItsOwn())

Then('no statistic opens the page it is on', async ({ world }) => world.monthEnd.expectNoStatisticOpensThisPage())

Then('the statistics are shown with the names the old system gave them:', async ({ world }, table) =>
  world.monthEnd.expectStatisticsWithOldNames(table.hashes()))

Then('each listing of members says that it waits for sign-in', async ({ world }) => world.monthEnd.expectListingsWaitForSignIn())

Then('no listing of members can be opened', async ({ world }) => world.monthEnd.expectNoListingCanBeOpened())

Given('a month that has been taken', async ({ api, world }) => {
  world.month = await new MonthEndApi(api.request).newestTakenMonth()
})

Given('the month that is open', async ({ api, world }) => {
  world.month = await new MonthEndApi(api.request).openMonth()
})

When('an administrator chooses that month on the Month end page', async ({ page, world }) => {
  world.monthEnd = await new MonthEndPage(page).openOn(world.month)
})

Then('the page says when that month was taken', async ({ world }) => world.monthEnd.expectTakenSaid(world.month))

Then('taking that month is offered and cannot be done', async ({ world }) => world.monthEnd.expectTakeOfferedAndRefused(world.month))

Then('the page says that taking a month waits for sign-in', async ({ world }) => world.monthEnd.expectTakeWaitsForSignIn())

// Sign-in (server 9641f05) holds the take by MONTH_END_TAKE: nobody signed in is answered 401 SIGN_IN_REQUIRED. Asked for
// the month after the open one -- whatever happened to the permission, no take of a month that has not begun can land.
Then('a take asked for without signing in is refused until someone signs in', async ({ api }) => {
  const months = new MonthEndApi(api.request)
  expectSignInRequired(await months.attemptTake(await months.monthNotBegun()), 'a take asked for without signing in')
})
