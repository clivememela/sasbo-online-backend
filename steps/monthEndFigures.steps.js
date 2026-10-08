import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { MemberStatusApi } from '../api/MemberStatusApi.js'
import { MonthEndApi } from '../api/MonthEndApi.js'
import { MonthEndFiguresPage } from '../pages/MonthEndFiguresPage.js'
import { monthName } from '../pages/MonthEndPage.js'
import { dateAsWritten, monthBefore, monthOfDate } from '../support/journeyMonth.js'
import { captureAMemberOfItsOwn } from '../support/monthEndMember.js'

/**
 * The month's figures, on Month end (month end `02.2`, `02.3`) -- the first screen's until the 5 Oct Show & Tell
 * (client `oct/home`).
 *
 * DELTAS, NEVER TOTALS. Other journeys' members are on this stack and cannot be deleted (D9), so a scenario reads a
 * figure, does one thing, and reads it again. That only means anything because `journeys-monthly` runs alone: nothing
 * else changes a member between the two readings.
 *
 * 🔑 AND ALWAYS OF THE OPEN MONTH, NAMED. Month end opens on the newest month TAKEN, whose figures are frozen and would
 * not move for anything a scenario did -- so every reading that is to see a change names the open month, as the server
 * says it (`MonthEndApi`), never a clock's.
 */

const STRIP = ['Members on record', 'Suspended', 'Paying members']

/** The page, read for its figures -- opened by the scenario's first step that needs it. */
const figuresOf = (page, world) => (world.figures ??= new MonthEndFiguresPage(page))

/** The open month's figures read again, once, after the scenario's one act. */
const readAgain = async (page, world, terms) =>
  (world.after ??= await new MonthEndFiguresPage(page).openAndRead(world.openMonth, terms))

/** The month the server says is open, asked once a scenario. */
const openMonthOf = async (api, world) => (world.openMonth ??= await new MonthEndApi(api.request).openMonth())

Given('a member captured for this scenario', async ({ page, api, world }) => {
  world.member = await captureAMemberOfItsOwn({ page, api, world })
})

When('a member is captured for this scenario', async ({ page, api, world }) => {
  world.member = await captureAMemberOfItsOwn({ page, api, world })
})

When('an administrator reads the figures for the month that is open', async ({ page, api, world }) => {
  world.month = await openMonthOf(api, world)
  world.figures = await new MonthEndFiguresPage(page).openAt(world.month)
})

When('an administrator reads the figures for that month', async ({ page, world }) => {
  world.figures = await new MonthEndFiguresPage(page).openAt(world.month)
})

When('they choose the month before it', async ({ world }) => {
  world.month = monthBefore(world.month)
  await world.figures.chooseMonth(world.month)
})

Then("the page shows the month's figures", async ({ page, world }) => figuresOf(page, world).expectFiguresShown())

Then('the page shows its statistics', async ({ page, world }) =>
  expect(figuresOf(page, world).statistics().first()).toBeVisible())

Then('the figures are those of the newest month taken', async ({ page, api, world }) =>
  figuresOf(page, world).expectMonthShown(monthName(await new MonthEndApi(api.request).newestTakenMonth())))

Then('the figures say what each counted:', async ({ world }, table) => world.figures.expectDefinitions(table.hashes()))

Given("an administrator has read the open month's figures for members on record, suspended and paying", async ({ page, api, world }) => {
  world.before = await new MonthEndFiguresPage(page).openAndRead(await openMonthOf(api, world), STRIP)
})

When("that member is suspended, with the union's reason", async ({ api, world }) => new MemberStatusApi(api.request).suspend(world.member))

When("that member leaves, with the union's reason", async ({ api, world }) => new MemberStatusApi(api.request).resign(world.member))

Then('the figure for members on record has not changed', async ({ page, world }) =>
  expect((await readAgain(page, world, STRIP))['Members on record']).toBe(world.before['Members on record']))

Then('the figure for suspended members is one more than it was', async ({ page, world }) =>
  expect((await readAgain(page, world, STRIP)).Suspended).toBe(world.before.Suspended + 1))

Then('the figure for paying members is one fewer than it was', async ({ page, world }) =>
  expect((await readAgain(page, world, STRIP))['Paying members']).toBe(world.before['Paying members'] - 1))

Given("an administrator has read the figures for members who joined in the server's month and the month before it", async ({ page, api, world }) => {
  const open = await openMonthOf(api, world)
  world.joinedBefore = await new MonthEndFiguresPage(page).readJoinedFor([open, monthBefore(open)])
})

