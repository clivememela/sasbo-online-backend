import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { MailApi } from '../api/MailApi.js'
import { MembersApi } from '../api/MembersApi.js'
import { MemberCapturePage, CAPTURE_FIELDS } from '../pages/MemberCapturePage.js'
import {
  MemberRegisterPage, SAVED_CONFIRMATION, isRegisterRead, isRegisterReadRequest, registerLoadOf,
} from '../pages/MemberRegisterPage.js'
import { mintEmailAddress, mintIdentityNumber, mintNumericTail, mintTestName } from '../support/guards.js'

/**
 * The member capture and member register journeys.
 *
 * Every step is a one-liner that delegates, as in `banks.steps.js`. Nothing
 * here knows a selector, a URL or an endpoint.
 *
 * THE ONE PLACE THIS FILE DOES MORE THAN DELEGATE is `captureFrom`, and it is
 * worth saying why: a Gherkin table is data, and turning it into an ordered
 * sequence of typed fields is translation rather than logic. It stays here so
 * that both capture steps -- the one that saves and the one that does not --
 * read the table identically. Two readers of one table is how they drift.
 */

/**
 * The placeholder a feature file writes where a value has to be unique per run.
 *
 * Scenarios run in parallel workers and repeat under the determinism gate, so a
 * literal surname would collide between workers and make one run's leftovers
 * another run's search results. Written out in the feature rather than hidden
 * in a step, because a reader of the scenario has to be able to see that the
 * name is not a constant.
 */
const MINTED = '<a name minted for this run>'

/**
 * The identity number placeholder, for scenarios that SAVE.
 *
 * A scenario that only reads a derivation keeps the fixed number in its table -- it is
 * asserting what 900101… means, and a minted one would make the expected date of birth
 * unreadable. A scenario that saves cannot: identity numbers are unique on the server
 * since V26 (A48), so two saves of the same number is a refusal, correctly.
 */
const MINTED_ID = '<an identity number minted for this run>'

/**
 * The email placeholder, for scenarios that read the member's welcome letter back.
 *
 * Since client 8c6b675 a capture SENDS the letter (A64), and every letter any scenario
 * sent sits in the same catcher -- so a scenario that asserts on its own letter addresses
 * it to a minted recipient. A scenario that only needs an address to be able to save keeps
 * a literal one, as it always has.
 */
const MINTED_EMAIL = '<an email address minted for this run>'

/** Each placeholder, and the mint that replaces it. */
const MINTS = {
  [MINTED]: () => mintTestName('member'),
  [MINTED_ID]: () => mintIdentityNumber(),
  [MINTED_EMAIL]: () => mintEmailAddress(),
}

async function captureFrom(page, world, table) {
  world.capture = new MemberCapturePage(page)
  await world.capture.open()

  world.captured = new Map()
  for (const { field, value } of table.hashes()) {
    const resolved = MINTS[value]?.() ?? value
    await world.capture.fillField(field, resolved)
    world.captured.set(field, resolved)
  }
  return world.capture
}

Given('the member register is available', async ({ api, world }) => {
  world.membersApi = new MembersApi(api.request)
  world.members = await world.membersApi.listMembers()
  expect(world.members.length, 'the register seeded no members').toBeGreaterThan(0)
})

When('an administrator begins capturing a member with:', async ({ page, world }, table) => {
  await captureFrom(page, world, table)
})

When('an administrator captures a member with:', async ({ page, world, api }, table) => {
  await captureFrom(page, world, table)
  await world.capture.save()

  // The record cannot be deleted -- there is no endpoint for it (D9) -- so the
  // scenario registers the strongest undo that exists rather than pretending.
  const members = await new MembersApi(api.request).listMembers()
  world.createdMember = members.find((m) => m.surname === world.captured.get('Surname'))
  if (world.createdMember) {
    world.registerRestore('captured member', async () => {
      await new MembersApi(api.request).neutralise(world.createdMember.id)
    })
  }
})

