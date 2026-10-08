import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { AuthApi, expectPermissionRequired } from '../api/AuthApi.js'
import { UsersApi } from '../api/UsersApi.js'
import { Account } from '../pages/Account.js'
import { HomePage } from '../pages/HomePage.js'
import { SignInPage } from '../pages/SignInPage.js'
import { UsersPage } from '../pages/UsersPage.js'
import { sessionInTheBrowser, startSignedIn } from '../support/browserSession.js'
import { DEMO_USERS, demoPassword, demoUserFor } from '../support/demoUsers.js'
import { mintTestName } from '../support/guards.js'

/**
 * Signing in (the October build's sign-in), as the demonstration users the stack seeds -- one per position, named in
 * a scenario by the position -- and as a user an administrator creates. One-liners that delegate, bar the bookkeeping
 * of who is signed in.
 *
 * Two ways in, kept apart on purpose: "signs in on the sign-in screen" types into the form, because these journeys are
 * about the form; "is signed in" hands the browser a session made through the API (`startSignedIn`), for a journey
 * about what a position may do once in.
 */

/** "the Organiser", "the Chief Financial Officer" -- every position a demonstration user holds, as a step reads it. */
const POSITION = `(${Object.keys(DEMO_USERS).join('|')})`

/** The demonstration user of `position`, signed in through the API: the session the client keeps. */
const sessionFor = async (api, world, position) => {
  world.session = await new AuthApi(api.request).signInAs(position)
  world.position = position
  return world.session
}

Given(new RegExp(`^the ${POSITION} is signed in$`), async ({ page, api, world }, position) => {
  await startSignedIn(page, await sessionFor(api, world, position))
})

When('someone opens SASBO Online', async ({ page, world }) => {
  world.signIn = new SignInPage(page)
  await page.goto(`${world.signIn.baseURL}/`)
})

Then('they are asked to sign in, and offered to continue without signing in', async ({ world }) => {
  await world.signIn.expectLoaded()
  await world.signIn.expectOpenDoor()
})

When('they continue without signing in', async ({ world }) => world.signIn.continueWithoutSigningIn().click())

Then('the first screen is shown, with a way to sign in', async ({ page }) => {
  await new HomePage(page).expectLoaded()
  await expect(new Account(page).signInLink()).toBeVisible()
})

When(new RegExp(`^the ${POSITION} signs in on the sign-in screen$`), async ({ page, world }, position) => {
  world.position = position
  world.signIn = await new SignInPage(page).open()
  await world.signIn.signIn(demoUserFor(position), demoPassword())
})

Given(new RegExp(`^the ${POSITION} has signed in on the sign-in screen$`), async ({ page, world }, position) => {
  world.position = position
  world.signIn = await new SignInPage(page).open()
  await world.signIn.signIn(demoUserFor(position), demoPassword())
})

Then('the first screen is shown', async ({ page }) => new HomePage(page).expectLoaded())

Then('the header shows who is signed in, by name and position', async ({ page, api }) => {
  // Who the server says the session is -- read with the token the browser now holds, never assumed.
  const session = await sessionInTheBrowser(page)
  expect(session, 'the browser keeps no session after signing in').toBeTruthy()
  const { status, body } = await new AuthApi(api.request, { token: session.token }).me()
  expect(status, 'the session the browser holds, asked of the server').toBe(200)
  await new Account(page).expectSignedIn({ displayName: body.user.displayName, position: body.user.position.name })
})

When('they sign out', async ({ page, world }) => {
  world.signedOutOf = (await sessionInTheBrowser(page))?.token
  expect(world.signedOutOf, 'the browser kept no session to sign out of').toBeTruthy()
  await new Account(page).signOut()
})

Then('the sign-in screen is shown', async ({ page }) => new SignInPage(page).expectLoaded())

Then('the session they signed out of is refused by the server', async ({ api, world }) => {
  const { status, body } = await new AuthApi(api.request, { token: world.signedOutOf }).me()
  expect(status, 'a session that was signed out, asked of the server').toBe(401)
  expect(body?.code, 'the refusal for a token that opens no session any more').toBe('SESSION_EXPIRED')
})

When(new RegExp(`^they create a user who is an? ${POSITION} in area ([A-Z])$`), async ({ page, api, world }, position, area) => {
  const name = mintTestName('user')
  const admin = new UsersApi(api.request, { token: world.session.token })
  // No delete exists for a user: the one this scenario makes is closed when it ends, by the administrator who made it.
  world.registerCleanup(name, 'user (closed: there is no delete)', () => admin.close(name))
  world.users = await new UsersPage(page).open()
  world.created = { name, position, area }
  world.created.temporaryPassword = await world.users.createUser({ displayName: name, username: name, position, areas: [area] })
})

Then(/^the new user is listed as an? (.+) who sees area ([A-Z]), waiting on a temporary password$/, async ({ world }, position, area) => {
  const row = await world.users.rowFor(world.created.name)
  await expect(row).toContainText(position)
  await expect(row).toContainText(`Area: ${area}`)
  await expect(row).toContainText('Temporary password')
})

Then('the new user can sign in with the temporary password, and must choose their own', async ({ api, world }) => {
  const { status, body } = await new AuthApi(api.request).login(world.created.name, world.created.temporaryPassword)
  expect(status, 'the new user signing in with the password the dialog showed').toBe(200)
  expect(body.user.mustChangePassword, 'a temporary password must be changed at the first sign-in').toBe(true)
  expect(body.user.position.name).toBe(world.created.position)
  expect(body.user.scope).toEqual({ type: 'AREA', codes: [world.created.area] })
})

When('they open the users', async ({ page, world }) => {
  world.users = await new UsersPage(page).open()
})

Then('they are told that managing users needs a permission they do not hold', async ({ world }) =>
  world.users.expectRefusedToSignedInUser())

Then('the server refuses them the users, naming the permission', async ({ api, world }) =>
  expectPermissionRequired(await new UsersApi(api.request, { token: world.session.token }).attemptList(), 'USERS_ADMIN',
    `the users, asked for by the ${world.position}`))
