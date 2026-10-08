import { expect } from '@playwright/test'

/**
 * Who is signed in, as every page's header says it (client `oct/signin`): the topbar -- the `banner` landmark -- shows
 * a signed-in user's name and position (two lines from 1280 px; an initials chip with both in screen-reader text below
 * it), a link "Change your password" and a button "Sign out"; nobody signed in sees a link "Sign in".
 *
 * 🔑 SCOPED TO THE BANNER. The rail's foot carries a "Sign out" and a "Sign in" of its own, so a page-wide locator
 * would find two.
 */
export class Account {
  /** @param {import('@playwright/test').Page} page */
  constructor(page) {
    this.page = page
  }

  banner() {
    return this.page.getByRole('banner')
  }

  /** The header names the person and their position -- in text, whichever of its two layouts the width chose. */
  async expectSignedIn({ displayName, position }) {
    await expect(this.banner()).toContainText(displayName)
    await expect(this.banner()).toContainText(position)
    await expect(this.signOutButton()).toBeVisible()
  }

  signOutButton() {
    return this.banner().getByRole('button', { name: 'Sign out', exact: true })
  }

  signInLink() {
    return this.banner().getByRole('link', { name: 'Sign in', exact: true })
  }

  async signOut() {
    await this.signOutButton().click()
  }
}
