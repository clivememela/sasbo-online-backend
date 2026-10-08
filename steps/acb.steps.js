import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { AcbApi } from '../api/AcbApi.js'
import { AcbDebitsPage } from '../pages/AcbDebitsPage.js'
import { AcbRunsPage } from '../pages/AcbRunsPage.js'

/**
 * The Re-Debits screen (ACB `04.3`). The decision is made on the screen, as an administrator makes it; the
 * unpaid it is made on is found or minted through the API first, because a decision is final and no scenario
 * could otherwise run twice on one stack (see `AcbApi`).
 */

/** Today in Johannesburg, YYYY-MM-DD -- the day a late return is noted. */
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg' }).format(new Date())

Given('the latest returned debit order month has an undecided unpaid its code would present again', async ({ api, world }) => {
  world.acbApi = new AcbApi(api.request)
  const run = await world.acbApi.latestReturnedRun()
  expect(run, 'the seed carries a returned month (ACB 03.8) -- no run is RETURNED or SUBMITTED').toBeTruthy()
  world.acbRun = run
  world.unpaid = await world.acbApi.undecidedUnpaidToPresentAgain(run.id, today())
  expect(world.unpaid.decision, 'the unpaid this scenario decides must still be undecided').toBeNull()
})

When('an administrator presents that unpaid again from the Debits screen', async ({ page, world }) => {
  world.acb = await new AcbDebitsPage(page).open()
  const row = world.acb.unpaidRow(world.unpaid.membershipNumber)
  await expect(row.getByRole('combobox', { name: /^Decision on/ }),
    'the decision its code suggests is already chosen (Q40, Q44)').toHaveValue('RE_PRESENT')
  await row.getByRole('button', { name: /^Confirm the decision/ }).click()
})

Then('the screen says the unpaid is presented again in the next run', async ({ world }) => {
  await expect(world.acb.recordedNotice()).toContainText('is presented again in the next run')
  // The row says the decision the server recorded, and offers the choice no more: a decision is final.
  const row = world.acb.unpaidRow(world.unpaid.membershipNumber)
  await expect(row).toContainText(/Present again\s*recorded/)
  await expect(row.getByRole('combobox')).toHaveCount(0)
})

Then('the server holds the decision to present it again', async ({ world }) => {
  expect(await world.acbApi.decisionOf(world.acbRun.id, world.unpaid.id)).toBe('RE_PRESENT')
})

// ── ACB 06.1: the runs ───────────────────────────────────────────────────────────────────────────────

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December']

/** The month after this one in Johannesburg -- what the Runs screen offers second -- as its value and its words. */
const nextMonth = () => {
  const [year, month] = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit' })
    .format(new Date()).split('-').map(Number)
  const [y, m] = month === 12 ? [year + 1, 1] : [year, month + 1]
  return { value: `${y}-${String(m).padStart(2, '0')}`, label: `${MONTHS[m - 1]} ${y}` }
}

/** Cents, so an amount is compared exactly and never as a float. */
const cents = (amount) => Math.round(Number(amount) * 100)

/**
 * Prepares next month's run through the screen and answers the server's response. A run that was made
 * registers the restore that cancels it -- a cancelled run releases its month, which is what lets the next
 * scenario prepare the same month.
 */
const prepareNextMonth = async ({ page, api, world }) => {
  world.acbApi ??= new AcbApi(api.request)
  world.month = nextMonth()
  world.runsPage ??= await new AcbRunsPage(page).open()
  const response = await world.runsPage.prepare(world.month.value, world.month.label)
  if (response.status() === 202) {
    const run = await response.json()
    world.registerRestore(`the ${world.month.label} run`, () => world.acbApi.cancelIfLive(run.id))
    if (world.run) world.earlierRun = world.run
    world.run = run
  }
  return response
}

Given('the debit order run for the next month has been prepared', async ({ page, api, world }) => {
  expect((await prepareNextMonth({ page, api, world })).status(), 'preparing the next month').toBe(202)
  await expect(world.runsPage.prepared(world.month.label)).toBeVisible({ timeout: 60_000 })
})

When('an administrator prepares the debit order run for the next month', async ({ page, api, world }) => {
  world.prepareResponse = await prepareNextMonth({ page, api, world })
})

When('an administrator prepares the debit order run for the next month again', async ({ page, api, world }) => {
  world.prepareResponse = await prepareNextMonth({ page, api, world })
})

