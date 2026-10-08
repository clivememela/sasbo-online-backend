// @vitest-environment jsdom -- the run's held letters: send once there is an address, or mark handled (Q46, R4-5)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbDebits from './AcbDebits'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * R4-5 (T2-I): a held letter is SENT once the member has an address, or MARKED HANDLED with a note
 * (Q46); a failure is said INSIDE the dialog; and after a timeout the screen re-reads rather than
 * repeats -- a letter must never go twice. Each red case has its must-pass.
 */

const pageOf = (content, size = 50) => ({ content, page: 0, size, totalElements: content.length, totalPages: content.length ? 1 : 0 })

const RUN = { id: 40, actionMonth: '2026-09', status: 'RETURNED', version: 3 }

// The server's AcbHeldLetterDto: a held letter is QUEUED, and every one carries its status (EH-E).
const letter = (overrides) => ({
  letterId: 21, lineId: 3, memberId: 7, membershipNumber: 'SYN-0007', surname: 'Testperson', template: 'RE_DEBIT',
  status: 'QUEUED', reason: 'NO_EMAIL', canSend: true, ...overrides,
})

let letterReads
let writes

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  letterReads = 0
  writes = []
  server.use(
    http.get('*/api/acb/runs', () => HttpResponse.json(pageOf([RUN], 12))),
    http.get('*/api/acb/runs/:id/unpaids', () => HttpResponse.json(pageOf([]))),
    http.get('*/api/acb/runs/:id/letters/held', () => {
      letterReads += 1
      return HttpResponse.json(pageOf([
        letter(),
        letter({ letterId: 22, lineId: 4, memberId: 8, membershipNumber: 'SYN-0008', surname: 'Sampleton',
          template: 'RETURN_DEBIT', canSend: false }),
      ]))
    }),
    http.post('*/api/acb/runs/:id/letters/:letterId/handled', async ({ params, request }) => {
      writes.push({ action: 'handled', run: params.id, letter: params.letterId, ...(await request.json()) })
      return HttpResponse.json(letter({ letterId: Number(params.letterId) }))
    }),
    http.post('*/api/acb/runs/:id/letters/:letterId/send', ({ params }) => {
      writes.push({ action: 'send', run: params.id, letter: params.letterId })
      return HttpResponse.json(letter({ letterId: Number(params.letterId) }))
    }),
    ...pageFixtures,
  )
})

const rowOf = async (surname) => {
  const table = await screen.findByRole('table', { name: 'Letters that could not go' })
  return (await within(table).findByText(new RegExp(surname))).closest('tr')
}

