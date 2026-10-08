// @vitest-environment jsdom -- a second re-debit in one run waits for the member's permission (D32, R4-4)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbDebits from './AcbDebits'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * R4-4 (T2-I): a member's SECOND unpaid presented again in one run goes on the same file (Q38) only with
 * the member's permission -- who agreed, when and how (D32). The confirm cannot be pressed without the
 * note, and says why; with the note it records, against the UNPAID's own run and line; a failure is
 * said inside the dialog. The must-pass is the note that records.
 */

const pageOf = (content, size = 50) => ({ content, page: 0, size, totalElements: content.length, totalPages: content.length ? 1 : 0 })

const RUNS = [
  { id: 41, actionMonth: '2026-10', status: 'PREPARED', version: 1 },
  { id: 40, actionMonth: '2026-09', status: 'RETURNED', version: 3 },
]

const HELD = { lineId: 9, runId: 30, memberId: 7, membershipNumber: 'SYN-0007', surname: 'Testperson', month: '2026-08', amount: 174, permitted: false }

let heldReads
let permissions

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  heldReads = 0
  permissions = []
  server.use(
    http.get('*/api/acb/runs', () => HttpResponse.json(pageOf(RUNS, 12))),
    http.get('*/api/acb/runs/:id/unpaids', () => HttpResponse.json(pageOf([]))),
    http.get('*/api/acb/runs/:id/letters/held', () => HttpResponse.json(pageOf([]))),
    http.get('*/api/acb/runs/:id/held-over', ({ params }) => {
      heldReads += 1
      expect(params.id).toBe('41')
      return HttpResponse.json(pageOf([{ ...HELD, permitted: permissions.length > 0 }]))
    }),
    http.post('*/api/acb/runs/:id/lines/:lineId/permission', async ({ params, request }) => {
      permissions.push({ run: params.id, line: params.lineId, ...(await request.json()) })
      return HttpResponse.json({ id: 9, decision: 'RE_PRESENT', permissionNote: 'kept' })
    }),
    ...pageFixtures,
  )
})

const openThePermission = async (user) => {
  const table = await screen.findByRole('table', { name: "Re-debits held over for the member's permission" })
  await user.click(await within(table).findByRole('button', { name: 'Record Testperson’s permission' }))
  return screen.findByRole('dialog', { name: 'The member’s permission' })
}

describe('R4-4: a second re-debit in one run needs the member\'s permission', () => {
  it('lists the held-over re-debit of the next run, with the month it was unpaid for', async () => {
    renderWithProviders(<AcbDebits />)
    expect(await screen.findByRole('heading', { name: 'Second re-debits held over — October 2026' })).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: "Re-debits held over for the member's permission" })
    expect(await within(table).findByText('August 2026')).toBeInTheDocument()
  })

  it('🔒 the confirm cannot be pressed without the note, and says why -- blank and spaces alike', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const dialog = await openThePermission(user)
    const record = within(dialog).getByRole('button', { name: 'Record permission' })

    expect(record).toBeDisabled()
    expect(record).toHaveAccessibleDescription('Write who agreed, when and how first.')
    await user.type(within(dialog).getByLabelText('Who agreed, when, and how'), '   ')
    expect(record).toBeDisabled()
    await user.click(record)
    expect(permissions).toEqual([])
  })

  it('the must-pass: with the note it records against the unpaid\'s own run and line, and the list says so', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const dialog = await openThePermission(user)
    await user.type(within(dialog).getByLabelText('Who agreed, when, and how'), 'The member, by phone, 21 September')
    const record = within(dialog).getByRole('button', { name: 'Record permission' })
    expect(record).toBeEnabled()
    expect(record).not.toHaveAccessibleDescription()
    await user.click(record)

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(permissions).toEqual([{ run: '30', line: '9', note: 'The member, by phone, 21 September' }])
    expect(await screen.findByText(/Permission recorded for Testperson\. The October 2026 run takes this re-debit/)).toBeInTheDocument()
    expect(await screen.findByText('On record — prepare the run again to take it')).toBeInTheDocument()
    expect(heldReads).toBe(2)
  })

  it('a note longer than the server keeps is not sent, and the button says so', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const dialog = await openThePermission(user)
    await user.click(within(dialog).getByLabelText('Who agreed, when, and how'))
    await user.paste('x'.repeat(301))
    expect(within(dialog).getByRole('button', { name: 'Record permission' })).toHaveAccessibleDescription('Keep it to 300 characters.')
  })

  it('a refused permission is said inside the dialog, and nothing is claimed', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/permission', () =>
      HttpResponse.json({ status: 400, message: 'The member’s permission is kept to 300 characters.', code: 'REQUEST_MALFORMED', details: {} }, { status: 400 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const dialog = await openThePermission(user)
    await user.type(within(dialog).getByLabelText('Who agreed, when, and how'), 'The member, by phone')
    await user.click(within(dialog).getByRole('button', { name: 'Record permission' }))

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(/was not recorded\. Nothing was written/)
    expect(screen.queryByText(/Permission recorded/)).not.toBeInTheDocument()
  })

  it('an answer that never came is not repeated: the dialog stops offering the write, and closing reads the list again', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/permission', () => HttpResponse.error()))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const dialog = await openThePermission(user)
    await user.type(within(dialog).getByLabelText('Who agreed, when, and how'), 'The member, by phone')
    await user.click(within(dialog).getByRole('button', { name: 'Record permission' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/could not be confirmed\. It may have been recorded/)
    expect(within(dialog).queryByRole('button', { name: 'Record permission' })).not.toBeInTheDocument()
    await user.click(within(dialog).getAllByRole('button', { name: 'Close' }).at(-1))
    await waitFor(() => expect(heldReads).toBe(2))
  })
})
