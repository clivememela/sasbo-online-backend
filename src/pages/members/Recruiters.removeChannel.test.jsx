// @vitest-environment jsdom -- Recruiters: removing removes the recruitment channel it listed, and says so (A49, MO21)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { Recruiters } from './Recruiters'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * REGISTER A49, CORRECTED ON THE OWNER'S DECISION OF 2026-10-01 (MO21) -- AND NAMED FOR WHAT IT DOES (6 Oct).
 *
 * The removal used to PUT every member it matched -- by a REPRESENTATIVE's id compared with the CHANNEL's -- as
 * `Inactive`, a status no list knows, and then DELETE the representative that happened to share the channel's number.
 * Since MO21 it removes the channel it listed, by one request the server does whole, and the members recruited through
 * it keep their status; only their channel is cleared.
 *
 * 🔴 THE MANUAL'S READING OF EVERY SCREEN (6 Oct): the action was labelled *Remove recruiter* -- a person -- while it
 * deletes a whole recruitment channel, which Capture Member then no longer offers. What removal does is MO21's; the
 * label, the question and its answer now name the channel and every consequence. The count is the server's (RC15).
 */

const CHANNEL = { channelId: 5, name: 'Synthetic Channel', enabled: true, members: 2, banks: [], branches: [] }

/** The writes the screen makes, in order, as `VERB what id`. */
function recordWrites(deleteAnswer = () => new HttpResponse(null, { status: 204 })) {
  const writes = []
  server.use(
    http.get('*/api/recruitment-channels/summary', () => HttpResponse.json([CHANNEL])),
    http.delete('*/api/recruitment-channels/:id', ({ params }) => {
      writes.push(`DELETE channel ${params.id}`)
      return deleteAnswer()
    }),
    http.delete('*/api/representatives/:id', ({ params }) => {
      writes.push(`DELETE representative ${params.id}`)
      return new HttpResponse(null, { status: 204 })
    }),
    http.put('*/api/members/:id', ({ params }) => {
      writes.push(`PUT member ${params.id}`)
      return HttpResponse.json({})
    }),
  )
  return writes
}

async function askToRemove(user) {
  const [open] = await screen.findAllByRole('button', { name: /Synthetic Channel — show the members recruited/ })
  await user.click(open)
  await user.click(await screen.findByRole('button', { name: /Remove recruitment channel/ }))
  return screen.findByRole('dialog', { name: 'Remove this recruitment channel?' })
}

describe('Recruiters — removing a recruitment channel (A49, MO21)', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
  })

  it('🔴 is named for what it removes, and says every consequence: off the list, off Capture Member, members kept', async () => {
    recordWrites()
    const user = setupUser()
    renderWithProviders(<Recruiters />)

    expect(screen.queryByRole('button', { name: /Remove recruiter$/ }), 'no action named for a person').toBeNull()
    const dialog = await askToRemove(user)

    expect(dialog).toHaveTextContent(
      'Synthetic Channel will be removed from the recruitment channels, and Capture Member will no longer offer it. '
      + '2 member(s) recruited through it keep their status; only their recruitment channel is cleared.')
    expect(dialog, 'no status is written, least of all one no list knows').not.toHaveTextContent(/Inactive/)
  })

  it('🔒 removes the channel it listed by one request -- no member written, no representative deleted', async () => {
    const writes = recordWrites()
    const user = setupUser()
    renderWithProviders(<Recruiters />)

    const dialog = await askToRemove(user)
    await user.click(within(dialog).getByRole('button', { name: 'Remove recruitment channel' }))

    await waitFor(() => expect(writes).toEqual(['DELETE channel 5']))
    expect(await screen.findByText(
      'Synthetic Channel was removed. Its members keep their status; only their recruitment channel was cleared.')).toBeInTheDocument()
  })

  it('the must-pass: a removal the server refused says it was not saved -- and nothing else was written', async () => {
    const writes = recordWrites(() => HttpResponse.json({ status: 409, message: 'In use elsewhere.' }, { status: 409 }))
    const user = setupUser()
    renderWithProviders(<Recruiters />)

    const dialog = await askToRemove(user)
    await user.click(within(dialog).getByRole('button', { name: 'Remove recruitment channel' }))

    expect(await screen.findByText(/Synthetic Channel's removal was not saved\./)).toBeInTheDocument()
    expect(writes).toEqual(['DELETE channel 5'])
  })
})
