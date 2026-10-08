import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { AuthApi, expectSignInRequired } from '../api/AuthApi.js'
import { SubscriptionsApi } from '../api/SubscriptionsApi.js'
import { SubscriptionsPage } from '../pages/SubscriptionsPage.js'
import { AcbDebitsPage } from '../pages/AcbDebitsPage.js'

/** The subscriptions and debit order journeys. One-liners that delegate. */

Given('the subscriptions register is available', async ({ api, world }) => {
  world.subscriptionsApi = new SubscriptionsApi(api.request)
  world.subscriptions = await world.subscriptionsApi.listSubscriptions()
  expect(world.subscriptions.length, 'the register seeded no subscriptions').toBeGreaterThan(0)
})

When('an administrator opens the subscriptions register', async ({ page, world }) => {
  world.subscriptionsPage = await new SubscriptionsPage(page).open()
})

When('they filter the subscriptions by the bank {string}', async ({ world }, bankName) => {
  await world.subscriptionsPage.filterByBank(bankName)
})

/**
 * 🔒 AS THE CHIEF FINANCIAL OFFICER: a subscription write needs `SUBSCRIPTIONS_MAINTAIN` since the October build, and
 * the CFO's position holds it. The restore is the CFO's too -- an anonymous one would be refused, and leave the day
 * changed for every later capture.
 */
When('the Chief Financial Officer corrects the debit order day on {string} to {int}', async ({ api, world }, bankName, day) => {
  const row = world.subscriptions.find((subscription) => subscription.bankName === bankName)
  expect(row, `no subscription is on record for ${bankName}`).toBeTruthy()
  const { token } = await new AuthApi(api.request).signInAs('Chief Financial Officer')
  world.registerRestore('debit order day', await new SubscriptionsApi(api.request, { token }).changeFieldReversibly(
    row.id, 'acbDebitDay', day,
  ))
})

/**
 * The user manual's reading of Maintenance (6 Oct; client `oct/bugs`): an emptied debit order day was saved as 0 and shown
 * as "NaN". Every write to a subscription this page makes is listened for from here on, so "nothing is sent" is a fact.
 */
When('they empty the debit order day for {string} and save it', async ({ page, world }, bankName) => {
  world.bankName = bankName
  world.subscriptionWrites = []
  page.on('request', (request) => {
    if (request.method() === 'PUT' && new URL(request.url()).pathname.startsWith('/api/subscriptions/')) {
      world.subscriptionWrites.push(new URL(request.url()).pathname)
    }
  })
  await world.subscriptionsPage.editRow(bankName)
  await world.subscriptionsPage.dayField(bankName).fill('')
  await world.subscriptionsPage.saveRow(bankName)
})

Then('the debit order day is refused in words at its field, and nothing is sent', async ({ world }) => {
  await expect(world.subscriptionsPage.dayRefusal()).toBeVisible()
  await expect(world.subscriptionsPage.dayField(world.bankName)).toHaveAttribute('aria-invalid', 'true')
  expect(world.subscriptionWrites, 'subscription writes the page sent').toEqual([])
  await world.subscriptionsPage.cancelEdit()
})

Then('the register says a bank is debited only while its row is ACTIVE', async ({ world }) => {
  await expect(world.subscriptionsPage.activeMeaning()).toBeVisible()
})

Then('the register says that changing an amount needs signing in, and offers no change', async ({ world }) => {
  // The rows first: with none on screen, "no Edit control" would be true of an empty table.
  await expect(world.subscriptionsPage.rows()).toHaveCount(world.subscriptions.length)
  await expect(world.subscriptionsPage.changeNeedsSignIn()).toBeVisible()
  await expect(world.subscriptionsPage.editControls()).toHaveCount(0)
})

Then('a change to a subscription asked for without signing in is refused until someone signs in', async ({ world }) => {
  expectSignInRequired(await world.subscriptionsApi.attemptUnchangedWrite(world.subscriptions[0].id), 'a subscription written anonymously')
})

When('an administrator searches the Debits screen for the surname {string}', async ({ page, world }, surname) => {
  world.acb = await new AcbDebitsPage(page).open()
  await world.acb.search(surname)
})

Then('the register shows a subscription for {string}', async ({ world }, bankName) => {
  await expect(world.subscriptionsPage.row(bankName)).toHaveCount(1)
})

Then('the register no longer shows a subscription for {string}', async ({ world }, bankName) => {
  await expect(world.subscriptionsPage.row(bankName)).toHaveCount(0)
})

Then('the register shows one row for every subscription on record', async ({ world }) => {
  await expect(world.subscriptionsPage.rows()).toHaveCount(world.subscriptions.length)
})

// The column was "ACB Debit Day" until the module became Debit Order Deductions (client `oct/home`).
Then('the register shows a debit order day of {string} for {string}', async ({ world }, day, bankName) => {
  expect(await world.subscriptionsPage.cellText(bankName, 'Debit Order Day')).toBe(day)
})

Then('every row names a bank', async ({ world }) => {
  const blank = (await world.subscriptionsPage.bankNamesShown()).filter((name) => name === '')
  expect(blank, 'subscription rows showing no bank at all').toEqual([])
})

Then('the search reports at least one member', async ({ world }) => {
  expect(await world.acb.reportedResultCount()).toBeGreaterThan(0)
})

Then('the search says no members match', async ({ world }) => {
  await expect(world.acb.emptyState()).toBeVisible()
})

/**
 * The one assertion in this suite that joins the two halves of the system.
 *
 * The subscriptions table is not a screen with a table on it -- it is what a
 * new member gets charged, applied by the server on create regardless of what
 * the form said. This is the scenario that would notice if that link were cut,
 * and no cheaper layer sees it: the client never reads the amount, and the
 * server's own tests do not go through a browser capture.
 */
Then('the member is charged the subscription on record for {string}', async ({ world, api }, bankName) => {
  const subscriptions = await new SubscriptionsApi(api.request).listSubscriptions()
  const onRecord = subscriptions.find((subscription) => subscription.bankName === bankName)
  expect(onRecord, `no subscription is on record for ${bankName}`).toBeTruthy()

  expect(
    Number(world.createdMember.monthlySubscription),
    `what the new member is charged, against the ${bankName} subscription`,
  ).toBe(Number(onRecord.subscriptionAmount))
})

