import { test as base, createBdd } from 'playwright-bdd'
import { ApiClient } from '../api/ApiClient.js'
import { API_URL } from './env.js'
import { World } from './world.js'

/**
 * The fixtures every step definition receives, and the BDD bindings built from
 * them.
 *
 * ALL STEP DEFINITIONS IMPORT `Given`/`When`/`Then` FROM HERE, never from
 * `playwright-bdd` directly. Importing from the library gets bindings without
 * these fixtures, so `api` and `world` arrive undefined -- and the failure
 * surfaces as a null-reference inside a step rather than as anything that
 * mentions fixtures. One import path is what prevents that.
 */
export const test = base.extend({
  /**
   * 🔴 GOOGLE MAPS NEVER LOADS IN THE SUITE'S BROWSERS.
   *
   * The client wraps its pages in the Maps JS API (`MapsScope`, for the address
   * field's suggestions), fetched from Google on every page with the live key the
   * client's tracked `.env` carries (register B2). The suite never picks a
   * suggestion -- it types the address as text, and `AddressAutocomplete` is a
   * plain input when Maps is absent -- so every load was a request to the
   * internet the verdict never needed: a third-party script's timing inside the
   * run, the live key's quota, the key in every uploaded trace, and suggestion
   * lists that can open over the next field a step fills.
   *
   * ⚠️ AND IT WAS THE LAST THING A STALLED PAGE DID. The ACB arc's final gate
   * (run 35659792559, 2026-09-21): on the member register with the API stopped,
   * the Maps loader arrived at 22:00:52.4 and the page made no request after it
   * -- not even the Maps modules that loader always fetches next -- nor fired its
   * own 10 s abort, nor took nginx's 502 at 14 s; at 151 s it was still loading,
   * and the same client and server had passed that scenario on the run before.
   * The most probable trigger, not proven by one occurrence; the block stands on
   * the reasons above whatever caused that stall.
   *
   * Aborted at the network, so a page sees what a user with Maps unreachable
   * sees. Only those two hosts are intercepted; the stack's own requests never
   * pass through a route handler.
   */
  context: async ({ context }, use) => {
    await context.route(/^https:\/\/maps\.(googleapis|gstatic)\.com\//, (route) => route.abort('blockedbyclient'))
    await use(context)
  },

  /**
   * The REST client, on Playwright's own request context so API calls appear in
   * the trace beside the browser steps.
   */
  api: async ({ request }, use) => {
    await use(new ApiClient(request))
  },

  /**
   * Per-scenario state, with cleanup that runs after the scenario regardless of
   * whether it passed.
   *
   * The cleanup is in the fixture's teardown rather than in an `After` hook so
   * that it runs even when a step throws -- a failed scenario is exactly the
   * one that leaves records behind, and cleanup that only happens on success
   * tidies up after the runs that did not need tidying.
   */
  world: async ({}, use) => {
    const world = new World()
    await use(world)
    await world.cleanUp()
  },

  /**
   * 🔴 THE FIRST WELCOME LETTER IS PAID FOR HERE, ONCE PER WORKER -- register row A76.
   *
   * The first letter the server builds after it starts takes 18-25 s (measured in p17,
   * 2026-09-21, in the server's own image), and every letter after it is quick. Since client
   * 8c6b675 a capture IS a letter (A64), so whichever capture scenario happened to run first
   * inherited that cost, and three of them went red on it -- a verdict decided by scheduling,
   * which is flake by this suite's definition. So each journeys worker asks for the letter's
   * PREVIEW before its first scenario: the same PDF is built, and nothing is sent.
   *
   * ⚠️ IT DOES NOT HIDE THE DEFECT. A76 keeps its own scenario, in the serial features, where
   * the first capture after an API restart is asserted as the application should behave.
   *
   * ⚠️ IT NEVER FAILS A WORKER. A stack without mail answers 503 here (and cannot capture at
   * all -- A64); a stack that is down says so in every scenario's own words. Either way this
   * warns and steps aside, rather than turning one cause into a hundred failures.
   */
  firstLetterPaid: [
    async ({ playwright }, use, workerInfo) => {
      if (workerInfo.project.name === 'journeys') {
        const request = await playwright.request.newContext({ baseURL: API_URL })
        try {
          const members = await (await request.get('/api/members', { timeout: 60_000 })).json()
          const first = [...members].sort((a, b) => a.id - b.id)[0]
          const preview = await request.get(`/api/members/${first.id}/welcome-letter-preview`, { timeout: 120_000 })
          if (!preview.ok()) {
            console.warn(`A76 warm-up: the letter preview answered ${preview.status()}; the first capture pays the cold letter itself`)
          }
        } catch (error) {
          console.warn(`A76 warm-up did not complete (${error.message}); the first capture pays the cold letter itself`)
        } finally {
          await request.dispose()
        }
      }
      await use()
    },
    { scope: 'worker', auto: true },
  ],
})

export const { Given, When, Then, Step } = createBdd(test)