When('they try to save the member', async ({ page, world }) => {
  // Every request this attempt sends that could add a member is kept, so "no member
  // has been added" is judged on this attempt alone -- see that step for why.
  world.attemptedWrites = []
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/members') world.attemptedWrites.push(request)
  })
  world.refusal = await world.capture.saveExpectingRefusal()
})

When('an administrator opens the member register', async ({ page, world }) => {
  // What THIS load's own read of the members said: what "every member on record" is judged against (see that step).
  world.memberRegister = new MemberRegisterPage(page)
  world.registerReceived = await world.memberRegister.openAndReadTheLoad()
})

// ── The register a page at a time (register paging, A10): the view lives in the address -- page, order, search -- so a
// link or a refresh returns to it; each is asked by its address, and judged by the page the server answered it with.

When('an administrator opens the member register at its second page', async ({ page, world }) => {
  world.memberRegister = new MemberRegisterPage(page)
  world.registerReceived = await world.memberRegister.openAndReadTheLoad('?page=2')
})

When('an administrator opens the member register sorted by surname, Z to A', async ({ page, world }) => {
  world.memberRegister = new MemberRegisterPage(page)
  world.registerReceived = await world.memberRegister.openAndReadTheLoad('?sort=surname%2Cdesc')
})

When("an administrator searches the register for the first member's membership number", async ({ page, world }) => {
  world.member = world.members[0]
  world.memberRegister = new MemberRegisterPage(page)
  world.registerReceived = await world.memberRegister.openAndReadTheLoad(`?q=${encodeURIComponent(world.member.membershipNumber)}`)
})

/** The page the server answered this load, or a failure saying the register read every member instead. */
const pageAnswered = (world) => {
  expect(world.registerReceived?.page, 'the register read every member at once: it is not read a page at a time on this client').toBeTruthy()
  return world.registerReceived.page
}

Then("the register asks the server for its second page, and shows that page's members", async ({ world }) => {
  const answered = pageAnswered(world)
  expect(answered.page, 'the page the server answered, counted from nought').toBe(1)
  await world.memberRegister.expectRowsOf(answered.content)
  await expect(world.memberRegister.summary()).toContainText('Page 2 of')
})

Then('the register asks the server for that order, and shows the members in the order the server gave', async ({ world }) => {
  const answered = pageAnswered(world)
  expect(answered.sort, 'the order the server says the rows are in').toBe('surname,desc')
  await world.memberRegister.expectRowsOf(answered.content)
})

Then('the server finds that one member, and the register shows them', async ({ world }) => {
  const answered = pageAnswered(world)
  expect(answered.totalElements, `members the server found for ${world.member.membershipNumber}`).toBe(1)
  await world.memberRegister.expectReportedCount(1)
  await expect(world.memberRegister.row(world.member.surname).first()).toBeVisible()
})

When('an administrator navigates to the member register', async ({ page, world }) => {
  world.memberRegister = new MemberRegisterPage(page)
  await page.goto(`${world.memberRegister.baseURL}${world.memberRegister.path}`)
})

When('they search the register by surname for {string}', async ({ world }, surname) => {
  await world.memberRegister.filterBy('Surname', surname)
})

When("they search the register for the first member's employee number", async ({ world }) => {
  world.member = world.members[0]
  await world.memberRegister.filterBy('Employee Number', world.member.employeeNumber)
})

When("they search the register for the first member's membership number", async ({ world }) => {
  // A61: null on every seeded member today. Kept for the skipped scenario that asserts
  // the search works, so the row has a live witness the day the seeder is fixed.
  world.member = world.members[0]
  await world.memberRegister.filterBy('Membership Number', world.member.membershipNumber)
})

When('they open the first member on record', async ({ world }) => {
  world.member = world.members[0]
  await world.memberRegister.openMember(world.member.employeeNumber)
})

