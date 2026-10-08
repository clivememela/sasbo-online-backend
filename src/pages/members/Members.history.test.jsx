// @vitest-environment jsdom -- renders the member record's strip and opens its History tab
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage, { RECORD_TABS, tabPlace } from './Members'
/*
 * 🔑 THE HISTORY TAB'S CHUNK IS IMPORTED HERE, AND THAT IS AN ANTI-FLAKE FIX RATHER THAN AN UNUSED IMPORT. `Members`
 * asks for it with `lazy(() => import(...))` when the tab is opened; a module already in the registry resolves on the
 * next microtask instead of on however busy the machine is (the old Home.figures.test.jsx, shell.drawer.test.jsx).
 */
import './MemberHistory.jsx'
import membersSource from './Members.jsx?raw'
import { monthEndBody } from '../../test/msw/monthEndExamples'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * THE RECORD'S STRIP AT SIX, AND THE HISTORY TAB BEHIND IT -- month end 03.6, the owner's approval of the mock on
 * 2026-09-30 ("proposed"):
 *
 *     Personal        Employment        Membership
 *     Recruitment      History          Structures
 *
 * Each column keeps its edge -- the left from the left, the right to the right, the middle centred -- so the four
 * corners stay where the owner put them on 2026-09-10 and Structures stays under Membership. Keyboard order follows
 * the eye. Five tabs stay exactly as they were: that arrangement is his too, and it is held here unchanged.
 */
const MEMBER = {
  id: 7,
  membershipNumber: 'M-900001',
  firstNames: 'Synthetic',
  surname: 'Testperson',
  initials: 'S',
  status: 'Active',
  paymentType: 'Payroll Deduction',
}

/** Today's five, in today's order, placed as the owner placed them on 2026-09-10. */
const FIVE = [
  'justify-self-start',
  'row-span-2 justify-self-center self-center',
  'justify-self-end',
  'justify-self-start',
  'col-start-3 justify-self-end',
]

const SIX = [
  { label: 'Personal', full: 'Personal Information', place: 'justify-self-start' },
  { label: 'Employment', full: 'Employment & Bank', place: 'justify-self-center' },
  { label: 'Membership', full: 'Membership & Subscriptions', place: 'justify-self-end' },
  { label: 'Recruitment', full: 'Recruitment Channel', place: 'justify-self-start' },
  { label: 'History', full: 'History of changes', place: 'justify-self-center' },
  { label: 'Structures', full: 'Sasbo Structures', place: 'justify-self-end' },
]

/** The history as the server answers it, and every read of it, page and size. */
const historyAsked = []
const historyAnswers = () => http.get('*/api/members/:id/history', ({ request, params }) => {
  const url = new URL(request.url)
  historyAsked.push(`${params.id} ${url.searchParams.get('page')}/${url.searchParams.get('size')}`)
  return HttpResponse.json(monthEndBody('GET /api/members/{id}/history'))
})

async function openRecord(user) {
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  return within(screen.getByTestId('member-panel'))
}

/** The strip's buttons, in the order the page holds them: the grid the History tab sits in. */
const stripButtons = (panel) => [...panel.getByRole('button', { name: 'History of changes' }).parentElement.children]

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  historyAsked.length = 0
  // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
  server.use(...pageFixtures)
  server.use(...registerHandlers([MEMBER]), historyAnswers())
})

