// @vitest-environment jsdom -- the register's "New this month" figure, as the server counts it
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * 🗣️ SASBO, 18 Sep 2026: "dashboard to capture total number of new debit orders captured for the month", and "new
 * members captured". The figure is the month's joiners -- Date Joined in SASBO's calendar month -- and its second line
 * is how many of them pay by debit order.
 *
 * 🔁 COUNTED BY THE SERVER SINCE THE REGISTER IS READ A PAGE AT A TIME (register row A10): the browser holds one page,
 * so the strip is `GET /api/members/register/overview`, counted over the caller's whole register. The rule -- this
 * month, not last month nor this month last year, a legacy "Payroll" not a debit order -- is the server's now, and is
 * proved there (`MemberRegisterServiceImplTest`, `MemberRegisterRepositoryTest`, `MemberRegisterIT`). What is held here
 * is the screen's half: it prints the server's figures in their places, and none at all where they were not read.
 *
 * Each figure is a different number, so a strip that printed one in another's place cannot pass.
 */
const MEMBERS = [1, 2, 3, 4, 5, 6].map((id) => ({
  id, membershipNumber: `M000${id}`, firstNames: 'Synthetic', surname: `Member${id}`, status: 'Active',
}))

const OVERVIEW = {
  total: 6, active: 6, pending: 0, suspended: 0, newThisMonth: 3, newDebitOrders: 1, updatedToday: 2,
  branches: [], asOf: '2026-09-18',
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

/** The figure's term and its readings, as a screen reader meets them. */
const figure = (label) => {
  const term = within(screen.getByTestId('figure-strip')).getByText(label)
  return term.closest('div')
}

/** A figure's readings, in order: the count, then its make-up. Read whole, so 3 cannot pass as part of 31. */
const readings = (label) => within(figure(label)).getAllByRole('definition').map((dd) => dd.textContent)

describe('the register\'s New this month figure', () => {
  it('🗣️ prints the month\'s joiners the server counted, and how many of them pay by debit order', async () => {
    server.use(...pageFixtures)
    server.use(...registerHandlers(MEMBERS, { overview: OVERVIEW }))
    renderWithProviders(<MembersPage />)
    await screen.findAllByText(/Member1\b/)

    await waitFor(() => expect(readings('New this month')).toEqual(['3', '1 by debit order']))
    // The must-pass: the same strip reads the whole register, so the month's figure is not the total.
    expect(readings('Total')).toEqual(['6'])
  })

  it('🔴 prints neither number while the figures cannot be read -- a figure nobody read is not a zero (A47, K1)', async () => {
    server.use(...pageFixtures)
    server.use(...registerHandlers(MEMBERS))
    // A later `use` goes in front: the figures are refused, the page is not.
    server.use(http.get('*/api/members/register/overview', () => HttpResponse.json({ status: 500 }, { status: 500 })))
    renderWithProviders(<MembersPage />)
    await screen.findAllByText(/Member1\b/)

    const newThisMonth = figure('New this month')
    await waitFor(() => expect(within(newThisMonth).getByText('not available')).toBeInTheDocument())
    expect(newThisMonth).not.toHaveTextContent(/\d/)
  })
})