/**
 * The debit days the server will accept, and the reason this step has to know
 * them.
 *
 * The validator runs over the WHOLE member being saved rather than over what
 * changed, so a member whose stored debit day is outside this set cannot be
 * saved at all -- see A32 and the scenario that asserts it. Correcting a phone
 * number on such a member fails for a reason on another tab, so the working
 * journey picks a member the API will actually accept and the defect gets its
 * own named scenario instead of quietly steering this one.
 */
const DEBIT_DAYS_THE_SERVER_ACCEPTS = [20, 25, 31]

function correctableMembers(members) {
  return members.filter(
    (member) =>
      member.paymentType !== 'Debit Order' ||
      DEBIT_DAYS_THE_SERVER_ACCEPTS.includes(member.debitDay),
  )
}

When('they open the first member the register can save', async ({ world }) => {
  const correctable = correctableMembers(world.members)
  expect(correctable.length, 'no member on record can be saved at all').toBeGreaterThan(0)
  world.member = correctable[0]
  // By employee number, for A61's reason -- see openMember.
  await world.memberRegister.openMember(world.member.employeeNumber)
})

/**
 * The correction itself, made THROUGH THE SCREEN.
 *
 * The cleanup is registered BEFORE the edit rather than after it, because the
 * run that leaves the register modified is the one where the save half-worked
 * and the assertion failed. Registering afterwards means the restore only ever
 * happens on the runs that did not need it.
 */
When("they correct that member's cellphone number", async ({ world }) => {
  await beginCorrecting(world)
  await world.memberRegister.editField('Cellphone Number', world.newPhone)

  // 🔴 AND THE IDENTITY TYPE, WHICH THE ADMINISTRATOR DID NOT COME HERE TO
  // CHANGE. The edit form makes it required and no member in the register has
  // one, so the save is refused outright without it -- see A36 and the skipped
  // scenario that says this should not be necessary. It is done here rather
  // than hidden in the page object so that a reader of the journey can see the
  // application asked for it.
  await world.memberRegister.editSelect('Identity Type', 'I.D. Number')
  await world.memberRegister.saveChanges()
})

When("they correct only that member's cellphone number", async ({ world }) => {
  await beginCorrecting(world)
  await world.memberRegister.editField('Cellphone Number', world.newPhone)
  await world.memberRegister.saveChanges()
})

/**
 * Mint the new value and register the undo BEFORE anything is typed.
 *
 * The run that leaves the register modified is the one where the save landed
 * and the assertion then failed; registering the restore afterwards means it
 * only ever happens on the runs that did not need it. Both fields the scenario
 * touches are put back, not just the one it came to change.
 */
async function beginCorrecting(world) {
  const original = { ...world.member }
  world.newPhone = `+27 82 ${mintNumericTail(6)}`
  world.registerRestore('member details', () => world.membersApi.restore(original))
  await world.memberRegister.startEditing()
}

When('the cellphone number on the first member on record is changed', async ({ world }) => {
  world.member = world.members[0]
  world.newPhone = `+27 82 ${mintNumericTail(6)}`
  world.registerRestore('cellphone number', await world.membersApi.changeFieldReversibly(
    world.member.id, 'phone', world.newPhone,
  ))
})

Then('the register confirms the change was saved', async ({ world }) => {
  // What saveChanges SAW, not a second look at a notice that is gone 2.6 s later.
  expect(world.memberRegister.lastSaveConfirmation, 'the register never confirmed the save').toMatch(
    SAVED_CONFIRMATION,
  )
})

Then('the member on record has the new cellphone number', async ({ world }) => {
  const after = await world.membersApi.findMember(world.member.id)
  expect(after.phone, 'the cellphone number the register holds').toBe(world.newPhone)
})

When('an administrator opens the capture form', async ({ page, world }) => {
  world.capture = await new MemberCapturePage(page).open()
})

Then('every field on the form is labelled', async ({ world }) => {
  // Fields on removed tabs are skipped by this walk and closed-listed in
  // tests/harness/captureCatalogue.spec.js.
  expect(await world.capture.unlabelledFields(), 'fields whose label is attached to no control').toEqual([])
})

