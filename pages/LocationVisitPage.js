import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * The Location Visit form (`/reports/organizer-report`, client `oct/reports-visits`; the 5 Oct Show & Tell: "the visit
 * forms saving and submitting").
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT: for a user holding VISITS_CAPTURE, the form's actions are "Save draft" and
 * "Submit visit" -- and, once a draft is saved, "Discard draft", which asks first ("Discard the draft for <workplace>
 * (R/A <n>)? A discarded draft cannot be brought back.") and answers "Discard it" / "Keep the draft". Each outcome is
 * said in a notice beside the actions: "Draft saved for <workplace> (R/A <n>)." / "Draft discarded for <workplace>
 * (R/A <n>)." The workplace is found by the form's "R / A Number".
 *
 * 🔴 NO JOURNEY SUBMITS A VISIT. A submitted visit is SASBO's record, append-only, and writes the branch's staff
 * complement: this page object saves and discards, which leaves nothing behind.
 */
export class LocationVisitPage extends BasePage {
  constructor(page) {
    super(page, '/reports/organizer-report')
  }

  async expectLoaded() {
    await super.expectLoaded()
    await expect(this.page.getByRole('heading', { level: 1, name: 'Location Visit' })).toBeVisible()
  }

  form() {
    return this.page.getByRole('button', { name: 'Print form', exact: true }).locator('xpath=ancestor::form')
  }

  async fill(label, value) {
    await this.form().getByLabel(labelPattern(label)).fill(String(value))
  }

  /** Save the form as a draft, and answer the draft the server made of it -- `{ id, version, workplaceName, raNumber }`. */
  async saveDraft() {
    const made = this.page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/workplaces/visit-drafts')
    await this.page.getByRole('button', { name: 'Save draft', exact: true }).click()
    const response = await made
    expect(response.status(), 'the draft saved').toBe(201)
    return response.json()
  }

  /** Discard the draft the form holds, saying so when asked. */
  async discardDraft() {
    await this.page.getByRole('button', { name: 'Discard draft', exact: true }).click()
    await expect(this.page.getByText(/^Discard the draft for .+\? A discarded draft cannot be brought back\.$/)).toBeVisible()
    await this.page.getByRole('button', { name: 'Discard it', exact: true }).click()
  }

  async expectNotice(text) {
    await expect(this.page.getByText(text, { exact: true })).toBeVisible()
  }
}
