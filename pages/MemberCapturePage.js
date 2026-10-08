import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * The capture form at `/members/capture` -- a row of tabs, one Save button.
 *
 * WHY THIS SCREEN GETS THE MOST DETAILED PAGE OBJECT IN THE SUITE
 * The engineering standards name *"a field that reads but does not write"* as
 * the most expensive defect class in a data-capture system, and this is the
 * only screen in the application where a user creates a record from nothing.
 * Everything else edits something that already exists, where a dropped field is
 * at least visible as "my change did not stick".
 *
 * THE FIELD CATALOGUE BELOW IS THE POINT OF THE FILE. A journey that fills a
 * form by remembering which controls to touch will quietly stop covering the
 * field somebody adds next month. The catalogue is data, the feature file names
 * the fields in the user's words, and anything not in the catalogue cannot be
 * filled -- so a new field is a one-line change here and a visible row there.
 */

/**
 * Tabs the client has REMOVED, whose fields are therefore unreachable.
 *
 * 🔴 `'History & Remarks'` WENT FIRST -- its tab button commented out, then its
 * panel deleted (client abbbab6, 2026-09-10) -- and the fields below that sat on
 * it cannot be driven at all. `openTab` refuses on them by name rather than
 * timing out after thirty seconds on a button that is not there.
 *
 * ⚠️ NO COUNT HERE. `tests/harness/captureCatalogue.spec.js` asserts the affected
 * labels as a closed list, so that assertion IS the count and it is checked; a
 * number in this comment would be a second one that is not.
 *
 * 🔑 THE HISTORY & REMARKS FIELDS STAY IN THE CATALOGUE, and that is the whole
 * decision. Deleting them would delete the evidence, and the evidence is what
 * makes the two findings below legible. They divide cleanly, and the division
 * is the point:
 *
 *   SIX have `dto: null` -- Suspension Code, MEMNET Member, Print Label for
 *   Postage, Deduct Subscriptions through ACB, Print Membership Cards, Home Loan
 *   Reduced Rate. Those were register row A31, whose turns-green condition is
 *   "the six fields persist, OR the form stops collecting them". ✅ The second
 *   has happened: client abbbab6 deleted the panel, not just the tab button, so
 *   A31 CLOSED on 2026-09-14 and its scenario was retired. ⚠️ The form still
 *   POSTS all six as fixed defaults the server ignores -- nobody can enter them,
 *   so nothing a user typed is lost, but a branch that still renders the panel
 *   would reopen A31 on merge.
 *
 *   THREE have real columns -- `remarks`, `remarks2`, `useAltAddress`, all
 *   present on both the entity and the DTO. 🔴 That is a REGRESSION rather than
 *   a closure: data the member record can hold, that the form no longer offers
 *   any way to capture. Nobody chose it as far as this suite can tell; it fell
 *   out of removing a tab for the six that could not be stored.
 *
 * 🔑 `'Sasbo Structures'` WENT TOO (client 4a7a67b, 2026-09-10), taking the
 * three COSATU fields off capture. The stakeholder placed COSATU on Edit Member
 * on 2026-09-09, and ✅ ON THE DESIGN CLIENT THAT SCREEN CAN SET ALL THREE: once
 * "COSATU Member?" is yes, the record panel's edit mode renders free-text editors
 * for `cosatuLocal` ("COSATU Local") and `cosatuRegion` ("Region within Local"),
 * and answering no clears both along with `cosatuPositions` (client `Members.jsx`
 * at main 75859ed, re-read 2026-09-16). This note used to say those editors were
 * commented out -- true on the pre-design client, stale since the design merge
 * (90471d7). ⏳ Still open with the stakeholder, and unchanged: COSATU Locals and
 * Regions are COSATU's own structures, not SASBO's Area and Region, and whether
 * they become a seeded list or stay free text is theirs to answer.
 *
 * ⚠️ THIS SUITE CANNOT VERIFY THE TAB IS ACTUALLY GONE, and pretending otherwise
 * was already tried and withdrawn. Reading the client's `MEMBER_TABS` from here
 * needs a path into a sibling checkout, which `tests/harness/invariants.spec.js`
 * forbids. So this is a RECORDED FACT with its evidence, and the executable
 * parity assertion belongs in the client, beside the constant it is about.
 */
export const TABS_REMOVED_FROM_THE_CLIENT = ['History & Remarks', 'Sasbo Structures']

