// @vitest-environment jsdom -- Recruiters: each channel's figures and members are the server's, by the member's own channel (RC15)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { Recruiters } from './Recruiters'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'
import fixture from '../../test/fixtures/recruitment-channel-fixture.json'

/**
 * RC15 AT THE SCREEN. The table counted a channel's members by the member's RECRUITER id matched against the channel's
 * id, out of the whole register loaded through `GET /api/members`. It reads the server's figures now -- counted by the
 * member's own channel, from the fixture the server's test holds its SQL to -- and asks for a channel's members only
 * when its dialog opens, by a user who may see them. "Add Member" sets the member's channel through the channel's own
 * route; it used to write the channel's name into the member's area representative and change nothing it showed.
 */

const channelIds = Object.fromEntries(fixture.channels.map((name, i) => [name, i + 1]))
const SUMMARY = fixture.expected.all.map((c) => ({
  channelId: channelIds[c.name], name: c.name, enabled: true, members: c.members, banks: c.banks, branches: c.branches,
}))
const BEC = channelIds.BEC
const ROW = { id: 41, membershipNumber: 'T-0041', initials: 'S', surname: 'Candidate', bankName: 'Synthetic Bank', branch: 'Hill', status: 'Active' }
const pageOf = (...rows) => ({ content: rows, page: 0, size: 100, totalElements: rows.length, totalPages: 1 })

/** Every request the screen makes, as `METHOD path?query`. */
let requests
const record = ({ request }) => {
  const url = new URL(request.url)
  requests.push(`${request.method} ${url.pathname}${url.search}`)
}

async function rowOf(name) {
  const open = await screen.findByRole('button', { name: new RegExp(`^${name} — show the members recruited`) })
  return open.closest('tr')
}

async function openChannel(user, name) {
  await user.click(await screen.findByRole('button', { name: new RegExp(`^${name} — show the members recruited`) }))
  return within(await screen.findByRole('dialog', { name }))
}

const asStaff = (ui) => (
  <TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW, PERMISSIONS.MEMBERS_EDIT]}>{ui}</TestAuthProvider>
)

describe('Recruiters -- RC15', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    requests = []
    server.events.on('request:start', record)
    server.use(...pageFixtures)
    server.use(
      http.get('*/api/recruitment-channels/summary', () => HttpResponse.json(SUMMARY)),
      http.get('*/api/banks/branches', () => HttpResponse.json(fixture.banks)),
    )
  })

  afterEach(() => {
    server.events.removeListener('request:start', record)
  })

  it('🔴 the figures are the server\'s, by each member\'s own channel: RO two, BEC three with its branches, EORO none', async () => {
    renderWithProviders(<Recruiters />)

    expect((await rowOf('RO')).querySelector('td[data-col="count"]')).toHaveTextContent(/^2$/)
    const bec = await rowOf('BEC')
    expect(bec.querySelector('td[data-col="count"]')).toHaveTextContent(/^3$/)
    expect(bec.querySelector('td[data-col="branches"]')).toHaveTextContent('Harbour, Main Street')
    expect((await rowOf('EORO')).querySelector('td[data-col="count"]')).toHaveTextContent(/^0$/)
  })

  it('🔴 the screen asks for no member to draw its table -- not the whole register, not one', async () => {
    renderWithProviders(<Recruiters />)
    await rowOf('BEC')

    expect(requests).toContain('GET /api/recruitment-channels/summary')
    // The register itself -- `GET /api/members`, with or without a query. (Its reference lists, the structures and the
    // roles, live under /api/members/ too and name no member.)
    expect(requests.filter((r) => /^GET \/api\/members(\?|$)/.test(r)), 'GET /api/members, the register').toEqual([])
  })

  it('a channel\'s members are asked for when it opens, a page from the server, its filters sent as criteria', async () => {
    server.use(http.get(`*/api/recruitment-channels/${BEC}/members`, () => HttpResponse.json(pageOf(ROW))))
    const user = setupUser()
    renderWithProviders(asStaff(<Recruiters />))

    const dialog = await openChannel(user, 'BEC')
    expect(await dialog.findByRole('button', { name: /T-0041 — open the record for/ })).toBeInTheDocument()

    await user.type(dialog.getByLabelText('Name'), 'cand')
    await waitFor(() => expect(requests).toContain(`GET /api/recruitment-channels/${BEC}/members?page=0&size=100&name=cand`))
  })

  it('🔒 without MEMBERS_VIEW the dialog says what is needed, and asks for no member', async () => {
    const user = setupUser()
    renderWithProviders(<Recruiters />)

    const dialog = await openChannel(user, 'BEC')

    expect(dialog.getByText(/Sign in to see members/)).toBeInTheDocument()
    expect(dialog.queryByRole('button', { name: /Add Member/ }), 'adding needs MEMBERS_EDIT').toBeNull()
    // No list that names a member: not the channel's, not a picker's, not the register.
    expect(requests.filter((r) => /recruitment-channels\/\d+\/members|member-candidates|^GET \/api\/members(\?|$)/.test(r)))
      .toEqual([])
  })

  it('🔴 Add Member sets the member\'s channel through the channel\'s own route -- no record sent -- and the figures follow', async () => {
    let body = 'unread'
    server.use(
      http.get(`*/api/recruitment-channels/${BEC}/members`, () => HttpResponse.json(pageOf())),
      http.get('*/api/recruitment-channels/member-candidates', () => HttpResponse.json({ rows: [ROW], more: true })),
      http.put(`*/api/recruitment-channels/${BEC}/members/41`, async ({ request }) => {
        body = await request.text()
        return HttpResponse.json(ROW)
      }),
    )
    const user = setupUser()
    renderWithProviders(asStaff(<Recruiters />))

    const dialog = await openChannel(user, 'BEC')
    await user.click(dialog.getByRole('button', { name: /Add Member/ }))
    const picker = within(await screen.findByRole('dialog', { name: 'Add member to recruiter' }))
    expect(await picker.findByText(/More members match than are shown here/)).toBeInTheDocument()
    expect(requests).toContain(`GET /api/recruitment-channels/member-candidates?excludeChannel=${BEC}`)
    await user.click(await picker.findByRole('button', { name: 'Add S Candidate to this recruiter' }))

    expect(await screen.findByText('S Candidate was added to BEC.')).toBeInTheDocument()
    expect(body, 'the route names the member; no record travels').toBe('')
    expect(requests.filter((r) => r === 'GET /api/recruitment-channels/summary').length).toBeGreaterThan(1)
    expect(requests.filter((r) => r.startsWith('PUT /api/members')), 'the whole-record PUT').toEqual([])
  })

  it('Remove takes the member off the channel through its route, and says so', async () => {
    let removed = false
    server.use(
      http.get(`*/api/recruitment-channels/${BEC}/members`, () => HttpResponse.json(pageOf(ROW))),
      http.delete(`*/api/recruitment-channels/${BEC}/members/41`, () => {
        removed = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = setupUser()
    renderWithProviders(asStaff(<Recruiters />))

    const dialog = await openChannel(user, 'BEC')
    await user.click(await dialog.findByRole('button', { name: 'Remove S Candidate from this recruitment channel' }))
    const confirm = within(await screen.findByRole('dialog', { name: 'Remove this member from the recruiter?' }))
    await user.click(confirm.getByRole('button', { name: 'Remove from recruiter' }))

    expect(await screen.findByText('S Candidate was removed from BEC.')).toBeInTheDocument()
    expect(removed).toBe(true)
  })
})
