// @vitest-environment jsdom -- choosing a form type, which is the only thing this screen is for
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { Forms } from './Forms'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 R2-F5 — FIX-NOW, found during P9's execution, 2026-09-17: CHOOSING A FORM TYPE CRASHED THE WHOLE SCREEN.
 *
 * `SearchableSelect` reports a choice as `onChange(value, option)` -- a VALUE, not an event; that is its contract and
 * every other consumer either takes the value or defends against both shapes. `Forms.jsx` passed it
 * `handleInputChange("formType")`, which reads `event.target.value`, so the moment anybody picked a form type React
 * threw `Cannot read properties of undefined (reading 'value')` and the route fell to the error boundary. The picker
 * is the only control on the page, so the entire Forms feature -- Membership Application, Branch Form, Location
 * Listing, Bulk Listing, General Form -- was unreachable in the built app.
 *
 * 🔑 HOW IT SURVIVED EVERY PASS, which is the finding behind the finding: the screen renders nothing until a type is
 * chosen, so the route smoke mounted it, the matrix photographed it and the contrast sweep measured it -- all in the
 * one state where the defect cannot happen. It was found the same hour a surface entry was added for General Form
 * (R4-F2's fix needed one), the first time anything drove this control. That is L2 with a crash behind it.
 */

const BANKS = [{ id: 1, bankName: 'ABSA BANK', branch: 'Sandton', randomAccessNumber: '94' }]

describe('the Forms picker', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(
      http.get('*/api/banks/branches', () => HttpResponse.json(BANKS)),
      http.get('*/api/banks*', () => HttpResponse.json(BANKS)),
      http.get('*/api/branches*', () => HttpResponse.json([])),
      http.get('*/api/areas*', () => HttpResponse.json([])),
    )
  })

  async function choose(label) {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await user.click(await screen.findByRole('combobox', { name: /^Form Type/ }))
    await user.click(await screen.findByRole('option', { name: label }))
  }

  it('renders the General Form when it is chosen, instead of falling over', async () => {
    await choose('General Form')

    expect(await screen.findByLabelText('Name of non-member 1')).toBeInTheDocument()
    expect(screen.queryByText(/could not be displayed/i)).not.toBeInTheDocument()
  })

  it('the must-pass: a different type renders ITS form, so the picker is wired and not hard-coded', async () => {
    await choose('Location Listing')

    expect(await screen.findByText(/SASBO Finance Union — Location Listing/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Name of non-member 1')).not.toBeInTheDocument()
    expect(screen.queryByText(/could not be displayed/i)).not.toBeInTheDocument()
  })
})
