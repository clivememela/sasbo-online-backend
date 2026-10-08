// @vitest-environment jsdom -- Subscriptions: its first frame, a failed save, and a bank list that did not load
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'

import Subscriptions from './Subscriptions'
import { ThemeProvider } from '../../context/ThemeContext'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'

/** Staff who may change amounts: every case below edits a row, or waits for the Edit control as the list's arrival. */
const asStaff = (ui) => <TestAuthProvider permissions={[PERMISSIONS.SUBSCRIPTIONS_MAINTAIN]}>{ui}</TestAuthProvider>

/**
 * THREE STATES THIS SCREEN STATED WITHOUT CHECKING. `06.4`.
 *
 *   ① its first frame said "No subscriptions match your search." -- `loading` started false;
 *   ② a failed SAVE replaced the whole list with "We could not load the subscriptions.";
 *   ③ a bank list that failed was a sentence on the sheet, while the Bank field itself read as a
 *     filter with nothing in it.
 *
 * Each is paired with its must-pass, because each has an obvious wrong fix that satisfies it alone:
 * a screen that never shows its empty state, a save that never closes its row, a field that always
 * says it failed.
 */

const SUB = {
  id: 3,
  bankName: 'Synthetic Bank',
  acbDebitDay: 25,
  subscriptionAmount: 100,
  isCoreBanking: false,
  status: 'ACTIVE',
  registrationDate: '2026-01-15',
}
const EMPTY = /No subscriptions match your search/i
const boom = () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })

async function saveTheRow() {
  const user = setupUser()
  renderWithProviders(asStaff(<Subscriptions />))
  await user.click(await screen.findByRole('button', { name: 'Edit the subscription for Synthetic Bank' }))
  await user.click(screen.getByRole('button', { name: 'Save changes to Synthetic Bank' }))
}

describe('Subscriptions', () => {
  beforeEach(() => {
    // Failures are reported through the sink as well as shown.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/banks', () => HttpResponse.json([{ name: 'Synthetic Bank' }])))
  })

  describe('① the first frame', () => {
    it('is not "No subscriptions match" -- nothing has been asked yet', () => {
      // 🔑 SERVER-RENDERED ON PURPOSE. The defect lives in the frame BEFORE the mount effect, and a
      // testing-library render runs inside act(), which flushes that effect before any query can look.
      // Server rendering runs no effects at all, so it is exactly that frame.
      const html = renderToString(
        <MemoryRouter>
          <ThemeProvider>
            <Subscriptions />
          </ThemeProvider>
        </MemoryRouter>,
      )

      expect(html).not.toMatch(EMPTY)
      expect(html).toMatch(/Retrieving subscriptions/)
    })

    it('the must-pass: a list that genuinely is empty still says so', async () => {
      server.use(http.get('*/api/subscriptions', () => HttpResponse.json([])))
      renderWithProviders(asStaff(<Subscriptions />))

      expect(await screen.findByText(EMPTY)).toBeInTheDocument()
    })
  })

  describe('② a failed save', () => {
    it('keeps the list and says the change was not saved -- it is not a failed load', async () => {
      server.use(http.get('*/api/subscriptions', () => HttpResponse.json([SUB])), http.put('*/api/subscriptions/:id', boom))
      await saveTheRow()

      expect(await screen.findByText('The changes to Synthetic Bank were not saved.')).toBeInTheDocument()
      expect(screen.queryByText('We could not load the subscriptions.')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Save changes to Synthetic Bank' })).toBeInTheDocument()
    })

    it('the must-pass: a save that works closes the row and says nothing went wrong', async () => {
      server.use(
        http.get('*/api/subscriptions', () => HttpResponse.json([SUB])),
        http.put('*/api/subscriptions/:id', async ({ request }) => HttpResponse.json(await request.json())),
      )
      await saveTheRow()

      expect(await screen.findByRole('button', { name: 'Edit the subscription for Synthetic Bank' })).toBeInTheDocument()
      expect(screen.queryByText(/were not saved/i)).not.toBeInTheDocument()
    })
  })

  describe('③ the bank list', () => {
    it('that did not load says so AT the Bank field, with a retry', async () => {
      server.use(http.get('*/api/subscriptions', () => HttpResponse.json([SUB])), http.get('*/api/banks', boom))
      renderWithProviders(asStaff(<Subscriptions />))

      expect(await screen.findByText('Bank could not be loaded.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
    })

    it('the must-pass: a bank list that loaded says nothing of the kind', async () => {
      server.use(http.get('*/api/subscriptions', () => HttpResponse.json([SUB])))
      renderWithProviders(asStaff(<Subscriptions />))

      await screen.findByRole('button', { name: 'Edit the subscription for Synthetic Bank' })
      expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
    })
  })

  describe('④ the list itself, when it fails and is retried', () => {
    /*
     * 🔴 NAKED UNTIL `10.4`, AND `10.4` IS WHAT MOVED IT. The load's two resets --
     * `setLoading(true)` and `setError(null)` -- sat inside the effect, which
     * `react-hooks/set-state-in-effect` makes an error this phase, so they moved onto the Try again
     * control that bumps `attempt`. ①-③ above cover the first frame, a save and the BANK list; none
     * of them ever retried THE LIST. So the path that just changed had no witness at all.
     *
     * ⚠️ AND ONE OF THOSE TWO LINES IS LOAD-BEARING IN A WAY A RETRY TEST CAN EASILY MISS.
     * Nothing else ever clears `error`, so without `setError(null)` a retry that SUCCEEDS still
     * draws the failure banner -- above the list it has just loaded. A test that only checks the
     * rows arrive passes against exactly that bug. The second assertion below is the one that
     * holds it.
     */
    it('Try again reloads the list, and the failure it replaced is GONE, not left above the rows', async () => {
      const user = setupUser()
      server.use(http.get('*/api/subscriptions', boom))
      renderWithProviders(asStaff(<Subscriptions />))

      const retry = await screen.findByRole('button', { name: /try again/i })
      // Swapped BEFORE the press, never after: a handler changed after the click races the
      // request the click has already issued, which is a flake written on purpose.
      server.use(http.get('*/api/subscriptions', () => HttpResponse.json([SUB])))
      await user.click(retry)

      expect(await screen.findByRole('button', { name: 'Edit the subscription for Synthetic Bank' })).toBeInTheDocument()
      expect(screen.queryByText(/could not load the subscriptions/i)).not.toBeInTheDocument()
    })

    it('the must-pass: a list that fails says so, and does not claim to be empty', async () => {
      server.use(http.get('*/api/subscriptions', boom))
      renderWithProviders(asStaff(<Subscriptions />))

      expect(await screen.findByText(/could not load the subscriptions/i)).toBeInTheDocument()
      expect(screen.queryByText(EMPTY)).not.toBeInTheDocument()
    })
  })
})
