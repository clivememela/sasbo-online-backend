// @vitest-environment jsdom -- the member record's Change status dialog, driven as a person drives it
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { NOTE_LIMIT } from '../../components/members/ChangeStatus'
import vocabulary from '../../test/api-vocabulary.json'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * MONTH END `03.3` ON THE RECORD -- A STATUS IS CHANGED, NEVER EDITED.
 *
 * The Membership tab shows the status as the card does, with *Change status* beside it: a dialog of the new status,
 * SASBO's code for it and a note, sent to `POST /api/members/{id}/status` with the status the screen showed. Editing
 * shows the status and cannot change it, and the edit's save never names it. What the dialog says, it says after the
 * member has been read again (ME-3). Its code list has four states, and none of them is an empty dropdown.
 *
 * Answered as the server answers (MD48): the lists, the change and every refusal are the golden answers of
 * `month-end-examples.json`; the member and the note are invented.
 */

const ALPHA = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Alpha',
  initials: 'S',
  identityType: 'I.D. Number',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
}

const ROUTE = 'POST /api/members/{id}/status'
const CHANGED = monthEndExamples.answers[ROUTE]
const DELETION_CODES = 'GET /api/reference/deletion-codes'
const SUSPENSION_CODES = 'GET /api/reference/suspension-codes'
const AGAIN = 'Asking again is safe — the same change asked twice is written once.'

/** The member as the register listed them, and as the server holds them now -- the two part when somebody else acts. */
let listed
let stored
/** Every change the dialog sent, how often the record read the member again, and how often each list was asked for. */
let sent
let reads
let asked
/**
 * The record is open. Since A10 the register lists slim rows and a row OPENS by reading the member
 * (`GET /api/members/{id}`), so the read that opens the record answers `listed` -- what the screen read -- and is not
 * one of the `reads`; every read after it is the record reading the member AGAIN, and answers `stored`, as the
 * register's page does from then on.
 */
let opened

/** The server's answer to a change it made: the golden answer's shape, carrying this change (MD48). */
function made(change) {
  const from = stored.status
  stored = { ...stored, status: change.toStatus }
  return {
    ...CHANGED.body,
    memberId: stored.id,
    membershipNumber: stored.membershipNumber,
    status: change.toStatus,
    change: {
      ...CHANGED.body.change,
      fromStatus: from,
      toStatus: change.toStatus,
      reason: change.reason,
      deletionCode: change.deletionCode ?? null,
      // The reason under the code as THIS change carried it -- never the golden change's own.
      reasonUnderCode: change.reasonUnderCode ?? null,
      suspensionCode: change.suspensionCode ?? null,
      note: change.note ?? null,
    },
  }
}

beforeEach(() => {
  // Every failure is reported through the one sink as well as said.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  listed = { ...ALPHA }
  stored = { ...ALPHA }
  sent = []
  reads = 0
  opened = false
  asked = { deletion: 0, suspension: 0 }
  // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
  server.use(...pageFixtures)
  server.use(
    http.get('*/api/members/:id', () => {
      if (!opened) return HttpResponse.json(listed)
      reads += 1
      return HttpResponse.json(stored)
    }),
    http.get('*/api/reference/deletion-codes', () => {
      asked.deletion += 1
      return HttpResponse.json(monthEndBody(DELETION_CODES))
    }),
    http.get('*/api/reference/suspension-codes', () => {
      asked.suspension += 1
      return HttpResponse.json(monthEndBody(SUSPENSION_CODES))
    }),
    http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      return HttpResponse.json(made(change))
    }),
    // Last, so the record's own handler above answers first. The page is the server's as it stands.
    ...registerHandlers(() => [opened ? stored : listed]),
  )
})

const panel = () => within(screen.getByTestId('member-panel'))
/** The status on the record's Membership tab: the value under its label. */
const statusField = () => panel().getByText('Account Membership Status').nextElementSibling
/** Asked for afresh each time: a list that failed and came back is a new control, not the one that was disabled. */
const codeSelect = (dialog) => dialog.getByLabelText(/^SASBO’s (deletion|suspension) code/)
/** The reason SASBO keeps under the code chosen -- there only where the code has any (04.5). */
const reasonSelect = (dialog) => dialog.getByLabelText(/^The reason they gave/)
const optionsOf = (select) => within(select).getAllByRole('option').map((option) => option.textContent)