When('an administrator cancels the run', async ({ world }) => {
  world.runsPage = await world.runsPage.open()
  await world.runsPage.openRun(world.month.label)
  await world.runsPage.cancelOpenRun(world.month.label)
})

When('an administrator opens the debit order runs', async ({ page, world }) => {
  world.runsPage = await new AcbRunsPage(page).open()
})

When('an anonymous caller asks the API to approve the run', async ({ world }) => {
  const run = await world.acbApi.run(world.run.id)
  world.approval = await world.acbApi.approveAnonymously(run.id, run.version)
})

Then('the run is prepared', async ({ world }) => {
  expect(world.prepareResponse.status(), 'the server took the preparation').toBe(202)
  await expect(world.runsPage.prepared(world.month.label)).toBeVisible({ timeout: 60_000 })
  expect((await world.acbApi.run(world.run.id)).status).toBe('PREPARED')
})

Then("the run's control total is the sum of its lines", async ({ world }) => {
  const run = await world.acbApi.run(world.run.id)
  const lines = await world.acbApi.allLines(run.id)
  expect(lines.length, 'the run debits somebody -- the seed carries a debit-order population (ACB 00.8)').toBeGreaterThan(0)
  expect(lines.reduce((sum, line) => sum + cents(line.amount), 0)).toBe(cents(run.totalAmount))
  // And the screen says the same total the server holds: its digits, whatever the grouping and the decimal mark.
  const shown = world.runsPage.fact('Control total')
  await expect(shown).toBeVisible()
  expect((await shown.textContent()).replace(/\D/g, '')).toBe(String(cents(run.totalAmount)))
})

Then('every debit of the month is charged the rate in force for it', async ({ world }) => {
  const { rates } = await world.acbApi.json(await world.acbApi.get('/api/acb/rates'))
  const first = `${world.month.value}-01`
  const inForce = rates.filter((rate) => rate.effectiveFrom <= first).at(-1)
  expect(inForce, `no rate reaches ${world.month.value}`).toBeTruthy()
  // A re-presentation charges the unpaid's own amount (Q43), so only the month's own debits are held to the rate.
  const own = (await world.acbApi.allLines(world.run.id)).filter((line) => line.rePresentsLineId == null)
  expect(own.length).toBeGreaterThan(0)
  expect(own.filter((line) => cents(line.amount) !== cents(inForce.amount))).toEqual([])
})

Then('the run names the members it leaves out, each with a reason', async ({ world }) => {
  const rows = world.runsPage.exclusionsTable().locator('tr[data-row]')
  await expect(rows.first(), 'the seed no longer leaves any member out of a run; find another fixture').toBeVisible()
  const count = await rows.count()
  for (let i = 0; i < count; i += 1) {
    await expect(rows.nth(i)).not.toHaveText(/^\s*$/)
  }
})

Then("they are told that month's run already exists, and what state it is in", async ({ world }) => {
  expect(world.prepareResponse.status()).toBe(409)
  await expect(world.runsPage.alreadyExists(world.month.label)).toContainText('prepared')
})

Then('there is one live run for that month', async ({ world }) => {
  const live = (await world.acbApi.runs())
    .filter((run) => run.actionMonth === world.month.value && !['CANCELLED', 'FAILED', 'DECLINED'].includes(run.status))
  expect(live).toHaveLength(1)
})

Then('the cancelled run is still on record, marked cancelled', async ({ world }) => {
  expect(world.earlierRun, 'the scenario prepared twice').toBeTruthy()
  expect((await world.acbApi.run(world.earlierRun.id)).status).toBe('CANCELLED')
  await world.runsPage.open()
  await expect(world.runsPage.runRows(world.month.label).filter({ hasText: 'Cancelled' }).first()).toBeVisible()
})

Then("the runs list shows that month's run, with its state and its control total", async ({ world }) => {
  const run = await world.acbApi.run(world.run.id)
  const row = world.runsPage.runRows(world.month.label).first()
  await expect(row).toContainText('Prepared')
  // The control total's digits, whatever the grouping and the decimal mark.
  expect((await row.textContent()).replace(/\D/g, '')).toContain(String(cents(run.totalAmount)))
})

Then('the approval is refused until administrators can sign in', async ({ world }) => {
  expect(world.approval.status, 'an anonymous caller approved a run').toBe(403)
  expect(world.approval.body.code).toBe('IDENTITY_REQUIRED')
})

Then('the run is still prepared, not approved', async ({ world }) => {
  expect((await world.acbApi.run(world.run.id)).status).toBe('PREPARED')
})
