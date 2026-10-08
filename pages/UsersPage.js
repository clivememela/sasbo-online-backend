import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * Maintenance → Users (`/admin/maintenance/users`, client `oct/signin`) -- "an administrator creating users with a
 * position and an area" (the 5 Oct Show & Tell).
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT: for a user holding USERS_ADMIN, a button "Create a user", a search
 * "Name or username", and the table "The users of SASBO Online" -- Name (the row's button, "Change <name> (<username>)"),
 * Position, Role, Sees ("All of SASBO", "Area: L"), Last signed in, Standing ("Temporary password", "Active", "Closed").
 * For anyone else, only the permission notice: nothing below the heading is mounted, so nothing is asked of the server.
 *
 * The dialog "Create a user": Name, Username, E-mail, Position, Role, the radio group "Sees" ("All of SASBO", "Only some
 * regions", "Only some areas") and, for areas, a box per area ("Area L · …"); "Create user". It then turns into
 * "Temporary password for <name>", showing the password once, and "Done".
 */
export class UsersPage extends BasePage {
  constructor(page) {
    super(page, '/admin/maintenance/users')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Users' })).toBeVisible()
  }

  dialog(name) {
    return this.page.getByRole('dialog', { name })
  }

  /**
   * Create a user through the dialog, with a position and the areas they see, and read the temporary password the
   * dialog shows once. Resolves with that password, the dialog closed.
   */
  async createUser({ displayName, username, position, areas }) {
    await this.page.getByRole('button', { name: 'Create a user', exact: true }).click()
    const form = this.dialog('Create a user')
    await expect(form).toBeVisible()
    await form.getByLabel(labelPattern('Name')).fill(displayName)
    await form.getByLabel(labelPattern('Username')).fill(username)
    await form.getByLabel(labelPattern('Position')).selectOption({ label: position })
    await form.getByRole('radio', { name: 'Only some areas', exact: true }).check()
    for (const area of areas) {
      await form.getByRole('checkbox', { name: new RegExp(`^Area ${area}\\b`) }).check()
    }
    await form.getByRole('button', { name: 'Create user', exact: true }).click()

    const shown = this.dialog(`Temporary password for ${displayName}`)
    await expect(shown).toBeVisible()
    const password = (await shown.getByText('Temporary password', { exact: true }).locator('xpath=following-sibling::p[1]').innerText()).trim()
    expect(password.length, 'the dialog showed a temporary password').toBeGreaterThan(0)
    await shown.getByRole('button', { name: 'Done', exact: true }).click()
    await expect(shown).toBeHidden()
    return password
  }

  /** The users' row for one user, found by searching their username. */
  async rowFor(username) {
    await this.page.getByLabel(labelPattern('Name or username')).fill(username)
    const row = this.page.getByRole('table', { name: 'The users of SASBO Online' }).getByRole('row')
      .filter({ has: this.page.getByRole('button', { name: new RegExp(`\\(${username.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\)$`, 'i') }) })
    await expect(row).toHaveCount(1)
    return row
  }

  /** The permission notice, for a signed-in user without USERS_ADMIN -- and no way to create one. */
  async expectRefusedToSignedInUser() {
    await expect(this.page.getByRole('status').filter({ hasText: 'You need permission to manage users. Ask your administrator if you should have it.' })).toBeVisible()
    await expect(this.page.getByRole('button', { name: 'Create a user', exact: true })).toHaveCount(0)
  }
}