Then('the correction is accepted', async ({ world }) => {
  const after = await world.membersApi.findMember(world.member.id)
  expect(after.phone, 'the corrected cellphone number').toBe(world.newPhone)
})

When('an anonymous caller asks the API for every member', async ({ world, api }) => {
  world.anonymous = await new MembersApi(api.request).get('/api/members', { expectOk: false })
})

Then('they are refused', async ({ world }) => {
  expect(
    world.anonymous.status(),
    'an unauthenticated caller read the member register',
  ).toBeGreaterThanOrEqual(401)
})

Then('the member on record has every field that was captured', async ({ world, api }) => {
  const members = await new MembersApi(api.request).listMembers()
  const surname = world.captured.get('Surname')
  const onRecord = members.find((member) => member.surname === surname)

  expect(onRecord, `no member surnamed ${surname} is on record`).toBeTruthy()

  // Asserted as one object rather than field by field, so a failure names EVERY
  // field that did not survive instead of stopping at the first. A form that
  // drops six fields should not take six runs to find out about.
  const expected = {}
  const actual = {}
  for (const [label, value] of world.captured) {
    const { dto } = CAPTURE_FIELDS[label]
    expected[label] = value
    actual[label] = dto === null ? '(the member record has no column for this)' : String(onRecord[dto] ?? '')
  }
  expect(actual, 'fields that did not survive the round trip').toEqual(expected)
})

/**
 * The letter the capture sent, read back from the stack's mail catcher. Since client
 * 8c6b675 the capture IS the send (A64), and the send completes inside the capture's own
 * request -- so the letter is caught before the toast appears, and the poll is a ceiling
 * for a slow catcher rather than a wait for delivery. Exactly one: a second letter from one
 * capture is the double-send A64 describes for a failed second recipient.
 */
Then('the welcome letter is emailed to their personal address, as a PDF', async ({ world, api }) => {
  const address = world.captured.get('Personal Email')
  const mail = new MailApi(api.request)

  await expect.poll(async () => (await mail.messagesTo(address)).length, { message: `letters caught for ${address}` }).toBe(1)

  const [summary] = await mail.messagesTo(address)
  const letter = await mail.message(summary.ID)
  expect(letter.Subject).toContain('Welcome to SASBO')
  expect(
    letter.Attachments.map(({ FileName, ContentType }) => `${FileName} (${ContentType.split(';')[0].trim()})`),
    'what the letter carried',
  ).toEqual(['Sasbo Welcome Letter.pdf (application/pdf)'])

  // The bytes as well: a name and a type are only what the server SAID it attached.
  const pdf = await mail.attachment(letter.ID, letter.Attachments[0].PartID)
  expect(pdf.subarray(0, 5).toString('latin1'), 'the attachment does not begin like a PDF').toBe('%PDF-')
})

Then('the form shows the date of birth {string}', async ({ world }, date) => {
  await expect(world.capture.control('Date of Birth')).toHaveValue(date)
})

Then('the form shows the gender {string}', async ({ world }, gender) => {
  await expect(world.capture.control('Gender')).toHaveValue(gender)
})

Then('they are told {string}', async ({ world }, message) => {
  expect(world.refusal, 'what the administrator was told when the save was refused').toContain(message)
})

/*
 * 🔴 NEITHER STEP BELOW COMPARES THE REGISTER'S TOTAL WITH THE BACKGROUND'S SNAPSHOT.
 * The journeys run in parallel and capture members throughout, and a member cannot be
 * deleted (D9) -- the undo neutralises -- so the total only grows during a run. A count
 * taken a moment before the page loaded was one short whenever a capture landed in
 * between: p20 (2026-09-21) read 163 on the register against a snapshot of 162, with
 * another worker's capture in nginx's log at 22:39:01.7. A verdict decided by what the
 * other workers happened to be doing is flake by this suite's definition, so each step
 * is judged on its own evidence instead.
 */