/** One predicate, three callers. */
const isOnRemovedTab = (tab) => TABS_REMOVED_FROM_THE_CLIENT.includes(tab)

/**
 * The client's own deadline for a write -- `WRITE_CEILING_MS` in its `services/parseApiError.js`
 * (20 000 at client main e70b675) -- recorded here as a fact, because this suite reaches into no
 * sibling checkout.
 *
 * 🔑 WHY THE SEND IS WAITED ON FOR THIS LONG AND NO LONGER. The welcome-letter dialog cannot
 * stay "Sending…" past it: the client aborts the request and the dialog shows its own error. So
 * the page waits for the DIALOG's outcome, bounded by the application's ceiling plus a margin
 * for a loaded renderer -- a number the application chose, not one this suite picked. A send that
 * outlives it is reported in the dialog's own words, which is exactly A76's shape on a cold server.
 */
const CLIENT_WRITE_CEILING_MS = 20_000
const RENDER_MARGIN_MS = 10_000

/**
 * Every field this suite can drive, keyed by the label the screen shows.
 *
 *   tab      which tab has to be open first
 *   control  how it is filled -- the three controls behave differently enough
 *            that a single `fill` works on exactly one of them
 *   dto      the property the API answers with, or `null` for a field the
 *            member record has NO COLUMN FOR
 *
 * 🔴 THE `null` ENTRIES ARE NOT GAPS IN THIS FILE. They are the fields register
 * row A31 was about -- once collected and dropped, now on a removed tab and posted
 * only as fixed defaults. They are kept, not omitted, as that record, and so the
 * closed list in tests/harness/captureCatalogue.spec.js can see them.
 */
export const CAPTURE_FIELDS = {
  'Title': { tab: 'Personal Information', control: 'select', dto: 'title' },
  'First Name(s)': { tab: 'Personal Information', control: 'text', dto: 'firstNames' },
  'Initials': { tab: 'Personal Information', control: 'text', dto: 'initials' },
  'Surname': { tab: 'Personal Information', control: 'text', dto: 'surname' },
  'Identity Type': { tab: 'Personal Information', control: 'select', dto: 'identityType' },
  'I.D. Number': { tab: 'Personal Information', control: 'text', dto: 'identityNumber' },
  'Race': { tab: 'Personal Information', control: 'select', dto: 'race' },
  'Personal Email': { tab: 'Personal Information', control: 'text', dto: 'personalEmail' },
  'Work Email': { tab: 'Personal Information', control: 'text', dto: 'workEmail' },
  'Cellphone Number': { tab: 'Personal Information', control: 'text', dto: 'phone' },
  'Work Tel': { tab: 'Personal Information', control: 'text', dto: 'workTel' },
  'Home Tel': { tab: 'Personal Information', control: 'text', dto: 'homeTel' },
  'Residential Address': { tab: 'Personal Information', control: 'text', dto: 'address' },
  'Employee Number': { tab: 'Employment & Bank', control: 'text', dto: 'employeeNumber' },
  'Employment Type': { tab: 'Employment & Bank', control: 'select', dto: 'employmentType' },
  'Bank Where Employed': { tab: 'Employment & Bank', control: 'searchable', dto: 'bankName' },
  'Bank Branch': { tab: 'Employment & Bank', control: 'searchable', dto: 'branch' },
  'Payment Type': { tab: 'Employment & Bank', control: 'select', dto: 'paymentType' },
  'Bank Account Number': { tab: 'Employment & Bank', control: 'text', dto: 'bankAccountNumber' },
  // ACB 00.5: the type the bureau file prints on every debit; required on capture for a debit order.
  'Account Type': { tab: 'Employment & Bank', control: 'select', dto: 'accountType' },
  'Debit Day': { tab: 'Employment & Bank', control: 'select', dto: 'debitDay' },
  'IBT No.': { tab: 'Employment & Bank', control: 'text', dto: 'ibtNo' },

  // ─── COSATU (V19), on the removed 'Sasbo Structures' tab since client 4a7a67b ───
  // Kept so the closed list in tests/harness/captureCatalogue.spec.js records that
  // they left the labelling check. The defect they once evidenced (provisional A39) was retired.
  'COSATU Member?': { tab: 'Sasbo Structures', control: 'select', dto: 'isCosatu' },
  'Local?': { tab: 'Sasbo Structures', control: 'select', dto: 'cosatuLocal' },
  'Regional?': { tab: 'Sasbo Structures', control: 'select', dto: 'cosatuRegion' },
  'Remarks / Comments': { tab: 'History & Remarks', control: 'text', dto: 'remarks' },
  '2nd Remarks Field': { tab: 'History & Remarks', control: 'text', dto: 'remarks2' },
  'Use Alternate Address (Y/N)': { tab: 'History & Remarks', control: 'select', dto: 'useAltAddress' },

  // `COSATU Positions` (a checkbox row) was never catalogued; it left capture with the same tab.

  // No column on `Member`. Once collected and dropped (A31, closed); now posted only as fixed defaults.
  'Suspension Code': { tab: 'History & Remarks', control: 'select', dto: null },
  'MEMNET Member (Y/N)': { tab: 'History & Remarks', control: 'select', dto: null },
  'Print Label for Postage (Y/N)': { tab: 'History & Remarks', control: 'select', dto: null },
  'Deduct Subscriptions through ACB (Y/N)': { tab: 'History & Remarks', control: 'select', dto: null },
  'Print Membership Cards (Y/N)': { tab: 'History & Remarks', control: 'select', dto: null },
  'Home Loan Reduced Rate (Y/N)': { tab: 'History & Remarks', control: 'select', dto: null },
}

