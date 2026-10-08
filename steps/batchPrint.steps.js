import { Given, When, Then } from '../support/fixtures.js'
import { WorkplacesApi } from '../api/WorkplacesApi.js'
import { BatchPrintPage } from '../pages/BatchPrintPage.js'
import { DEMO_USER_AREA } from '../support/demoUsers.js'

/**
 * The organisers' batch print (client and server `oct/reports-visits`). No workplace is named in this repository
 * (MD57): each scenario reads the ones it prints from the server when it runs, and looks for them on the screen.
 */

/** The bank a batch print files a workplace under -- the employer its table code proves, or as the list prints it. */
const bankOf = (w) => w.employer ?? w.employerPrinted

Given("three workplaces in the Organiser's area, two at one bank and one at another", async ({ api, world }) => {
  world.area = DEMO_USER_AREA.Organiser
  world.printed = await new WorkplacesApi(api.request).threeAtTwoBanks(world.area)
})

// The slash escaped, as `workplaces.steps.js` says why: unescaped, "R/A" offers "R" or "A".
When("the Organiser prints their three R\\/A numbers in one batch, the two banks' numbers interleaved", async ({ page, world }) => {
  world.batch = await new BatchPrintPage(page).open()
  await world.batch.printTogether(world.printed.map((w) => w.raNumber))
})

Then('the listings are grouped by bank, each bank once, in the order its first number was typed', async ({ world }) => {
  const [first, other, second] = world.printed
  await world.batch.expectGroupedByBank([
    { name: bankOf(first), raNumbers: [first.raNumber, second.raNumber] },
    { name: bankOf(other), raNumbers: [other.raNumber] },
  ])
})

Then("each bank's heading names the Organiser's area and counts its branches", async ({ world }) => {
  await world.batch.expectHeading(0, { area: world.area, branches: 2 })
  await world.batch.expectHeading(1, { area: world.area, branches: 1 })
})

Then('each bank after the first starts a new printed page', async ({ world }) => {
  // Only once every branch has its members (or the sentence why not) does the page offer to print at all.
  await world.batch.expectReadyToPrint()
  await world.batch.expectEachBankAfterTheFirstOnANewPage()
})

Given('two workplaces on record, and a number that is no workplace', async ({ api, world }) => {
  const workplaces = new WorkplacesApi(api.request)
  const [first, other] = await workplaces.threeAtTwoBanks(DEMO_USER_AREA.Organiser)
  world.printed = [first, other]
  world.unknown = await workplaces.aNumberThatIsNoWorkplace()
})

When('the three numbers are printed in one batch', async ({ page, world }) => {
  const [first, other] = world.printed
  world.batch = await new BatchPrintPage(page).open()
  await world.batch.printTogether([first.raNumber, world.unknown, other.raNumber])
})

Then('the number that is no workplace is said to be not on record', async ({ world }) => world.batch.expectNotOnRecord(world.unknown))

Then("the two workplaces' listings are shown", async ({ world }) => {
  for (const w of world.printed) await world.batch.expectListing(w.raNumber)
})