Then('no member has been added', async ({ world }) => {
  // What THIS attempt sent, each awaited to its answer so one still in flight is not
  // missed. A refusal the client makes itself sends nothing, which is also "none added".
  const answers = await Promise.all(world.attemptedWrites.map((request) => request.response()))
  expect(
    answers.filter((answer) => answer?.ok()).map((answer) => `${answer.status()} ${answer.url()}`),
    'member writes from this attempt that the server accepted',
  ).toEqual([])
})

Then('the register reports every member on record', async ({ world }) => {
  // The page's own read IS the record at the moment it loaded -- the whole list, or the first page's total since
  // register paging -- so the claim, the register reports every member the server holds, is judged without a race.
  expect(world.registerReceived, 'the register was not opened by "an administrator opens the member register"').toBeDefined()
  expect(await world.memberRegister.reportedCount()).toBe(world.registerReceived.total)
})

Then('the register reports {int} member(s)', async ({ world }, count) => {
  await world.memberRegister.expectReportedCount(count)
})

Then('the register shows a row for each member on the page', async ({ world }) => {
  // 🔴 NOT "shows member X". Which members are on page one is the order's, which nothing here pins (see
  // `openAndReadTheLoad`). What is stable is the shape: a full page -- of the size this load's read says a page is --
  // or the whole register when it is smaller than one.
  const shown = Math.min(await world.memberRegister.reportedCount(), world.registerReceived.pageSize)
  await expect(world.memberRegister.rows()).toHaveCount(shown)
})

Then("the register shows that member's surname", async ({ world }) => {
  await expect(world.memberRegister.row(world.member.surname).first()).toBeVisible()
})

Then('the register says no members match', async ({ world }) => {
  await expect(world.memberRegister.emptyState()).toBeVisible()
})

/**
 * Hold the register's read of its members in flight until the scenario lets it go.
 *
 * 🔴 WITHOUT THIS THE LOADING SCENARIO IS A RACE, and a race is a flake: against a warm
 * stack "Loading members…" is on screen for a few milliseconds, so asserting it would pass
 * or fail on the host's mood. Holding the response makes the loading state last exactly as
 * long as the scenario needs it, and releasing it proves the same screen then arrives.
 *
 * Only the register's READ -- the whole list, or the paged search since register paging (`isRegisterRead`) -- never
 * `/api/members/{id}` nor the figure strip's overview, which pass straight on. The request is held, not faked: it
 * continues to the real API when released.
 */
Given('the member list is held in flight until it is released', async ({ page, world }) => {
  let release
  const released = new Promise((resolve) => {
    release = resolve
  })
  world.releaseMembers = release
  await page.route(/\/api\/members(\/register\/search)?(\?[^/]*)?$/, async (route) => {
    if (isRegisterReadRequest(route.request())) await released
    await route.continue()
  })
})

When('the member list is released', async ({ page, world }) => {
  // What the held read brings back is what "every member on record" is judged against, as when the register is opened
  // directly (see that step). Listened for BEFORE the release, so the answer cannot arrive unheard.
  const received = page.waitForResponse(isRegisterRead).then(registerLoadOf)
  world.releaseMembers()
  world.registerReceived = await received
})

Then('the register says it is loading', async ({ world }) => {
  await expect(world.memberRegister.loadingState()).toBeVisible()
})

Then('nothing else on the register claims to know what it could not load', async ({ world }) => {
  await expect(world.memberRegister.claimsAboutMissingData()).toHaveCount(0)
})

Then('the register offers a way to try again', async ({ world }) => {
  await expect(world.memberRegister.retry()).toBeVisible()
})

Then("the member's details show their identity number", async ({ world }) => {
  await world.memberRegister.expectDetail('Identity Number', world.member.identityNumber)
})

Then("the member's details show their bank", async ({ world }) => {
  await world.memberRegister.openRecordTab('Employment & Bank')
  await world.memberRegister.expectDetail('Bank Where Employed', world.member.bankName)
})

