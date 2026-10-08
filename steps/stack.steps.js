import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { startService, stopService } from '../support/stack.js'
import { BankListingPage } from '../pages/BankListingPage.js'

/**
 * The steps that take the API away and give it back.
 *
 * 🔑 STOPPING THE SERVER ALSO REGISTERS PUTTING IT BACK, at the moment it is
 * stopped, and the restore runs in the world's teardown -- so it runs even when
 * the scenario fails. A scenario that takes the stack down and only restores it
 * in a later step leaves the stack down whenever it fails, and every scenario
 * after it then fails for a reason that has nothing to do with what it tests.
 * That is the shape of failure that gets a whole suite declared flaky.
 *
 * Starting a container that is already running is a no-op, so the explicit
 * "the API is started again" step and the automatic restore cannot conflict.
 */

async function takeTheApiDown(world) {
  world.registerRestore('the API container', () => startService('server'))
  await stopService('server')
}

/**
 * ⚠️ Registered ONCE, although the feature files write it as both `Given` and
 * `When`. Gherkin keywords are prose: a step definition matches on its text, so
 * declaring the same sentence under two keywords is a duplicate-step error
 * rather than two steps.
 */
Given('the API is stopped', async ({ world }) => {
  await takeTheApiDown(world)
})

When('the API is started again', async () => {
  await startService('server')
})

Given('an administrator has the bank listing open', async ({ page, world }) => {
  world.listing = await new BankListingPage(page).open()
})

When('they reload the bank listing', async ({ page, world }) => {
  await page.reload()
  world.listing = new BankListingPage(page)
})

When('an administrator navigates to the bank listing', async ({ page, world }) => {
  world.listing = new BankListingPage(page)
  await page.goto(`${world.listing.baseURL}${world.listing.path}`)
})

/**
 * 🔴 THE 90-SECOND CEILING IS THE FINDING, not a workaround for a slow test.
 *
 * The client sets no deadline of its own on any request (A14), so how long an
 * administrator watches a spinner is decided by the infrastructure in front of
 * the API rather than by the application. Measured against this stack, with the
 * API container stopped and the page reloaded: the screen said
 * "Loading banks..." for a full **60 seconds**, then the proxy returned 504 and
 * the error state appeared. Sixty seconds is nginx's default
 * `proxy_read_timeout` -- a number nobody in this system chose.
 *
 * ⚠️ An earlier measurement of 14 seconds was the FIRST request after the stop,
 * taken from the host, while the container's endpoint was still being torn
 * down. It is not what a reloaded page sees, and building the ceiling on it
 * would have produced a scenario that failed on a screen about to be right.
 *
 * The scenario immediately below asserts, and skips, the ten-second version:
 * that the application gives up on its own instead of waiting to be told.
 */
Then('the listing reports that it could not be loaded', async ({ world }) => {
  await expect(world.listing.errorState()).toBeVisible({ timeout: 90_000 })
})

Then('the listing reports that it could not be loaded before the proxy would give up', async ({ world }) => {
  // Twice the client's own 10 s read ceiling, a third of nginx's 60 s. See the feature.
  await expect(world.listing.errorState()).toBeVisible({ timeout: 20_000 })
})

/**
 * 🔴 THE ASSERTION THAT MAKES THE SCENARIO WORTH RUNNING.
 *
 * "Could not load" and "there are none" are the two answers a list can give,
 * and only one of them is true here. A screen that renders the empty state on a
 * failure tells an administrator there are no banks when the backend is
 * unreachable -- the confident wrong answer -- and a test that only checked for
 * the error message would still pass if the empty message were shown beside it.
 */
Then('the listing does not claim there are no banks', async ({ world }) => {
  await expect(world.listing.emptyState()).toHaveCount(0)
})

Then('the register reports that it could not be loaded', async ({ world }) => {
  // 🔴 NOT the default 10 s. The register makes ONE request and gives up at the client's own
  // 10 s read ceiling, measured from when the request started -- which is before this
  // assertion starts. At 10 s the two raced, and on 2026-09-16 the screen was caught still
  // saying "It will stop trying shortly". Twice the ceiling, a third of nginx's 60 s, as A14's.
  await expect(world.memberRegister.errorState()).toBeVisible({ timeout: 20_000 })
})

Then('the register does not claim there are no members', async ({ world }) => {
  await expect(world.memberRegister.emptyState()).toHaveCount(0)
})

Then('the listing offers a way to try again', async ({ world }) => {
  await expect(world.listing.retry()).toBeVisible()
})