async function openRecord(user) {
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for.*Alpha/i }))
  await user.click(await panel().findByRole('button', { name: /Membership & Subscriptions/i }))
  opened = true
}

async function openDialog(user) {
  await openRecord(user)
  await user.click(panel().getByRole('button', { name: 'Change status' }))
  return within(await screen.findByRole('dialog', { name: 'Change status' }))
}

/** Choose the new status -- and, where it takes one, SASBO's code, once the list is there to choose from. */
async function choose(dialog, user, status, code) {
  await user.selectOptions(dialog.getByLabelText(/^New status/), status)
  if (code === undefined) return
  await waitFor(() => expect(codeSelect(dialog)).toBeEnabled())
  await user.selectOptions(codeSelect(dialog), String(code))
}

const press = (dialog, user) => user.click(dialog.getByRole('button', { name: 'Change status' }))
const closed = () => waitFor(() => expect(screen.queryByRole('dialog', { name: 'Change status' })).not.toBeInTheDocument())

describe('the record -- a status is shown, and changed only through Change status', () => {
  it('reading: the Membership tab shows the status as the card does, with Change status beside it', async () => {
    const user = setupUser()
    await openRecord(user)

    expect(within(statusField()).getByText('Active')).toBeInTheDocument()
    expect(within(statusField()).getByRole('button', { name: 'Change status' })).toBeInTheDocument()
  })

  it('🔴 editing: the status is shown and cannot be changed there -- no control, and the way to change it is said', async () => {
    const user = setupUser()
    await openRecord(user)
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))

    expect(panel().queryByLabelText(/Account Membership Status/)).not.toBeInTheDocument()
    expect(panel().queryByRole('combobox', { name: /Status/ })).not.toBeInTheDocument()
    expect(within(statusField()).getByText('Active')).toBeInTheDocument()
    expect(panel().getByText('Changed with Change status, once this edit is saved or cancelled.')).toBeInTheDocument()
    // Not beside an edit in progress: a change of status there would reseed the form under the person typing.
    expect(panel().queryByRole('button', { name: 'Change status' })).not.toBeInTheDocument()
  })

  it('🔴 an edit\'s save never names the status -- and the must-pass: the rest of the record is sent as it stands', async () => {
    let saved
    server.use(http.put('*/api/members/:id', async ({ request }) => {
      saved = await request.json()
      return HttpResponse.json(saved)
    }))
    const user = setupUser()
    await openRecord(user)
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))
    await user.click(panel().getByRole('button', { name: /Personal Information/i }))
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const confirm = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(confirm).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).not.toHaveProperty('status')
    expect(saved).toMatchObject({ id: 7, initials: 'Q', surname: 'Alpha', identityNumber: '9001010000080' })
    // The record still reads the status it had: a save that names none leaves it where it was.
    await user.click(panel().getByRole('button', { name: /Membership & Subscriptions/i }))
    expect(within(statusField()).getByText('Active')).toBeInTheDocument()
  })
})

