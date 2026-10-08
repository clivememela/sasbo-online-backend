import { expect } from '@playwright/test'
import { MemberRegisterPage } from './MemberRegisterPage.js'
import { said } from '../support/journeyMonth.js'

/**
 * A member's record, for what month end added to it (`03.1`–`03.6`): the status, changed through *Change status* and
 * never edited; and the history of changes, newest first.
 *
 * WHAT IS ON IT, READ FROM THE BUILT CLIENT
 *   - the Membership tab ("Membership & Subscriptions") shows "Account Membership Status" as a chip, with *Change
 *     status* beside it -- and, while the record is being edited, no button and a note saying the way to change it;
 *   - *Change status* is a dialog of that name: "New status", then SASBO's deletion or suspension code for it, the
 *     reason the member gave where the code keeps any, and a note. Until a status and its code are chosen its button
 *     stays shut and says what is missing ("Choose SASBO's code for it.");
 *   - the History tab ("History of changes") is a section named "History of changes, newest first", its saves a list
 *     within it: a capture is one line, "Captured as M-…"; a change of status names "Account Membership Status", its
 *     From and To, and its reason as SASBO's code by number and name -- "Code 2 · Resigned Society".
 *
 * The record is reached through the register, as an administrator reaches it: by the surname minted for the member,
 * which no other member has.
 */
export class MemberRecordPage {
  /** @param {import('@playwright/test').Page} page */
  constructor(page) {
    this.page = page
    this.register = new MemberRegisterPage(page)
  }

  async openFor(member) {
    await this.register.open()
    await this.register.filterBy('Surname', member.surname)
    await expect(this.register.summary()).toHaveText(/Showing 1–1 of 1\b/)
    await this.register.row(member.surname).click()
    await expect(this.record()).toBeVisible()
    return this
  }

  record() {
    return this.register.record()
  }

  async openMembership() {
    await this.register.openRecordTab('Membership & Subscriptions')
  }

  status() {
    return this.register.detail('Account Membership Status')
  }

  async expectStatus(status) {
    await this.openMembership()
    await expect(this.status()).toContainText(status)
  }

  changeStatusButton() {
    return this.record().getByRole('button', { name: 'Change status', exact: true })
  }

  dialog() {
    return this.page.getByRole('dialog', { name: 'Change status' })
  }

  async openChangeStatus() {
    await this.openMembership()
    await this.changeStatusButton().click()
    await expect(this.dialog()).toBeVisible()
  }

  /** Choose the new status and, where its kind asks for one, SASBO's code for it -- by the code's number. */
  async choose(status, code) {
    await this.dialog().getByLabel(/^New status/).selectOption(status)
    if (code !== undefined) {
      await this.dialog().getByLabel(/^SASBO’s (deletion|suspension) code/).selectOption(String(code))
    }
  }

  submit() {
    return this.dialog().getByRole('button', { name: 'Change status', exact: true })
  }

  /** Change the status through the dialog, and wait for it to close: the change is made and the record read again. */
  async changeStatus(status, code) {
    await this.openChangeStatus()
    await this.choose(status, code)
    await this.submit().click()
    await expect(this.dialog()).toBeHidden()
  }

  /** Ask for the change and leave the dialog to say what came of it -- for a change the server is to refuse. */
  async attemptStatusChange(status, code) {
    await this.openChangeStatus()
    await this.choose(status, code)
    await this.submit().click()
  }

  /** Open Change status and choose only the new status -- no code. */
  async chooseWithoutCode(status) {
    await this.openChangeStatus()
    await this.choose(status)
  }

  /** The dialog asks for SASBO's code: it says so, and its button stays shut. */
  async expectAskedForCode() {
    await expect(this.dialog().getByText('Choose SASBO’s code for it.', { exact: true })).toBeVisible()
    await expect(this.submit()).toBeDisabled()
  }

  /** The dialog says what the server said, and stays open. */
  async expectTold(sentence) {
    await expect(this.dialog().getByText(sentence, { exact: true })).toBeVisible()
  }

  /** A change refused on a stale view: the status the member was moved to and when, as a person reads it -- and no more. */
  async expectToldMoved(status, changedAt) {
    await this.expectTold(`This member was moved to ${status} on ${said(changedAt)}. Nothing was changed.`)
  }

  async cancel() {
    await this.dialog().getByRole('button', { name: 'Cancel', exact: true }).click()
    await expect(this.dialog()).toBeHidden()
  }

  async openHistory() {
    await this.register.openRecordTab('History of changes')
    await expect(this.history()).toBeVisible()
  }

  history() {
    return this.record().getByRole('region', { name: 'History of changes, newest first' })
  }

  entries() {
    return this.history().getByRole('listitem')
  }

  /** The entry of a change of status into `to`, with SASBO's code `code` by its number and name. */
  statusChangeInto(to, code) {
    return this.entries()
      .filter({ hasText: 'Account Membership Status' })
      .filter({ hasText: new RegExp(`Code ${code} · \\S`) })
      .filter({ hasText: to })
  }

  async expectStatusChange(to, code) {
    await this.openHistory()
    await expect(this.statusChangeInto(to, code)).toHaveCount(1)
  }

  /** The record of a return: a change into `to` with no code, its reason "Reinstated". */
  async expectReinstatedInto(to) {
    await this.openHistory()
    await expect(this.entries().filter({ hasText: 'Account Membership Status' }).filter({ hasText: 'Reinstated' })
      .filter({ hasText: to })).toHaveCount(1)
  }

  async expectOnlyCaptured() {
    await this.openHistory()
    await expect(this.entries()).toHaveCount(1)
    await expect(this.entries().first()).toContainText(/^.*Captured( as \S+)?/)
  }

  async startEditing() {
    await this.register.startEditing()
  }

  /** While the record is edited, the status is shown with no way to edit it there, and a note says the way. */
  async expectStatusNotEditable() {
    await this.openMembership()
    await expect(this.status()).toBeVisible()
    await expect(this.changeStatusButton()).toHaveCount(0)
    await expect(this.status().locator('select, input, textarea')).toHaveCount(0)
    await expect(this.record().getByText('Changed with Change status, once this edit is saved or cancelled.', { exact: true })).toBeVisible()
  }
}
