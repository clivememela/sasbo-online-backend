import { expect } from '@playwright/test'
import { BasePage } from './BasePage.js'

/**
 * The sign-in screen (`/login`), made real in the October build (client `oct/signin`).
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT: the heading "Sign in"; the fields "Username" and "Password" (each named
 * with a screen-reader "(required)" -- and the password has a "Show password" toggle beside it, which is why both are
 * found by the START of their names); the button "Sign in", "Signing in…" while it works. While sign-in is open --
 * this stack's mode -- it also says "No account yet? Sign-in is not required while SASBO’s accounts are being set up."
 * and offers a button, "Continue without signing in", which goes on to `next` or the first screen.
 *
 * The root address still sends everybody here; an address under `/login?next=` brings them back after.
 */
export class SignInPage extends BasePage {
  constructor(page) {
    super(page, '/login')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible()
  }

  username() {
    return this.page.getByLabel(/^Username/)
  }

  password() {
    return this.page.getByLabel(/^Password/)
  }

  continueWithoutSigningIn() {
    return this.page.getByRole('button', { name: 'Continue without signing in', exact: true })
  }

  /** The open door, as this mode offers it: the sentence that says why, and the button. */
  async expectOpenDoor() {
    await expect(this.page.getByText('No account yet? Sign-in is not required while SASBO’s accounts are being set up.')).toBeVisible()
    await expect(this.continueWithoutSigningIn()).toBeVisible()
  }

  /**
   * Sign in with the form, as a person does, and wait until the screen has let go of it: the sign-in screen gives way
   * to wherever it was sending them. The password is passed in, never written here.
   */
  async signIn(username, password) {
    await this.username().fill(username)
    await this.password().fill(password)
    await this.page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(this.page).not.toHaveURL((url) => url.pathname === '/login')
  }
}