describe('EH-E, R3-7: a send that failed', () => {
  beforeEach(() => {
    server.use(http.get('*/api/acb/runs/:id/letters/held', () => {
      letterReads += 1
      return HttpResponse.json(pageOf([
        letter({ letterId: 31, surname: 'Failperson', status: 'FAILED', reason: 'MailSendException' }),
        letter({ letterId: 32, lineId: 5, memberId: 9, membershipNumber: 'SYN-0009', surname: 'Changedperson',
          status: 'FAILED', reason: 'LETTER_CHANGED_SINCE_RENDERED' }),
        letter({ letterId: 21, status: 'QUEUED' }),
      ]))
    }))
  })

  it('is listed with why it did not go -- never a message -- and offers Send again', async () => {
    renderWithProviders(<AcbDebits />)
    const failed = await rowOf('Failperson')
    expect(within(failed).getByText('Sending failed')).toBeInTheDocument()
    expect(within(failed).queryByText(/MailSendException/)).not.toBeInTheDocument()
    expect(within(failed).getByRole('button', { name: 'Send Failperson’s letter again' })).toHaveTextContent('Send again')
    expect(within(await rowOf('Changedperson')).getByText(/details changed after it was written/)).toBeInTheDocument()
    // The must-pass: a held letter beside it is still a plain Send.
    expect(within(await rowOf('Testperson')).getByRole('button', { name: 'Send Testperson’s letter' })).toHaveTextContent(/^Send$/)
  })

  it('asks before sending it again -- and the sign-in it waits for is said inside the dialog (B1)', async () => {
    server.use(http.post('*/api/acb/runs/:id/letters/:letterId/send', () => HttpResponse.json({
      status: 403, code: 'IDENTITY_REQUIRED', details: {},
      message: 'Sending a letter to a member waits until administrators sign in to this system. Nothing has been sent.',
    }, { status: 403 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(within(await rowOf('Failperson')).getByRole('button', { name: /again$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Send this letter again?' })
    await user.click(within(dialog).getByRole('button', { name: 'Send again' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/The letter was not sent .*sign in/)
  })
})

describe('R4-5: the held letters', () => {
  it('lists each held letter; one with no address yet cannot be sent, and says so', async () => {
    renderWithProviders(<AcbDebits />)
    const waiting = await rowOf('Sampleton')
    expect(within(waiting).getByText('Return debit')).toBeInTheDocument()
    expect(within(waiting).getByText('No email address on record')).toBeInTheDocument()
    expect(within(waiting).getByRole('button', { name: /^Send/ })).toBeDisabled()
    expect(within(waiting).getByText('No address yet')).toBeInTheDocument()
    expect(within(await rowOf('Testperson')).getByRole('button', { name: /^Send/ })).toBeEnabled()
  })

  it('marking handled needs a note of how, and records it', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(within(await rowOf('Sampleton')).getByRole('button', { name: /Mark .* as handled/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Mark this letter handled' })
    const mark = within(dialog).getByRole('button', { name: 'Mark handled' })
    expect(mark).toBeDisabled()
    expect(mark).toHaveAccessibleDescription('Say how the letter was dealt with first.')

    await user.type(within(dialog).getByLabelText('How it was dealt with'), 'Posted by hand on 21 September')
    await user.click(mark)

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(writes).toEqual([{ action: 'handled', run: '40', letter: '22', note: 'Posted by hand on 21 September' }])
    expect(await screen.findByText(/Sampleton’s letter is marked as dealt with by hand/)).toBeInTheDocument()
    await waitFor(() => expect(letterReads).toBe(2))
  })

  it('a send the server refuses is said inside the dialog -- today, the sign-in it waits for (B1)', async () => {
    server.use(http.post('*/api/acb/runs/:id/letters/:letterId/send', () => HttpResponse.json({
      status: 403, code: 'IDENTITY_REQUIRED', details: {},
      message: 'Sending a letter to a member waits until administrators sign in to this system. Nothing has been sent.',
    }, { status: 403 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(within(await rowOf('Testperson')).getByRole('button', { name: /^Send/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Send this letter?' })
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(/The letter was not sent/)
    expect(alert).toHaveTextContent(/waits until administrators sign in/)
    expect(screen.queryByText(/letter was sent\./)).not.toBeInTheDocument()
  })

  it('🔒 a send whose answer never came is not repeated: no second Send in the dialog, and closing re-reads', async () => {
    let sends = 0
    server.use(http.post('*/api/acb/runs/:id/letters/:letterId/send', () => { sends += 1; return HttpResponse.error() }))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(within(await rowOf('Testperson')).getByRole('button', { name: /^Send/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Send this letter?' })
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/The letter may have gone .* Do not send it again/)
    expect(within(dialog).queryByRole('button', { name: 'Send' })).not.toBeInTheDocument()
    await user.click(within(dialog).getAllByRole('button', { name: 'Close' }).at(-1))
    await waitFor(() => expect(letterReads).toBe(2))
    expect(sends).toBe(1)
  })

  it('the must-pass: a send that is answered closes the dialog, says so, and reads the list again', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(within(await rowOf('Testperson')).getByRole('button', { name: /^Send/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Send this letter?' })
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(writes).toEqual([{ action: 'send', run: '40', letter: '21' }])
    expect(await screen.findByText('Testperson’s letter was sent.')).toBeInTheDocument()
    await waitFor(() => expect(letterReads).toBe(2))
  })
})
