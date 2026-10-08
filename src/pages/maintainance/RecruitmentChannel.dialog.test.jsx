// @vitest-environment jsdom -- the recruiter-type delete, whose confirmation stays open while its write runs
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import RecruitmentChannel from './RecruitmentChannel'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 R4-F1 + R2-F3 (P9's independent review) on the one screen that shows both.
 *
 *   ① A FAILED DELETE WAS ANNOUNCED BEHIND THE DIALOG. The confirmation stays open on a failure, and the failure was
 *     written to the PAGE's notice, under the scrim -- and that notice's focus effect pulled focus out of the dialog,
 *     which Radix's trap pulled straight back. The dialog in front of the user showed nothing.
 *   ② THE CONFIRMATION COULD BE CLOSED WHILE THE DELETE RAN.
 *
 * Must-passes: a delete that succeeds closes the dialog and leaves the page quiet; a refusal still says it was not
 * deleted; a delete whose answer never arrived does not claim either outcome (R3-F2's rule, which this copy follows).
 */

const CHANNELS = [
  { id: 1, name: 'Walk-in', enabled: true },
  { id: 2, name: 'Referral', enabled: true },
]

async function askToDelete() {
  const user = setupUser()
  renderWithProviders(<RecruitmentChannel />)
  await user.click(await screen.findByRole('button', { name: 'Delete Walk-in' }))
  const dialog = await screen.findByRole('dialog', { name: 'Delete recruiter type' })
  await user.click(within(dialog).getByRole('button', { name: 'Delete recruiter type' }))
  return { user, dialog }
}

describe('Recruitment Channel, deleting a recruiter type', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/recruitment-channels', () => HttpResponse.json(CHANNELS)))
  })

  it('① a refused delete is said INSIDE the dialog, not on the page behind it', async () => {
    server.use(
      http.delete('*/api/recruitment-channels/:id', () =>
        HttpResponse.json({ status: 409, message: 'Members still use this channel.' }, { status: 409 }),
      ),
    )
    const { dialog } = await askToDelete()

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent('Walk-in was not deleted. Members still use this channel.')
    expect(dialog).toContainElement(document.activeElement)
  })

  it('① a delete whose answer never arrived does not say it failed -- it says to check', async () => {
    server.use(http.delete('*/api/recruitment-channels/:id', () => HttpResponse.error()))
    const { dialog } = await askToDelete()

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(/could not be confirmed/i)
    expect(alert).not.toHaveTextContent(/not deleted/i)
  })

  it('② while the delete runs, Escape does not close the confirmation', async () => {
    server.use(http.delete('*/api/recruitment-channels/:id', () => new Promise(() => {})))
    const { user, dialog } = await askToDelete()

    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog', { name: 'Delete recruiter type' })).toBeInTheDocument()
  })

  it('the must-pass: a delete that succeeds closes the dialog and says nothing went wrong', async () => {
    let deleted = false
    server.use(
      http.delete('*/api/recruitment-channels/:id', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
      http.get('*/api/recruitment-channels', () => HttpResponse.json(deleted ? CHANNELS.slice(1) : CHANNELS)),
    )
    await askToDelete()

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(await screen.findByText('Referral')).toBeInTheDocument()
  })
})
