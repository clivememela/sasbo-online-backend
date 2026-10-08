// @vitest-environment jsdom -- what the member record says after a save, when the answer is not a plain success
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers, registerPage } from '../../test/msw/memberRegister'

/**
 * 🔴 R3-F2 (P9's independent review), THE RECORD'S HALF: "NOTHING WAS WRITTEN" WAS SAID OF WRITES THAT MAY HAVE LANDED.
 *
 * `commitSave` put the write and the register refresh in ONE `try`, and every failure printed "We could not save these
 * changes. Nothing was written" -- so
 *   ① a save that SUCCEEDED, followed by a refresh that failed, was reported as not saved, and
 *   ② a save whose answer never arrived (a timeout, a lost connection, a 5xx) was reported as certainly not written.
 * Either way the clerk is invited to redo a change that already happened.
 *
 * The must-pass: a refusal (4xx) is a server that answered "no", and still says plainly that nothing was saved.
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
  paymentType: 'Payroll',
}

async function saveAChange() {
  const user = setupUser()
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for.*Alpha/i }))
  await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  const initials = await within(screen.getByTestId('member-panel')).findByLabelText(/^Initials/)
  await user.clear(initials)
  await user.type(initials, 'Q')
  await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
  const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
  await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument())
}

describe('the member record, after Save', () => {
  let registerCalls
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    registerCalls = 0
  })

  it('① a save that landed, then a refresh that failed, says the changes were SAVED', async () => {
    server.use(
      // A10: the page on screen is what a save refreshes -- its first read answers, the one after the save fails.
      http.post('*/api/members/register/search', async ({ request }) => {
        registerCalls += 1
        const query = await request.json()
        return registerCalls === 1
          ? HttpResponse.json(registerPage([ALPHA], query))
          : HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })
      }),
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', async ({ request }) => HttpResponse.json(await request.json())),
    )
    await saveAChange()

    expect(await screen.findByText(/record saved/)).toBeInTheDocument()
    expect(await screen.findByText(/register could not be refreshed/i)).toBeInTheDocument()
    expect(screen.queryByText(/not saved|nothing was written/i)).not.toBeInTheDocument()
  })

  it('② a save whose answer never arrived says it could not be confirmed -- never that nothing was written', async () => {
    server.use(
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', () => HttpResponse.error()),
    )
    await saveAChange()

    expect(await screen.findByText(/could not be confirmed/i)).toBeInTheDocument()
    expect(screen.queryByText(/nothing was written|were not saved/i)).not.toBeInTheDocument()
  })

  it('② a server error (5xx) could have landed too, and says so', async () => {
    server.use(
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })),
    )
    await saveAChange()

    expect(await screen.findByText(/could not be confirmed/i)).toBeInTheDocument()
  })

  it('the must-pass: a refusal (4xx) says the changes were not saved', async () => {
    server.use(
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', () =>
        HttpResponse.json({ status: 400, message: 'Identity number is invalid.' }, { status: 400 }),
      ),
    )
    await saveAChange()

    expect(await screen.findByText('The changes were not saved')).toBeInTheDocument()
    expect(screen.getByText(/Nothing was written/)).toBeInTheDocument()
    expect(screen.queryByText(/could not be confirmed/i)).not.toBeInTheDocument()
  })
})

/**
 * THE TWO DEFECTS THE MANUAL STREAM FOUND IN THE RECORD'S SAVE (7 Oct, Members.jsx:1016-1022 and :1043-1046).
 *
 *   ⓐ TWO PEOPLE EDITING ONE MEMBER: the later save silently undid the earlier one. The record now carries the version it
 *     read (V68), the save sends it back, the server refuses a record saved since (MEMBER_CHANGED_SINCE), and the record
 *     says someone else changed it and offers to read it again.
 *   ⓑ A REFUSED EDIT HID ITS REASON behind "We could not save these changes. Nothing was written — please try again."
 *     The server's sentence is what the clerk reads now, and the fields it names are marked.
 */
describe('the record’s save, refused', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
  })

  it('🔴 ⓑ a refused edit says the server’s reason -- not a sentence that hides it', async () => {
    server.use(
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', () => HttpResponse.json(
        { status: 400, message: 'Error: Selected role requires an assigned structure.' }, { status: 400 },
      )),
    )
    await saveAChange()

    expect(await screen.findByText('The changes were not saved')).toBeInTheDocument()
    expect(screen.getByText(/Selected role requires an assigned structure\./)).toBeInTheDocument()
    expect(screen.queryByText(/please try again/i)).not.toBeInTheDocument()
  })

  it('🔴 ⓑ and a field the server names is marked at the field, its tab opened', async () => {
    server.use(
      ...registerHandlers([ALPHA]),
      http.put('*/api/members/:id', () => HttpResponse.json({
        status: 400, message: 'Some fields are not valid.', validationErrors: { surname: 'Surname may not be blank.' },
      }, { status: 400 })),
    )
    await saveAChange()

    const panel = within(screen.getByTestId('member-panel'))
    expect(await panel.findByText('Surname may not be blank.')).toBeInTheDocument()
    expect(panel.getByRole('button', { name: /Personal Information/ })).toHaveAttribute('aria-current', 'true')
  })

  it('🔴 ⓐ a save on a record somebody saved since says so, and Reload reads the record as it now stands', async () => {
    const members = [{ ...ALPHA, version: 3 }]
    let reads = 0
    server.use(
      http.get('*/api/members/:id', () => {
        reads += 1
        return HttpResponse.json(members[0])
      }),
      ...registerHandlers(members),
      http.put('*/api/members/:id', () => HttpResponse.json({
        status: 409,
        code: 'MEMBER_CHANGED_SINCE',
        message: 'Someone else saved changes to this member after you opened the record, so your changes were not saved. '
          + 'Reload the record to see theirs, then make your changes again.',
        details: { memberId: '7', versionOnRecord: '4' },
      }, { status: 409 })),
    )
    await saveAChange()

    expect(await screen.findByText('Someone else changed this record')).toBeInTheDocument()
    expect(screen.getByText(/Someone else saved changes to this member/)).toBeInTheDocument()
    expect(screen.queryByText(/could not be confirmed/i)).not.toBeInTheDocument()

    // Somebody else's save: the record as it now stands.
    members[0] = { ...ALPHA, version: 4, initials: 'Z' }
    const readsBefore = reads
    await setupUser().click(screen.getByRole('button', { name: 'Reload the record' }))

    await waitFor(() => expect(reads).toBe(readsBefore + 1))
    const panel = within(screen.getByTestId('member-panel'))
    expect(await panel.findByRole('button', { name: /Edit Member/i })).toBeInTheDocument()
    expect(await panel.findByText('Z')).toBeInTheDocument()
    expect(screen.queryByText('Someone else changed this record')).not.toBeInTheDocument()
  })

  it('🔴 ⓐ the version read with the record goes back with the save -- and the version a save wrote with the next', async () => {
    const bodies = []
    server.use(
      ...registerHandlers([{ ...ALPHA, version: 3 }]),
      http.put('*/api/members/:id', async ({ request }) => {
        const body = await request.json()
        bodies.push(body)
        return HttpResponse.json({ ...body, version: (body.version ?? 0) + 1 })
      }),
    )
    await saveAChange()
    expect(await screen.findByText(/record saved/)).toBeInTheDocument()

    const user = setupUser()
    await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
    const initials = await within(screen.getByTestId('member-panel')).findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'R')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[0].version).toBe(3)
    expect(bodies[1].version).toBe(4)
  })
})
