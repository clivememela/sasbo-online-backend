// @vitest-environment jsdom -- the Debits screen's member search is the server's, paged (R-SEC-B, R-PERF-D)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbDebits from './AcbDebits'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * 🔒 `04.1`, R-SEC-B, R-PERF-D. The screen called `memberService.getMembers()` on mount and filtered
 * in the browser: every member's banking fields, shipped to find one -- 65,000 of them in production.
 * Now the question goes to `POST /api/acb/members/search` with a page size, and only what was asked
 * for comes back. The identity number travels in the body, never in a URL.
 */

const member = (id) => ({
  id, membershipNumber: `SYN-${String(id).padStart(4, '0')}`, title: 'Ms', initials: 'T', surname: 'Testperson',
  bankName: 'Synthetic Bank', accountLastFour: '0001', debitDay: 25, status: 'Active',
})

let listedEveryone
let searches

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  listedEveryone = false
  searches = []
  server.use(
    // The old way in. Nothing on this screen may ask for it.
    http.get('*/api/members', () => { listedEveryone = true; return HttpResponse.json([]) }),
    http.post('*/api/acb/members/search', async ({ request }) => {
      const body = await request.json()
      searches.push({ url: request.url, body })
      const all = Array.from({ length: 30 }, (_, i) => member(i + 1))
      const content = all.slice(body.page * body.size, body.page * body.size + body.size)
      return HttpResponse.json({ content, page: body.page, size: body.size, totalElements: all.length, totalPages: 2 })
    }),
    ...pageFixtures,
  )
})

describe('AcbDebits, the member search', () => {
  it('🔒 asks the server, with the query and a page size in the body -- and never lists every member', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.type(screen.getByLabelText('Surname'), 'Testperson')
    await user.type(screen.getByLabelText('Identity number'), '9001010000080')
    await user.click(screen.getByRole('button', { name: /^Search$/ }))

    expect(await screen.findByText('30 members found')).toBeInTheDocument()
    expect(searches).toHaveLength(1)
    expect(searches[0].body).toEqual({
      surname: 'Testperson', membershipNumber: '', identityNumber: '9001010000080', page: 0, size: 25,
    })
    expect(searches[0].url).not.toContain('9001010000080')
    expect(listedEveryone).toBe(false)
  })

  it('the count is the server\'s total, and the next page is asked of the server', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.type(screen.getByLabelText('Surname'), 'Testperson')
    await user.click(screen.getByRole('button', { name: /^Search$/ }))
    expect(await screen.findByText('30 members found')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /load banking details/i })).toHaveLength(25)

    await user.click(screen.getByRole('button', { name: /next/i }))
    await waitFor(() => expect(searches).toHaveLength(2))
    expect(searches[1].body).toMatchObject({ surname: 'Testperson', page: 1, size: 25 })
    await waitFor(() => expect(screen.getAllByRole('button', { name: /load banking details/i })).toHaveLength(5))
  })

  it('🔒 a search with nothing in it asks nothing, and says what to type', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.click(screen.getByRole('button', { name: /^Search$/ }))

    expect(await screen.findByText(/Type a surname, a membership number or an identity number/)).toBeInTheDocument()
    expect(searches).toHaveLength(0)
    expect(screen.queryByText(/members? found/)).not.toBeInTheDocument()
  })

  it('a search that fails says so -- never "0 members found"', async () => {
    server.use(http.post('*/api/acb/members/search', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.type(screen.getByLabelText('Surname'), 'Testperson')
    await user.click(screen.getByRole('button', { name: /^Search$/ }))

    expect(await screen.findByText('We could not search the members.')).toBeInTheDocument()
    expect(screen.queryByText(/members? found/)).not.toBeInTheDocument()
  })

  it('the must-pass: a search that matches nobody says so, once the answer came', async () => {
    server.use(http.post('*/api/acb/members/search', () =>
      HttpResponse.json({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await user.type(screen.getByLabelText('Surname'), 'Nobodyhasthissurname')
    await user.click(screen.getByRole('button', { name: /^Search$/ }))

    expect(await screen.findByText('0 members found')).toBeInTheDocument()
    expect(screen.getByText('No members match your search.')).toBeInTheDocument()
  })
})