/**
 * The catalogue's fields whose tab the client no longer offers -- the fields
 * `unlabelledFields()` skips. A plain function, not a method, so the harness can
 * closed-list it without a browser.
 */
export const fieldsOnRemovedTabs = () =>
  Object.entries(CAPTURE_FIELDS)
    .filter(([, field]) => isOnRemovedTab(field.tab))
    .map(([label]) => label)

export class MemberCapturePage extends BasePage {
  constructor(page) {
    super(page, '/members/capture')
  }

  async expectLoaded() {
    await expect(this.page.getByRole('heading', { name: 'Capture Member' })).toBeVisible()
  }

  /**
   * Switch tabs.
   *
   * 🔴 A TAB'S NAME CHANGES WHEN A FIELD ON IT IS INVALID. The heading renders
   * an asterisk beside the label as soon as that tab holds a validation error,
   * so `{ name: 'Personal Information', exact: true }` matches until the moment
   * a field goes red and then stops -- and the failure is a click that times
   * out on a button plainly visible in the screenshot, in the middle of a form
   * being filled. Matching the optional marker is what makes the page object
   * survive its own error paths.
   */
  async openTab(name) {
    // 🔴 REFUSE BY NAME RATHER THAN TIME OUT. A tab the client has removed has no
    // button, so this click waited the full action timeout and then reported that
    // a button was not visible -- thirty seconds to say "that tab is gone", in
    // the middle of a form being filled, with nothing naming the cause.
    if (isOnRemovedTab(name)) {
      throw new Error(
        `The '${name}' tab has been removed from the client, so its fields cannot be ` +
          `driven. This is not a gap in the page object -- see ` +
          `TABS_REMOVED_FROM_THE_CLIENT for which fields are affected and which of ` +
          `them the member record can still store. If the tab is back, delete it from ` +
          `that list deliberately.`,
      )
    }
    await this.page.getByRole('button', { name: labelPattern(name) }).click()
    return this
  }

  /**
   * A control addressed by the label above it.
   *
   * ⚠️ A required field's accessible name carries the asterisk the screen
   * shows, because the marker sits inside the `<label>`. Matching an optional
   * trailing ` *` lets the feature files name fields the way a user would.
   */
  control(label) {
    return CAPTURE_FIELDS[label]?.control === 'searchable'
      ? this.searchable(label)
      : this.page.getByLabel(labelPattern(label))
  }

  /**
   * 🔴 THE ONLY LOCATOR IN THIS SUITE THAT CANNOT USE AN ACCESSIBLE NAME, and
   * that is a finding about the screen rather than a shortcut taken here.
   *
   * Every "searchable" field on this form -- Bank Where Employed, Bank Branch,
   * both recruiter pickers, and the two BEC selectors -- renders its label with
   * `htmlFor` pointing at an id that does not exist: the control underneath is
   * a bare `<input>` inside a hand-rolled component that sets neither `id` nor
   * `aria-label`. The label is therefore attached to nothing. A screen reader
   * announces an unlabelled text box, and `getByLabel` finds no element at all
   * -- which is how this surfaced: as a 30-second timeout waiting for a field
   * plainly visible in the screenshot.
   *
   * The suite's own rule is that reaching past an accessible name is a finding
   * to file, not a technique to normalise, so this is deliberately the only
   * place it happens and it is registered rather than absorbed.
   *
   * The field is reached through its label's own container instead -- the same
   * shape the read-only bank/branch modal needs, for the same underlying reason.
   */
  searchable(label) {
    return this.page
      .locator('div')
      .filter({ has: this.page.getByText(labelPattern(label)) })
      .last()
      .locator('input')
  }

