import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { WorkplacesApi } from '../api/WorkplacesApi.js'
import { MemberRecordPage } from '../pages/MemberRecordPage.js'
import { WorkplacesPage } from '../pages/WorkplacesPage.js'

/**
 * SASBO's workplaces (month end `01.5`) and a member's place in one (`01.3`).
 *
 * No workplace is named in this repository (MD57): one is read from the server when a scenario runs, and the scenario
 * looks for it on the screen by its name or its R/A number.
 */

Given('a workplace on record', async ({ api, world }) => {
  world.workplace = await new WorkplacesApi(api.request).oneWithItsLineage()
})

When("an administrator searches the workplaces for that workplace's name", async ({ page, world }) => {
  world.workplaces = await new WorkplacesPage(page).openAndSearch(world.workplace.name)
})

// The slash is escaped: in a Cucumber expression an unescaped "/" offers alternatives, and "R/A" would match "R" or "A".
When("an administrator searches the workplaces for that workplace's R\\/A number", async ({ page, world }) => {
  world.workplaces = await new WorkplacesPage(page).openAndSearch(world.workplace.raNumber)
})

Then('the workplace is shown with its R\\/A number, its employer, its area and its committee', async ({ world }) =>
  world.workplaces.expectRowWithLineage(world.workplace))

Then('that workplace is the only one shown', async ({ world }) => world.workplaces.expectOnlyRow(world.workplace.raNumber))

When('an administrator opens the workplaces', async ({ page, world }) => {
  world.workplaces = await new WorkplacesPage(page).open()
})

Then('one page of workplaces is shown, the number on record is said, and the pages count them all', async ({ world }) =>
  world.workplaces.expectPaged())

Then("the screen names the union's lists the workplaces were read from", async ({ world }) => world.workplaces.expectProvenance())

When("an administrator opens the member's record at its employment", async ({ page, world }) => {
  world.record = await new MemberRecordPage(page).openFor(world.member)
  await world.record.register.openRecordTab('Employment & Bank')
})

Then('the record says that no workplace is on record', async ({ world }) =>
  expect(world.record.record().getByText('No workplace on record', { exact: true })).toBeVisible())
