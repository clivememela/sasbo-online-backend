// @vitest-environment jsdom -- renders the capture form's Membership & Subscriptions tab
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * THE SASBO SUBSCRIPTION IS 174, AND THE FORM SAYS SO BEFORE ANYONE PICKS A BANK.
 *
 * 🗣️ The owner, repeatedly: "the amount must be 174", and then "You keep on giving me a blank field instead of
 * 174! Put 174!!!" -- the field sat empty until a bank was chosen. The subscription is the UNION's fee, set once
 * for everybody; the reference table has a row per bank only because the debit DAY differs. SASBO's own branch
 * report (31/07/26) confirms it: subs = members x R174 on all 5,513 workplaces.
 *
 * On main this field rendered `form.monthlySubscription`, which nothing ever filled -- so it was blank for every
 * member, bank or no bank.
 */

const rows = (...amounts) =>
  amounts.map((amount, i) => ({ id: i + 1, bankName: ['Absa Bank Limited', 'Capitec Bank Limited', 'Nedbank Limited'][i], subscriptionAmount: amount, debitDay: 25 }))

let posted

beforeEach(() => {
  posted = undefined
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(...pageFixtures)
  server.use(
    http.post('*/api/members', async ({ request }) => {
      posted = await request.json()
      return HttpResponse.json({ id: 1, ...posted })
    }),
  )
})

const openMembership = async (user) =>
  user.click(await screen.findByRole('button', { name: /Membership & Subscriptions/i }))

const subscriptionField = () => screen.getByLabelText(/^Monthly Subscription/)
/** The amount as the one formatter prints it (00.2, F33): rands, en-ZA's comma, two decimals. */
const RANDS_174 = /^R\s174,00$/

describe('Monthly Subscription on Capture Member', () => {
  it('🔴 shows R 174,00 with NO bank chosen, because every row agrees on it', async () => {
    server.use(http.get('*/api/subscriptions', () => HttpResponse.json(rows(174, 174, 174))))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openMembership(user)

    await waitFor(() => expect(subscriptionField().value).toMatch(RANDS_174))
    expect(subscriptionField(), 'it is set by the union, not typed').toBeDisabled()
  })

  it('and the captured member is saved with it', async () => {
    server.use(http.get('*/api/subscriptions', () => HttpResponse.json(rows(174, 174, 174))))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openMembership(user)
    await waitFor(() => expect(subscriptionField().value).toMatch(RANDS_174))

    await user.click(screen.getByRole('button', { name: /Personal Information/i }))
    const type = async (label, value) => {
      const f = screen.getByLabelText(new RegExp(`^${label}`))
      await user.clear(f)
      await user.type(f, value)
    }
    await type('First Name\\(s\\)', 'Synthetic')
    await type('Surname', 'Testperson')
    await type('Personal Email', 'synthetic@example.test')
    await user.selectOptions(screen.getByLabelText(/^Identity Type/), 'I.D. Number')
    await type('I.D. Number', '9001010000080')
    await user.click(screen.getByRole('button', { name: /Employment & Bank/i }))
    await user.selectOptions(screen.getByLabelText(/^Payment Type/), 'Payroll Deduction')
    await user.click(screen.getByRole('button', { name: /save member/i }))
    const sendBtn = await screen.findByRole('button', { name: /send welcome letter/i })
    await user.click(sendBtn)

    await waitFor(() => expect(posted).toBeDefined())
    // 🔒 00.2: a NUMBER on the wire, never the string the form shows -- "174,00" is a 400 on the server, and a
    // client that posted its display text would be a second source of the comma-decimal risk V40's report exists for.
    expect(posted.monthlySubscription).toBe(174)
  })

  it('does NOT guess when the rows disagree and no bank is chosen', async () => {
    // The must-pass half: a form that always showed the first row's amount would pass the tests above.
    server.use(http.get('*/api/subscriptions', () => HttpResponse.json(rows(174, 150, 174))))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openMembership(user)

    // Give the list time to arrive, then assert it stayed empty rather than asserting before it could fill.
    await screen.findByText(/reviewed annually/i)
    await new Promise((resolve) => setTimeout(resolve, 300))
    expect(subscriptionField()).toHaveValue('')
  })
})