describe('the dialog\'s code list -- four states, and never an empty dropdown', () => {
  it('asks for nothing until a status needs a code, then for each list once, kept for the visit (MD69)', async () => {
    const user = setupUser()
    let dialog = await openDialog(user)
    expect(asked).toEqual({ deletion: 0, suspension: 0 })

    // A reinstatement's kind carries no code, so no list is asked for and no code is shown.
    await choose(dialog, user, 'Pending')
    expect(dialog.queryByLabelText(/^SASBO’s/)).not.toBeInTheDocument()
    await choose(dialog, user, 'Resigned', 2)
    await choose(dialog, user, 'Retired')
    expect(codeSelect(dialog)).toBeEnabled()
    await choose(dialog, user, 'Suspended', 13)
    await choose(dialog, user, 'Terminated')
    expect(codeSelect(dialog)).toBeEnabled()

    // Closed and opened again: a fresh form, and SASBO's lists as they were read.
    await user.click(dialog.getByRole('button', { name: 'Cancel' }))
    await closed()
    await user.click(panel().getByRole('button', { name: 'Change status' }))
    dialog = within(await screen.findByRole('dialog', { name: 'Change status' }))
    expect(dialog.getByLabelText(/^New status/)).toHaveValue('')
    await choose(dialog, user, 'Resigned')
    expect(codeSelect(dialog)).toBeEnabled()

    expect(asked).toEqual({ deletion: 1, suspension: 1 })
  })

  it('loading: the code says so and cannot be chosen, and Change status waits -- then ready, with where the list was read from', async () => {
    const held = deferred()
    server.use(http.get('*/api/reference/deletion-codes', async () => {
      await held.promise
      return HttpResponse.json(monthEndBody(DELETION_CODES))
    }))
    const user = setupUser()
    try {
      const dialog = await openDialog(user)
      await choose(dialog, user, 'Resigned')

      expect(codeSelect(dialog)).toBeDisabled()
      expect(codeSelect(dialog)).toHaveAttribute('aria-busy', 'true')
      expect(optionsOf(codeSelect(dialog))).toEqual(['Loading…'])
      expect(dialog.getByRole('button', { name: 'Change status' })).toBeDisabled()
      expect(dialog.getByText('Choose SASBO’s code for it.')).toBeInTheDocument()

      held.resolve()
      await waitFor(() => expect(codeSelect(dialog)).toBeEnabled())
      // SASBO's codes by their numbers, in the server's order -- never a row's id (RC7).
      expect(optionsOf(codeSelect(dialog))).toEqual(['Choose a code', '2 · Resigned Society', '5 · Subscription in Arrears'])
      expect(dialog.getByText('As printed on 31 August 2026 — not yet confirmed by SASBO')).toBeInTheDocument()
    } finally {
      held.resolve()
    }
  })

  it.each([
    ['the server\'s own reason (ME-19)',
      () => HttpResponse.json({ ...monthEndBody(DELETION_CODES), rows: [], reason: 'NOT_CONFIRMED' }),
      'Not confirmed by SASBO yet.'],
    ['an answer that is not a list\'s -- the suite\'s shared `[]` (MV-36)',
      () => HttpResponse.json([]),
      'This server’s answer could not be read.'],
    ['a server built before the route (MD58)',
      () => HttpResponse.json({ status: 404, error: 'Not Found' }, { status: 404 }),
      'This server does not have this list yet.'],
  ])('empty -- %s: the control says why and offers nothing, and it is not a failure', async (_, answer, sentence) => {
    server.use(http.get('*/api/reference/deletion-codes', answer))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned')

    await waitFor(() => expect(optionsOf(codeSelect(dialog))).toEqual([sentence]))
    expect(codeSelect(dialog)).toBeDisabled()
    expect(dialog.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeDisabled()
  })

  it('failed: said as a failure with Try again -- never as "none" -- and Try again asks again', async () => {
    server.use(http.get('*/api/reference/deletion-codes', () => {
      asked.deletion += 1
      return asked.deletion === 1
        ? HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })
        : HttpResponse.json(monthEndBody(DELETION_CODES))
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned')

    expect(await dialog.findByText('SASBO’s deletion code could not be loaded.')).toBeInTheDocument()
    expect(codeSelect(dialog)).toBeDisabled()
    expect(optionsOf(codeSelect(dialog))).toEqual(['Unavailable'])
    await user.click(dialog.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(codeSelect(dialog)).toBeEnabled())
    expect(optionsOf(codeSelect(dialog))).toEqual(['Choose a code', '2 · Resigned Society', '5 · Subscription in Arrears'])
    expect(dialog.queryByText('SASBO’s deletion code could not be loaded.')).not.toBeInTheDocument()
    expect(asked.deletion).toBe(2)
  })
})

describe('what is sent -- the status the screen showed, and SASBO\'s code by its number (RC7)', () => {
  it('🔑 a leaving: the deletion code, the reason under it and the note, exactly the server\'s own example -- then read again, and said', async () => {
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await user.selectOptions(reasonSelect(dialog), CHANGED.requestBody.reasonUnderCode)
    await user.type(dialog.getByLabelText(/^Note/), 'Invented: moved away')
    await press(dialog, user)

    expect(await screen.findByText(`Synthetic Alpha is now Resigned. The reason they gave: ${CHANGED.requestBody.reasonUnderCode}.`))
      .toBeInTheDocument()
    expect(sent).toEqual([CHANGED.requestBody])
    expect(reads, 'the record read the member again before it said anything').toBe(1)
    await closed()
    expect(within(statusField()).getByText('Resigned')).toBeInTheDocument()
    const row = screen.getByRole('button', { name: /open the record for.*Alpha/i }).closest('tr')
    expect(within(row).getByText('Resigned')).toBeInTheDocument()
  })

  it('a suspension: the suspension code -- and no note is sent where none was written', async () => {
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Suspended', 13)
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Suspended.')).toBeInTheDocument()
    expect(sent).toEqual([{ toStatus: 'Suspended', expectedStatus: 'Active', reason: 'SUSPENSION_CODE', suspensionCode: 13 }])
  })

  it('a reinstatement: from a leaving status back to Active, with no code -- and no list is asked for', async () => {
    listed = { ...ALPHA, status: 'Resigned' }
    stored = { ...listed }
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Active')
    expect(dialog.queryByLabelText(/^SASBO’s/)).not.toBeInTheDocument()
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Active.')).toBeInTheDocument()
    expect(sent).toEqual([{ toStatus: 'Active', expectedStatus: 'Resigned', reason: 'REINSTATED' }])
    expect(asked).toEqual({ deletion: 0, suspension: 0 })
  })

  it('the new status offers every status of the vocabulary but the member\'s own; the note stops at the server\'s limit', async () => {
    const user = setupUser()
    const dialog = await openDialog(user)

    const others = vocabulary.monthEnd.memberStatuses.map((status) => status.name).filter((name) => name !== 'Active')
    expect(optionsOf(dialog.getByLabelText(/^New status/))).toEqual(['Choose a status', ...others])
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeDisabled()
    expect(dialog.getByText('Choose the new status.')).toBeInTheDocument()

    const note = dialog.getByLabelText(/^Note/)
    expect(note).toHaveAttribute('maxLength', String(NOTE_LIMIT))
    await user.type(note, 'Invented')
    expect(dialog.getByText(`8 of ${NOTE_LIMIT} characters`)).toBeInTheDocument()
  })

})

describe('the reason under a code -- what a resigning member gave, as SASBO keeps it (04.5)', () => {
  // SASBO's published codes (`api-vocabulary.json`): numbered, and the reasons for resigning kept under 2.
  const numbered = vocabulary.monthEnd.deletionCodes.filter((row) => Number.isInteger(row.code))
  const underTwo = vocabulary.monthEnd.deletionCodes.filter((row) => row.under === 2).map((row) => row.name)
  const published = () => server.use(http.get('*/api/reference/deletion-codes', () => {
    asked.deletion += 1
    return HttpResponse.json({ ...monthEndBody(DELETION_CODES), rows: vocabulary.monthEnd.deletionCodes })
  }))

  it('the reasons kept under the code chosen are offered -- optional -- and none chosen, the change carries the code alone', async () => {
    published()
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)

    expect(optionsOf(codeSelect(dialog))).toEqual(['Choose a code', ...numbered.map((row) => `${row.code} · ${row.name}`)])
    expect(optionsOf(reasonSelect(dialog))).toEqual(['No reason given', ...underTwo])
    expect(reasonSelect(dialog)).not.toBeRequired()
    expect(reasonSelect(dialog)).toHaveAccessibleDescription('Optional — a reason SASBO keeps under 2 · Resigned Society.')
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeEnabled()
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
    expect(sent).toEqual([{ toStatus: 'Resigned', expectedStatus: 'Active', reason: 'DELETION_CODE', deletionCode: 2 }])
  })

  it('🔴 one chosen is sent by its name as reasonUnderCode, and what is said names it -- the note stays the administrator\'s own', async () => {
    published()
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await user.selectOptions(reasonSelect(dialog), 'Financial')
    await user.type(dialog.getByLabelText(/^Note/), 'Invented note')
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned. The reason they gave: Financial.')).toBeInTheDocument()
    expect(sent).toEqual([{
      toStatus: 'Resigned', expectedStatus: 'Active', reason: 'DELETION_CODE', deletionCode: 2, reasonUnderCode: 'Financial',
      note: 'Invented note',
    }])
  })

  it('a code with no reasons under it offers none -- and changing the code lets go of the reason chosen', async () => {
    published()
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 5)
    expect(dialog.queryByLabelText(/^The reason they gave/)).not.toBeInTheDocument()

    await user.selectOptions(codeSelect(dialog), '2')
    await user.selectOptions(reasonSelect(dialog), 'Personal')
    await user.selectOptions(codeSelect(dialog), '5')
    expect(dialog.queryByLabelText(/^The reason they gave/)).not.toBeInTheDocument()
    await user.selectOptions(codeSelect(dialog), '2')
    expect(reasonSelect(dialog)).toHaveValue('')
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
    expect(sent).toEqual([{ toStatus: 'Resigned', expectedStatus: 'Active', reason: 'DELETION_CODE', deletionCode: 2 }])
  })

  it('a suspension never carries one, whatever was chosen for a leaving before it', async () => {
    published()
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await user.selectOptions(reasonSelect(dialog), 'Personal')
    await choose(dialog, user, 'Suspended', 13)
    expect(dialog.queryByLabelText(/^The reason they gave/)).not.toBeInTheDocument()
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Suspended.')).toBeInTheDocument()
    expect(sent).toEqual([{ toStatus: 'Suspended', expectedStatus: 'Active', reason: 'SUSPENSION_CODE', suspensionCode: 13 }])
  })

  it('🔴 a reason the server does not keep under the code: said AT THE REASON in its words, the code kept, the list read again', async () => {
    published()
    // The server's refusal in its golden shape, naming the reason's code and what it keeps under it (details `known`).
    const refusal = monthEndExamples.refusals.DELETION_CODE_UNKNOWN
    const sentence = 'That reason is not one SASBO keeps under code 2. Choose one of its own, or none.'
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({ ...refusal.body, message: sentence, details: { deletionCode: '2', known: underTwo.join(',') } },
        { status: refusal.status })
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await user.selectOptions(reasonSelect(dialog), 'Other Union')
    await press(dialog, user)

    await waitFor(() => expect(asked.deletion).toBe(2))
    await waitFor(() => expect(reasonSelect(dialog)).toHaveAccessibleDescription(sentence))
    expect(reasonSelect(dialog)).toHaveValue('')
    expect(codeSelect(dialog)).toHaveValue('2')
    expect(codeSelect(dialog)).not.toHaveAccessibleDescription(sentence)
    expect(dialog.getAllByText(sentence)).toHaveLength(1)
    expect(sent).toHaveLength(1)
    expect(sent[0]).toMatchObject({ deletionCode: 2, reasonUnderCode: 'Other Union' })
  })
})

