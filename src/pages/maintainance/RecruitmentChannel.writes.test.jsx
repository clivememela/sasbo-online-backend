// @vitest-environment jsdom -- the recruiter-type screen's OTHER two writes: add, and enable/disable
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import RecruitmentChannel from './RecruitmentChannel'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 R3-F2, WIDENED 2026-09-18 BY P9's OWN REFACTOR REVIEW: the rule reached 3 of 11 write-failure sites.
 *
 * `writeMayHaveLanded` was applied to the record, ACB and this screen's DELETE, and the same false claim was left
 * standing beside them — here on **add** and on **enable/disable**, which said "That change could not be saved" after
 * a timeout or a lost connection, when the change may well have been saved. An administrator who believes it adds the
 * channel twice.
 *
 * The pairs, per write: a refusal still says plainly that nothing was saved; a lost answer says to check.
 */

const CHANNELS = [{ id: 1, name: 'Walk-in', enabled: true }]

const refused = (message) => () => HttpResponse.json({ status: 409, message }, { status: 409 })

async function addAChannel() {
  const user = setupUser()
  renderWithProviders(<RecruitmentChannel />)
  await user.click(await screen.findByRole('button', { name: /Add a channel/i }))
  await user.type(await screen.findByLabelText('New recruitment channel name'), 'Referral')
  await user.click(screen.getByRole('button', { name: /Add this recruitment channel/i }))
}

async function toggleTheFirst() {
  const user = setupUser()
  renderWithProviders(<RecruitmentChannel />)
  await user.click(await screen.findByRole('button', { name: /Walk-in is active/i }))
}

describe('Recruitment Channel, the writes that are not the delete', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/recruitment-channels', () => HttpResponse.json(CHANNELS)))
  })

  it('add: a lost answer does not claim the channel was not saved', async () => {
    server.use(http.post('*/api/recruitment-channels', () => HttpResponse.error()))
    await addAChannel()

    const notice = await screen.findByText(/could not be confirmed/i)
    expect(notice).toBeInTheDocument()
    expect(screen.queryByText(/could not be saved/i)).not.toBeInTheDocument()
  })

  it('add: the must-pass -- a refusal still says it was not saved, with the server’s reason', async () => {
    server.use(http.post('*/api/recruitment-channels', refused('That name is already in use.')))
    await addAChannel()

    expect(await screen.findByText(/was not saved/i)).toBeInTheDocument()
    expect(screen.getByText(/That name is already in use\./)).toBeInTheDocument()
  })

  it('enable/disable: a lost answer does not claim the change was not saved', async () => {
    server.use(http.put('*/api/recruitment-channels/:id', () => HttpResponse.error()))
    await toggleTheFirst()

    expect(await screen.findByText(/could not be confirmed/i)).toBeInTheDocument()
  })

  it('enable/disable: the must-pass -- a refusal says it was not saved', async () => {
    server.use(http.put('*/api/recruitment-channels/:id', refused('That channel is in use.')))
    await toggleTheFirst()

    expect(await screen.findByText(/was not saved/i)).toBeInTheDocument()
  })
})