  /**
   * The fields whose label is attached to no control at all.
   *
   * Completeness by discovery over the whole catalogue, carrying no number of
   * its own: it grows with the form, so a field added next month is covered
   * without anyone remembering to add it here. It is the assertion behind A35,
   * and it lives on the page object rather than in the step because a step that
   * loops is a step that has stopped delegating.
   *
   * `getByLabel` deliberately, not `control` -- `control` routes the searchable
   * fields around the missing label, which is exactly what is being asserted
   * about.
   */
  async unlabelledFields() {
    const found = []
    for (const [label, field] of Object.entries(CAPTURE_FIELDS)) {
      // Fields on a removed tab are skipped rather than walked. They cannot be
      // reached, so "is this field labelled" has no answer for them -- and
      // `openTab` would now throw, turning A35's assertion about LABELS into a
      // failure about a missing tab. `fieldsOnRemovedTabs` is where they are
      // accounted for.
      if (isOnRemovedTab(field.tab)) continue

      await this.openTab(field.tab)
      if ((await this.page.getByLabel(labelPattern(label)).count()) === 0) found.push(label)
    }
    return found
  }

  /**
   * Fill one field, by the only method that works for its control.
   *
   * 🔴 THE THREE CONTROLS ARE NOT INTERCHANGEABLE, and getting it wrong fails
   * in three different unhelpful ways. `fill` on a `<select>` throws; `select`
   * on a text input throws; and the "searchable" control is neither -- it is a
   * text box whose typing filters a `<ul>` that has to be CLICKED, so `fill`
   * appears to work, leaves the underlying value unset, and the field arrives
   * at the server empty.
   */
  async fillField(label, value) {
    const field = CAPTURE_FIELDS[label]
    if (!field) {
      throw new Error(
        `The capture form has no field called ${JSON.stringify(label)} in this suite's ` +
          `catalogue. Add it to CAPTURE_FIELDS with its tab, control type and the ` +
          `property the API answers with -- or null if the record has no column for it.`,
      )
    }

    await this.openTab(field.tab)

    if (field.control === 'select') {
      await this.control(label).selectOption(String(value))
    } else if (field.control === 'searchable') {
      await this.control(label).fill(String(value))
      await this.page.getByRole('option', { name: String(value), exact: true }).click()
      await expect(this.control(label)).toHaveValue(String(value))
    } else {
      await this.control(label).fill(String(value))
    }
    return this
  }

  /**
   * Save the capture and require it to succeed.
   *
   * 🔴 A REFUSAL IS AN IN-PAGE NOTICE NOW, NOT AN `alert()`. The design client lists what is
   * wrong under "Fix these before saving" (or "This member was not saved" for a server
   * refusal), beside the fields that caused it. So this waits for success OR that notice, and
   * a refusal is thrown with its own words rather than left to time out.
   *
   * 🔴 THE SUCCESS NOTICE IS RECORDED BY THE PAGE, because it does not stay. `CaptureMember`
   * hides it 3 000 ms after it appears (`setShowSavedNotification(false)`), and on a stalled
   * host a poll can miss a window that short entirely -- measured 2026-09-18, a run on a
   * starved box timed out waiting for a notice the save had almost certainly shown. This is
   * the register's save-confirmation flake (fixed 2026-09-17) one screen over, so it gets the
   * same fix: a MutationObserver armed BEFORE the click stores the text in the page the
   * instant it renders, and the wait reads what was stored. The refusal list is persistent,
   * so it is still read from the DOM.
   *
   * 🔴 SINCE CLIENT 8c6b675, "SAVE MEMBER" DOES NOT SAVE -- register row A64. It opens the
   * welcome-letter dialog, and the member is created only by that dialog's "Send Welcome
   * Letter": one request that saves AND emails (`POST /api/members?sendWelcome=true`). So
   * there are two flows, and both are driven:
   *
   *   before 8c6b675   Save saves and the toast appears; the dialog opens AFTERWARDS to offer a
   *                    send, which this method never makes -- the scenario asked for no letter.
   *   since 8c6b675    Save opens the dialog; Send saves and emails; then the toast appears.
   *
   * Both, because this suite gates branches cut on either side of that commit (ACB's client
   * branch predates it), and the toast is the one signal the two flows share. In the old flow
   * the toast and the dialog arrive in ONE render, so `saved` is read first and wins.
   *
   * ⚠️ A member with no email address cannot be saved in the new flow -- Send is disabled --
   * and that is thrown naming A64, rather than left to time out beside a disabled button.
   */
  async save() {
    await this.page.evaluate(() => {
      window.__sasboCaptureSaved = false
      const observer = new MutationObserver(() => {
        const saved = [...document.querySelectorAll('span')].some(
          (el) => el.textContent.trim() === 'Member captured successfully!',
        )
        if (!saved) return
        window.__sasboCaptureSaved = true
        observer.disconnect()
      })
      observer.observe(document.body, { subtree: true, childList: true, characterData: true })
    })

    await this.page.getByRole('button', { name: 'Save Member' }).click()
    const refusal = this.refusalTitle()
    const letter = this.welcomeLetter()
    const outcome = async () =>
      (await this.savedSeen())
        ? 'saved'
        : (await refusal.isVisible())
          ? 'refused'
          : (await letter.isVisible())
            ? 'letter'
            : null
    await expect.poll(outcome, { message: 'Save Member was neither confirmed, refused, nor answered with the welcome letter' }).not.toBeNull()

    const reached = await outcome()
    if (reached === 'refused') {
      throw new Error(`The capture was refused: ${(await this.refusalMessages()).join('; ')}`)
    }
    if (reached === 'letter') await this.sendWelcomeLetterToSave(letter)
    return this
  }

