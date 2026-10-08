import { CLIENT_URL } from './env.js'

/**
 * A browser that opens the application already signed in -- for the journeys that need a permission and are not
 * about signing in.
 *
 * WHY NOT THE SIGN-IN FORM EVERY TIME. The form is a journey of its own (`features/signin/`), and a payroll journey
 * that typed a password first would fail on the form's account rather than its own. So the session is made through
 * the API (`AuthApi.signInAs`) and handed to the client the way the sign-in contract documents for exactly this: the
 * client keeps `{token, expiresAt, user}` in `sessionStorage['sasbo.session']`, and reads it when it starts.
 *
 * 🔑 BEFORE ANY PAGE SCRIPT RUNS, AND ONCE A TAB. An init script puts the session in place before the client reads
 * it, on the application's own origin only; a marker beside it means a journey that signs out stays signed out --
 * without it, the next navigation would sign the browser straight back in.
 */
export const SESSION_KEY = 'sasbo.session'
const SEEDED = 'sasbo.suite.sessionSeeded'

/** Open every later page of `page` signed in with `session` -- `{ token, expiresAt, user }`, as the login answered. */
export async function startSignedIn(page, session) {
  const origin = new URL(CLIENT_URL).origin
  await page.addInitScript(
    ({ origin, key, seeded, value }) => {
      if (window.location.origin !== origin || window.sessionStorage.getItem(seeded) === '1') return
      window.sessionStorage.setItem(key, value)
      window.sessionStorage.setItem(seeded, '1')
    },
    { origin, key: SESSION_KEY, seeded: SEEDED, value: JSON.stringify(session) },
  )
}

/** The session the client holds in this tab now, or null -- what the sign-in form left behind. */
export async function sessionInTheBrowser(page) {
  const kept = await page.evaluate((key) => window.sessionStorage.getItem(key), SESSION_KEY)
  return kept ? JSON.parse(kept) : null
}