Then("the figure for members who joined in the month of that member's join date is one more than it was", async ({ page, world }) => {
  const month = monthOfDate(dateAsWritten(world.member.joinDate))
  expect(Object.keys(world.joinedBefore), "the member's month is one of the two read before").toContain(month)
  const figures = await new MonthEndFiguresPage(page).openAt(month)
  expect(await figures.readNumber('Joined this month')).toBe(world.joinedBefore[month] + 1)
})

Given("an administrator has read the open month's figures for members on record and for members who left", async ({ page, api, world }) => {
  world.before = await new MonthEndFiguresPage(page).openAndRead(await openMonthOf(api, world), ['Members on record', 'Left this month'])
})

Then('the figure for members who left this month is one more than it was', async ({ page, world }) =>
  expect((await readAgain(page, world, ['Members on record', 'Left this month']))['Left this month']).toBe(world.before['Left this month'] + 1))

Then('the figure for members on record is one fewer than it was', async ({ page, world }) =>
  expect((await readAgain(page, world, ['Members on record', 'Left this month']))['Members on record']).toBe(world.before['Members on record'] - 1))

Then('the figures are shown by employer group', async ({ world }) => expect(world.figures.sheet('Employer group')).toBeVisible())

Then('they can be grouped by employer, by table code, by area, by region and by committee', async ({ world }) =>
  expect(await world.figures.groupings()).toEqual(['Employer group', 'Employer', 'Table code', 'Area', 'Region', 'Committee']))

Then('the sheet by employer group adds up to the paying and suspended members above it', async ({ world }) =>
  world.figures.expectSheetAddsUp())

Then('so does the sheet by area', async ({ world }) => world.figures.expectSheetAddsUp('Area'))

Then('the members with no workplace on record are one line in every grouping', async ({ world }) =>
  world.figures.expectNoWorkplaceRowInEveryGrouping())

Then('the screen says how many members have no workplace on record', async ({ world }) =>
  expect(world.figures.note(/\d members? ha(s|ve) no workplace on record/)).toBeVisible())

Given('a past month that has not been taken', async ({ api, world }) => {
  world.month = await new MonthEndApi(api.request).newestPastMonthNotTaken()
})

Then('the screen says the figures are grouped by where each member works today', async ({ world }) =>
  expect(world.figures.note('Grouped by where each member works today.')).toBeVisible())

Then('the screen says its members are grouped where they stood when it was taken', async ({ world }) =>
  expect(world.figures.note(`As taken: each member is grouped where they stood when ${monthName(world.month)} was taken.`)).toBeVisible())

Then('the screen says how many of the members are invented, for demonstration', async ({ world }) =>
  expect(world.figures.note(/^[\d\s]+ of these members are invented, for demonstration;/)).toBeVisible())

Then("the screen says which of SASBO's lists grouped the figures", async ({ world }) =>
  expect(world.figures.note(/^Grouped by SASBO's lists /)).toBeVisible())

Then('the figures are those of that month', async ({ world }) => world.figures.expectMonthShown(monthName(world.month)))

// ── With the API stopped (features/serial): the steps that take it down and bring it back are `stack.steps.js`'s ──

When('an administrator has Month end open, its figures not available', async ({ page, world }) => {
  world.figures = await new MonthEndFiguresPage(page).openWithFiguresNotAvailable()
})

Then("the month's figures say they could not be read", async ({ page, world }) =>
  figuresOf(page, world).expectFiguresNotAvailable({ timeout: 90_000 }))

/**
 * ⚠️ FORTY SECONDS, AND WHY NOT THE BANK LISTING'S TWENTY. Month end asks for its months first and for the figures only
 * once the months have answered -- or failed -- so with the API stopped the figures give up after TWO of the client's
 * ten-second reads, one after the other: about twenty seconds. Forty is twice that, so a slow host does not decide it,
 * and a third of the proxy's two sixty-second waits, so a screen waiting on the proxy still fails it -- the same two
 * margins the listing's twenty keeps around its one read.
 */
Then("the month's figures say they could not be read before the proxy would give up", async ({ page, world }) =>
  figuresOf(page, world).expectFiguresNotAvailable({ timeout: 40_000 }))

Then('no figure on Month end reads nought', async ({ page, world }) => figuresOf(page, world).expectNoFigureReadsNought())

Then("the month's figures offer a way to try again", async ({ page, world }) => figuresOf(page, world).expectRetryOffered())

When("they ask the month's figures to try again", async ({ world }) => world.figures.tryAgain())
