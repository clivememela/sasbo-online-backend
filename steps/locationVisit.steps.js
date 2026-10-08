import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { VisitDraftsApi } from '../api/VisitDraftsApi.js'
import { WorkplacesApi } from '../api/WorkplacesApi.js'
import { LocationVisitPage } from '../pages/LocationVisitPage.js'
import { DEMO_USER_AREA } from '../support/demoUsers.js'
import { mintTestName } from '../support/guards.js'

/**
 * The Location Visit form's drafts (client and server `oct/reports-visits`). No workplace is named in this repository
 * (MD57): the scenario reads one of the Organiser's area from the server when it runs.
 *
 * 🔁 JUDGED BY THE DRAFT'S OWN ID. The gate runs every scenario three times at once, as the one demonstration
 * Organiser: their list of drafts holds the other runs' too, so what this run saved is followed by the id the server
 * gave it -- never by a line of a list.
 */

/** The drafts API as the signed-in Organiser asks it: their own drafts only. */
const draftsOf = (api, world) => new VisitDraftsApi(api.request, { token: world.session.token })

Given("a workplace in the Organiser's area", async ({ api, world }) => {
  const [workplace] = await new WorkplacesApi(api.request).openInArea(DEMO_USER_AREA.Organiser)
  expect(workplace, `an open workplace in area ${DEMO_USER_AREA.Organiser}`).toBeTruthy()
  world.workplace = workplace
})

When('the Organiser saves a visit to that workplace as a draft', async ({ page, api, world }) => {
  world.visitForm = await new LocationVisitPage(page).open()
  // The organiser's name is minted: what this run saved is unmistakably the suite's, and the cleanup may discard it.
  world.organiser = mintTestName('visit')
  await world.visitForm.fill('R / A Number', world.workplace.raNumber)
  await world.visitForm.fill('Organiser Name', world.organiser)
  world.draft = await world.visitForm.saveDraft()
  const drafts = draftsOf(api, world)
  world.registerCleanup(world.organiser, 'visit draft', () => drafts.discardIfThere(world.draft.id))
})

Then('the form says the draft is saved, and the server holds it as the Organiser\'s draft', async ({ api, world }) => {
  await world.visitForm.expectNotice(`Draft saved for ${world.draft.workplaceName} (R/A ${world.workplace.raNumber}).`)
  const { status, body } = await draftsOf(api, world).attemptFind(world.draft.id)
  expect(status, 'the draft, read back by its owner').toBe(200)
  expect({ status: body.status, raNumber: body.raNumber, organiser: body.organiser })
    .toEqual({ status: 'DRAFT', raNumber: world.workplace.raNumber, organiser: world.organiser })
})

When('the Organiser discards that draft', async ({ world }) => world.visitForm.discardDraft())

Then('the form says the draft is discarded, and the server holds it no more', async ({ api, world }) => {
  await world.visitForm.expectNotice(`Draft discarded for ${world.draft.workplaceName} (R/A ${world.workplace.raNumber}).`)
  const { status, body } = await draftsOf(api, world).attemptFind(world.draft.id)
  expect(status, 'the draft, asked for after it was discarded').toBe(404)
  expect(body?.code, "the refusal's code").toBe('VISIT_DRAFT_NOT_FOUND')
})