  /** The dialog `CaptureMember` opens over the form -- the save itself, since client 8c6b675. */
  welcomeLetter() {
    return this.page.getByRole('dialog', { name: 'Welcome Letter Preview' })
  }

  /** Whether the success toast has rendered since `save` armed its observer. */
  async savedSeen() {
    return this.page.evaluate(() => window.__sasboCaptureSaved)
  }

  /**
   * The second half of a save in the new flow: Send, then success or the dialog's own error.
   *
   * The dialog is then CLOSED, as a clerk would close it -- it stays open on "Welcome Letter
   * Sent" otherwise, and the old flow never left a dialog open that the scenario had driven.
   */
  async sendWelcomeLetterToSave(letter) {
    const send = letter.getByRole('button', { name: 'Send Welcome Letter' })
    if (await send.isDisabled()) {
      const why = letter.getByText(/has no email address/)
      throw new Error(
        'The capture cannot be completed: since client 8c6b675 a member is saved only by sending ' +
          'the welcome letter, and its Send button is disabled' +
          ((await why.isVisible()) ? ` -- the dialog says "${(await why.textContent()).trim()}"` : '') +
          '. This is register row A64.',
      )
    }

    await send.click()
    const failed = letter.locator('[data-send-error]')
    const sent = async () => ((await this.savedSeen()) ? 'saved' : (await failed.isVisible()) ? 'failed' : null)
    await expect
      .poll(sent, {
        message: 'the welcome letter was neither sent nor refused within the client’s own write deadline',
        timeout: CLIENT_WRITE_CEILING_MS + RENDER_MARGIN_MS,
      })
      .not.toBeNull()
    if ((await sent()) === 'failed') {
      throw new Error(`The capture was not completed -- the welcome letter dialog said: ${(await failed.textContent()).trim()}`)
    }

    // TWO buttons are named "Close": the header's icon (`aria-label`, no text, in `Modal`) and
    // the footer's own. The one whose TEXT says Close is the footer's, the one a clerk reads.
    await letter.getByRole('button', { name: 'Close', exact: true }).filter({ hasText: 'Close' }).click()
    await expect(letter).toBeHidden()
  }

  /**
   * Save, expecting the form to refuse, and return what it said.
   *
   * The design client's refusal is a titled list inside the form: each line is one problem,
   * and the lines are returned joined so a step can check for one of them.
   */
  async saveExpectingRefusal() {
    await this.page.getByRole('button', { name: 'Save Member' }).click()
    await expect(this.refusalTitle()).toBeVisible()
    return (await this.refusalMessages()).join('\n')
  }

  refusalTitle() {
    return this.page.getByText(/^(Fix these before saving|This member was not saved)$/)
  }

  async refusalMessages() {
    return this.refusalTitle().locator('xpath=following-sibling::ul[1]').getByRole('listitem').allTextContents()
  }
}