describe('what the dialog says -- once the member has been read again (ME-3)', () => {
  it('🔴 STATUS_CHANGED_SINCE: read again, the server\'s sentence said, the choices kept -- and asked again on what is shown now', async () => {
    listed = { ...ALPHA, status: 'Pending' } // the screen read Pending...
    stored = { ...ALPHA, status: 'Active' } // ...and somebody has since moved the member to Active
    const refusal = monthEndExamples.refusals.STATUS_CHANGED_SINCE
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      return sent.length === 1 ? HttpResponse.json(refusal.body, { status: refusal.status }) : HttpResponse.json(made(change))
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await dialog.findByRole('alert')).toHaveTextContent(refusal.body.message)
    expect(reads).toBe(1)
    expect(within(statusField()).getByText('Active')).toBeInTheDocument()
    expect(dialog.getByLabelText(/^New status/)).toHaveValue('Resigned')
    expect(codeSelect(dialog)).toHaveValue('2')
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
    expect(sent.map((change) => change.expectedStatus)).toEqual(['Pending', 'Active'])
  })

  it('MEMBER_NOT_FOUND: the server\'s sentence, and nothing left to send', async () => {
    server.use(monthEndRefusal('MEMBER_NOT_FOUND', ROUTE))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await dialog.findByRole('alert')).toHaveTextContent(monthEndExamples.refusals.MEMBER_NOT_FOUND.body.message)
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeDisabled()
  })

  it.each([
    ['DELETION_CODE_UNKNOWN', 'Resigned', 2, 'deletion'],
    ['SUSPENSION_CODE_UNKNOWN', 'Suspended', 13, 'suspension'],
  ])('%s: said at the code in the server\'s words, the code cleared, and SASBO\'s list asked for again', async (code, status, number, list) => {
    server.use(monthEndRefusal(code, ROUTE))
    const sentence = monthEndExamples.refusals[code].body.message
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, status, number)
    await press(dialog, user)

    await waitFor(() => expect(asked[list]).toBe(2))
    await waitFor(() => expect(codeSelect(dialog)).toBeEnabled())
    expect(codeSelect(dialog)).toHaveAccessibleDescription(sentence)
    expect(codeSelect(dialog)).toHaveValue('')
    expect(dialog.getAllByText(sentence)).toHaveLength(1)
  })

  it('🔑 no answer, and the change had been made: read again BEFORE a word is said -- and said as done, never as "not saved"', async () => {
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      made(change) // the server wrote it...
      return HttpResponse.error() // ...and its answer never arrived
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
    expect(reads).toBe(1)
    expect(screen.queryByText(/could not be confirmed|not saved/)).not.toBeInTheDocument()
    await closed()
    expect(within(statusField()).getByText('Resigned')).toBeInTheDocument()
  })

  it('no answer, and the member stands where they did: said as it is -- and asking again sends the same change', async () => {
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      return sent.length === 1 ? HttpResponse.error() : HttpResponse.json(made(change))
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await dialog.findByRole('alert'))
      .toHaveTextContent(`The change could not be confirmed. Synthetic Alpha is Active now. ${AGAIN}`)
    expect(reads).toBe(1)
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
    expect(sent).toHaveLength(2)
    expect(sent[1]).toEqual(sent[0])
  })

  it('no answer, and the member cannot be read again either: said so, and nothing is claimed', async () => {
    server.use(
      http.post('*/api/members/:id/status', () => HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })),
      http.get('*/api/members/:id', () => {
        // The read that opens the record is answered (by the handler beneath); every read after it fails.
        if (!opened) return undefined
        reads += 1
        return HttpResponse.error()
      }),
    )
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await dialog.findByRole('alert'))
      .toHaveTextContent(`The change could not be confirmed. The member could not be read again either. ${AGAIN}`)
    expect(reads).toBe(1)
    expect(within(statusField()).getByText('Active')).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeEnabled()
  })

  it('a member already where the change asks: nothing more is written, and that is said', async () => {
    stored = { ...ALPHA, status: 'Resigned' } // somebody made this very change since the screen read the member
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      return HttpResponse.json({ ...CHANGED.body, memberId: 7, membershipNumber: 'M0007', status: stored.status, change: null })
    }))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await screen.findByText('Synthetic Alpha was already Resigned. Nothing more was written.')).toBeInTheDocument()
    expect(reads).toBe(1)
  })

  it('any other refusal is said in the server\'s words (ME-13) -- and may be asked again', async () => {
    server.use(monthEndRefusal('STATUS_REASON_MISMATCH', ROUTE))
    const user = setupUser()
    const dialog = await openDialog(user)
    await choose(dialog, user, 'Resigned', 2)
    await press(dialog, user)

    expect(await dialog.findByRole('alert')).toHaveTextContent(monthEndExamples.refusals.STATUS_REASON_MISMATCH.body.message)
    expect(reads, 'a refusal is an answer: nothing to read again').toBe(0)
    expect(dialog.getByRole('button', { name: 'Change status' })).toBeEnabled()
  })

  it('while the change runs, nothing closes the dialog and it cannot be sent twice', async () => {
    const held = deferred()
    server.use(http.post('*/api/members/:id/status', async ({ request }) => {
      const change = await request.json()
      sent.push(change)
      await held.promise
      return HttpResponse.json(made(change))
    }))
    const user = setupUser()
    try {
      const dialog = await openDialog(user)
      await choose(dialog, user, 'Resigned', 2)
      await press(dialog, user)
      await waitFor(() => expect(sent).toHaveLength(1))

      expect(dialog.getByRole('button', { name: 'Change status' })).toHaveAttribute('aria-busy', 'true')
      expect(dialog.getByRole('button', { name: 'Change status' })).toBeDisabled()
      expect(dialog.getByRole('button', { name: 'Cancel' })).toBeDisabled()
      await user.keyboard('{Escape}')
      expect(screen.getByRole('dialog', { name: 'Change status' })).toBeInTheDocument()

      held.resolve()
      expect(await screen.findByText('Synthetic Alpha is now Resigned.')).toBeInTheDocument()
      expect(sent).toHaveLength(1)
    } finally {
      held.resolve()
    }
  })
})