describe('the record\'s strip', () => {
  it('at SIX: Personal · Employment · Membership over Recruitment · History · Structures -- each column keeping its edge', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecord(user)

    const buttons = stripButtons(panel)
    expect(buttons).toHaveLength(6)
    buttons.forEach((button, index) => {
      const { label, full, place } = SIX[index]
      // Shown short, named in full -- the History tab's name is "History of changes".
      expect(button).toHaveTextContent(`${label}${full}`)
      expect(within(button).getByText(label)).toHaveAttribute('aria-hidden', 'true')
      expect(within(button).getByText(full)).toHaveClass('sr-only')
      for (const utility of place.split(' ')) expect(button).toHaveClass(utility)
    })
    // At six the middle column holds two like the others: nothing spans, and nothing is pushed to a column.
    for (const button of buttons) {
      expect(button).not.toHaveClass('row-span-2')
      expect(button).not.toHaveClass('col-start-3')
    }
    expect(panel.getByRole('button', { name: 'History of changes' })).not.toHaveAttribute('aria-current')
  })

  it('at SIX the keyboard follows the eye: Personal, Employment, Membership, Recruitment, History, Structures', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecord(user)

    const buttons = stripButtons(panel)
    buttons[0].focus()
    const visited = [SIX[0].full]
    for (let i = 1; i < buttons.length; i += 1) {
      await user.tab()
      visited.push(SIX.find(({ full }) => document.activeElement.textContent.endsWith(full))?.full)
    }
    expect(visited).toEqual(SIX.map(({ full }) => full))
  })

  it('at FIVE the owner\'s arrangement of 2026-09-10 stands exactly as it was -- and no other count is arranged', () => {
    expect(tabPlace(5)).toEqual(FIVE)
    expect(tabPlace(6)).toEqual(SIX.map(({ place }) => place))
    expect(tabPlace(4)).toEqual([])
    expect(tabPlace(7)).toEqual([])
    // The record's five were these, in this order -- the keyboard's -- and History goes before Structures.
    expect(RECORD_TABS.filter(({ id }) => id !== 'history').map(({ id }) => id))
      .toEqual(['personal', 'employment', 'membership', 'representatives', 'structures'])
    expect(RECORD_TABS.map(({ id }) => id).indexOf('history')).toBe(4)
    expect(RECORD_TABS.find(({ id }) => id === 'history')).toEqual({ id: 'history', label: 'History', full: 'History of changes' })
  })
})

describe('the History tab behind it', () => {
  it('asks for nothing until it is opened -- then reads the member\'s first page, and shows the server\'s answer', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecord(user)
    await user.click(panel.getByRole('button', { name: 'Employment & Bank' }))
    expect(historyAsked).toEqual([])

    await user.click(panel.getByRole('button', { name: 'History of changes' }))
    expect(panel.getByRole('button', { name: 'History of changes' })).toHaveAttribute('aria-current', 'true')
    const sheet = await panel.findByRole('region', { name: 'History of changes, newest first' })
    await waitFor(() => expect(sheet).toHaveAttribute('data-history', 'ready'))
    expect(historyAsked).toEqual(['7 0/50'])
    expect(within(sheet).getAllByRole('listitem')).toHaveLength(2)
    expect(within(sheet).getByText('Cell Tel')).toBeInTheDocument()
    expect(sheet).toHaveTextContent('Captured as M-900001')
    expect(sheet).toHaveTextContent('Who: Not recorded')
  })

  it('is a chunk of its own: Members imports it lazily, and nowhere by a static import', () => {
    expect(membersSource).toMatch(/lazy\(\(\) => import\("\.\/MemberHistory\.jsx"\)\)/)
    expect(membersSource).not.toMatch(/^import\s[^;]*from\s+["']\.\/MemberHistory(\.jsx)?["']/m)
  })

  it('leaving the tab and coming back reads it again, from the first page', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecord(user)

    await user.click(panel.getByRole('button', { name: 'History of changes' }))
    await waitFor(() => expect(panel.getByRole('region', { name: 'History of changes, newest first' }))
      .toHaveAttribute('data-history', 'ready'))
    await user.click(panel.getByRole('button', { name: 'Sasbo Structures' }))
    expect(panel.queryByRole('region', { name: 'History of changes, newest first' })).toBeNull()
    await user.click(panel.getByRole('button', { name: 'History of changes' }))
    await waitFor(() => expect(panel.getByRole('region', { name: 'History of changes, newest first' }))
      .toHaveAttribute('data-history', 'ready'))
    expect(historyAsked).toEqual(['7 0/50', '7 0/50'])
  })
})
