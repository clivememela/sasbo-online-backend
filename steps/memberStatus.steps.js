import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { MembersApi } from '../api/MembersApi.js'
import { MemberStatusApi } from '../api/MemberStatusApi.js'
import { MemberRecordPage } from '../pages/MemberRecordPage.js'

/**
 * A member's status and the record of what changed (month end `03.1`–`03.6`).
 *
 * Each scenario's member is its own (`a member captured for this scenario`), opened in the register by the surname
 * minted for it. "Has not changed" is read from the server, the record's source; what the record SAYS is read from
 * the record. A change asked for as an anonymous caller would ask it -- a reason of the wrong kind, which the screen
 * never offers -- goes through `MemberStatusApi`, and so does the change another administrator makes behind an open
 * record.
 */

/** The member's record, opened once a scenario needs it. */
const recordOf = async (page, world) => (world.record ??= await new MemberRecordPage(page).openFor(world.member))

When("an administrator changes the member's status to {word}, with the union's code {int}", async ({ page, world }, status, code) =>
  (await recordOf(page, world)).changeStatus(status, code))

When("an administrator changes the member's status to {word}", async ({ page, world }, status) =>
  (await recordOf(page, world)).changeStatus(status))

Then("the member's status is {word}", async ({ page, world }, status) => {
  const record = await recordOf(page, world)
  if (await record.dialog().isVisible()) await record.cancel()
  await record.expectStatus(status)
})

Then("the member's history shows the change to {word}, with the union's code {int} and its name", async ({ page, world }, status, code) =>
  (await recordOf(page, world)).expectStatusChange(status, code))

Then("the member's history shows the return to {word}", async ({ page, world }, status) =>
  (await recordOf(page, world)).expectReinstatedInto(status))

When("an administrator chooses {word} as the member's new status, and no code", async ({ page, world }, status) =>
  (await recordOf(page, world)).chooseWithoutCode(status))

Then("they are asked for the union's code, and the change cannot be made", async ({ world }) => world.record.expectAskedForCode())

Then("the member's status has not changed", async ({ api, world }) =>
  expect((await new MembersApi(api.request).findMember(world.member.id)).status).toBe(world.member.status))

When("a change to Suspended is asked for with the union's code for leaving", async ({ api, world }) => {
  world.refusal = await new MemberStatusApi(api.request).change(world.member.id, {
    toStatus: 'Suspended', expectedStatus: world.member.status, reason: 'DELETION_CODE', deletionCode: 2,
  }, { expectOk: false })
})

Then('it is refused: {string}', async ({ world }, sentence) => {
  expect(world.refusal.status, 'refused as the wrong kind of reason').toBe(400)
  expect(world.refusal.body.message).toBe(sentence)
})

Given("an administrator has the member's record open", async ({ page, world }) => recordOf(page, world))

Given('another administrator has suspended the member since', async ({ api, world }) => new MemberStatusApi(api.request).suspend(world.member))

When("the first administrator changes the member's status to {word}, with the union's code {int}", async ({ world }, status, code) =>
  world.record.attemptStatusChange(status, code))

Then('they are told when the member was moved to {word}, and that nothing was changed', async ({ api, world }, status) =>
  world.record.expectToldMoved(status, await new MemberStatusApi(api.request).lastChangedAt(world.member)))

Given('the member has resigned', async ({ api, world }) => new MemberStatusApi(api.request).resign(world.member))

When("an administrator edits the member's record", async ({ page, world }) => (await recordOf(page, world)).startEditing())

Then('the status is shown and cannot be edited there, and the record says the way to change it', async ({ world }) =>
  world.record.expectStatusNotEditable())

When("an administrator opens the member's history", async ({ page, world }) => (await recordOf(page, world)).openHistory())

Then('it shows only that the member was captured', async ({ world }) => world.record.expectOnlyCaptured())
