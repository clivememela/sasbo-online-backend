import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { BanksApi } from '../api/BanksApi.js'
import { BankListingPage } from '../pages/BankListingPage.js'
import { BankBranchPage } from '../pages/BankBranchPage.js'
import { mintTestName } from '../support/guards.js'

/**
 * Step definitions for the banks and branches journey.
 *
 * EVERY STEP HERE IS A ONE-LINER THAT DELEGATES. Nothing in this file knows a
 * selector, a URL or an endpoint -- that is the whole convention, and it is what
 * lets a selector change touch one file instead of every feature that mentions
 * the screen. If a step needs more than a line, the logic belongs in a page
 * object or an API client and the step calls it.
 *
 * `Given`/`When`/`Then` come from `../support/fixtures.js`, never from
 * `playwright-bdd` directly -- the library's bindings have no `api` or `world`.
 */

Given('the branch register is available', async ({ api, world }) => {
  world.banksApi = new BanksApi(api.request)
  world.branches = await world.banksApi.listBranches()
  expect(world.branches.length, 'the register seeded no branches').toBeGreaterThan(0)
})

When('an administrator opens the bank listing', async ({ page, world }) => {
  world.listing = await new BankListingPage(page).open()
})

When('an administrator opens the branch register', async ({ page, world }) => {
  world.branchRegister = await new BankBranchPage(page).open()
})

When('they search the listing for the bank name {string}', async ({ world }, name) => {
  await world.listing.filterBy('Bank', name)
})

When('they open the first branch on record', async ({ world }) => {
  world.branch = world.branches[0]
  await world.branchRegister.openBranch(world.branch)
})

When("the manager's name on the first branch is corrected", async ({ world }) => {
  world.branch = world.branches[0]
  world.newManager = mintTestName('mgr')
  world.registerCleanup(world.newManager, 'manager name', await world.banksApi.changeFieldReversibly(
    world.branch.id, 'managerName', world.newManager,
  ))
})

When('they choose to edit it', async ({ world }) => {
  await world.branchRegister.clickEdit()
})

When('they begin adding a branch but leave the bank name and branch empty', async ({ world }) => {
  await world.branchRegister.startAdding()
})

When('they try to save it', async ({ world }) => {
  await world.branchRegister.save()
})

Then('the listing reports every bank on record', async ({ world }) => {
  expect(await world.listing.reportedCount()).toBeGreaterThan(0)
})

Then('the listing shows {string}', async ({ world }, name) => {
  await expect(world.listing.row(name)).toBeVisible()
})

Then('the listing no longer shows {string}', async ({ world }, name) => {
  await expect(world.listing.row(name)).toHaveCount(0)
})

Then('the branch details show its bank name', async ({ world }) => {
  await world.branchRegister.expectDetail('Bank Name', world.branch.bankName)
})

Then('the branch details show its area', async ({ world }) => {
  await world.branchRegister.expectDetail('Area', world.branch.area)
})

Then("the register shows the new manager's name", async ({ world }) => {
  // Manager is no longer a column on a desk, so the corrected name is read from the branch's
  // own record -- which is still the register, one click in.
  await world.branchRegister.openBranch(world.branch)
  await world.branchRegister.expectDetail('Manager Name', world.newManager)
})

// Was A29's crash: these three are the edit it now makes (client `oct/bugs` 5bc3fc1).
Then("the branch's edit form shows its bank and its branch", async ({ world }) => {
  await world.branchRegister.expectEditForm(world.branch)
})

When("they correct the branch manager's name on the form and save it", async ({ world }) => {
  // The undo is registered BEFORE the save, for the run where the save lands and an assertion then fails.
  world.newManager = mintTestName('mgr')
  const original = world.branch.managerName
  world.registerCleanup(world.newManager, 'manager name', () => world.banksApi.updateBranch(world.branch.id, { managerName: original }))
  await world.branchRegister.saveFieldOnForm('Manager Name', world.newManager)
})

Then("the branch's details show the new manager's name, and so does the register", async ({ world }) => {
  await world.branchRegister.expectDetail('Manager Name', world.newManager)
  expect((await world.banksApi.findBranch(world.branch.id)).managerName, 'the manager the server holds').toBe(world.newManager)
})

Then('they are told the bank name and branch are required', async ({ world }) => {
  await world.branchRegister.expectFormError('Bank Name and Branch are required.')
})

Then('no branch has been added', async ({ world }) => {
  expect(await world.banksApi.listBranches()).toHaveLength(world.branches.length)
})
