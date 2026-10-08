// @vitest-environment jsdom -- drives the record of who did what over MSW, its filters included
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Audit from './Audit'
import { PERMISSIONS } from '../../auth/permissions'
import { expectNoA11yViolations } from '../../test/assertions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * *Maintenance → Who did what* (contracts/signin.md): the record, newest first, a page at a time, narrowed by who, what,
 * which, and the days between two dates. Every person and every entry is invented.
 *
 * 🔑 THE CALENDAR IS PINNED, ONLY THE CALENDAR: the date picker opens on today's month, so "choose the 1st" means a
 * different day each month. Only `Date` is faked -- the timers stay real, so the debounce and user-event run as written.
 */
const ENTRIES = [
  { id: 2, at: '2026-10-06T08:15:00Z', username: 'ada.synthetic', displayName: 'Ada Synthetic', action: 'USER_UPDATED', entityType: 'USER', entityId: '31', summary: 'Changed: position, scope' },
  { id: 1, at: '2026-10-05T07:30:00Z', username: 'ada.synthetic', displayName: 'Ada Synthetic', action: 'SIGN_IN', entityType: 'USER', entityId: '31', summary: 'Signed in' },
]
const page = (content) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })

/** `GET /api/audit`, remembering every query it was asked with. */
const answersAudit = (answer = () => HttpResponse.json(page(ENTRIES))) => {
  const asked = []
  server.use(http.get('*/api/audit', ({ request }) => {
    asked.push(Object.fromEntries(new URL(request.url).searchParams))
    return answer()
  }))
  return asked
}

const renderAudit = (permissions = [PERMISSIONS.AUDIT_VIEW]) =>
  renderWithProviders(<TestAuthProvider permissions={permissions}><Audit /></TestAuthProvider>)

const chooseDay = async (user, label, day) => {
  await user.click(screen.getByLabelText(new RegExp(`^${label}`)))
  await user.click(await screen.findByRole('gridcell', { name: new RegExp(`^${day}`) }))
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 9, 7, 10, 0) })
})

describe('the record of who did what', () => {
  it('shows each entry: when in Johannesburg, who, the action in words, what, and the summary', async () => {
    answersAudit()
    renderAudit()
    const row = (await screen.findByText('Changed: position, scope')).closest('tr')
    expect(row.querySelector('[data-col="at"]')).toHaveTextContent('06 Oct 2026, 10:15')
    expect(row.querySelector('[data-col="who"]')).toHaveTextContent('Ada Synthetic (ada.synthetic)')
    expect(row.querySelector('[data-col="action"]')).toHaveTextContent('User updated')
    expect(row.querySelector('[data-col="entity"]')).toHaveTextContent('USER 31')
  })

  it('narrows by who, once typing pauses, and by the days between two dates', async () => {
    const user = setupUser()
    const asked = answersAudit()
    renderAudit()
    await screen.findByText('Signed in')

    await user.type(screen.getByLabelText(/^Who/), 'ada.synthetic')
    await waitFor(() => expect(asked.at(-1)).toMatchObject({ username: 'ada.synthetic', page: '0', size: '50' }))

    await chooseDay(user, 'From', '01 Oct 2026')
    await chooseDay(user, 'To', '06 Oct 2026')
    await waitFor(() => expect(asked.at(-1)).toMatchObject({ username: 'ada.synthetic', from: '2026-10-01', to: '2026-10-06' }))
    expect(asked[0]).toEqual({ page: '0', size: '50' })
  })

  it('dates the wrong way round are said on the field, and nothing is asked for them', async () => {
    const user = setupUser()
    const asked = answersAudit()
    renderAudit()
    await screen.findByText('Signed in')
    await chooseDay(user, 'From', '06 Oct 2026')
    await waitFor(() => expect(asked.at(-1)).toMatchObject({ from: '2026-10-06' }))
    const before = asked.length

    await chooseDay(user, 'To', '01 Oct 2026')
    expect(await screen.findByText('The end date is before the start date.')).toBeInTheDocument()
    expect(screen.getByText('Those dates are the wrong way round.')).toBeInTheDocument()
    expect(asked.length).toBe(before)
  })

  it('a failed read says so, with a retry, and is never an empty record', async () => {
    answersAudit(() => HttpResponse.json({ message: 'boom' }, { status: 500 }))
    renderAudit()
    expect(await screen.findByText('The record did not load, so this is not an empty record.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByText('Nothing is recorded yet.')).not.toBeInTheDocument()
  })

  it('an empty record says so', async () => {
    answersAudit(() => HttpResponse.json(page([])))
    renderAudit()
    expect(await screen.findByText('Nothing is recorded yet.')).toBeInTheDocument()
  })

  it('is refused, and asks nothing, without AUDIT_VIEW', () => {
    renderAudit([PERMISSIONS.USERS_ADMIN])
    expect(screen.getByRole('status')).toHaveTextContent('You need permission to read the record of who did what.')
  })

  it('has no violations, with rows and with a date picker open', async () => {
    const user = setupUser()
    answersAudit()
    const { container } = renderAudit()
    await screen.findByText('Signed in')
    await expectNoA11yViolations(container)
    await user.click(screen.getByLabelText(/^From/))
    await screen.findByRole('gridcell', { name: /^07 Oct 2026/ })
    await expectNoA11yViolations(screen.getByRole('dialog'), { rules: { region: { enabled: false } } })
  })
})
