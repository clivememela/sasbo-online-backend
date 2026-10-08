// @vitest-environment jsdom -- Capture Member's bank and recruiter fields when their lists fail
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * THE LISTS, WHERE THEY ARE CHOSEN. `06.4`.
 *
 * 🔴 When the bank or recruiter list failed, the form said so once, in a notice above its tabs. Bank Where
 * Employed and the Recruitment Channel pickers -- two tabs away from that notice -- read as pickers with nothing in
 * them, and a capture could go on without the bank or the recruiter that was simply never offered.
 *
 * The handlers go BEFORE the catch-all in one `use` call: msw answers with the first match, and the catch-all
 * would otherwise answer every one of them with an empty list (see `CaptureMember.jobGrade.test.jsx`).
 */

const boom = () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })

describe('Capture Member, when a list it offers failed', () => {
  beforeEach(() => {
    // The page reports the failure through the sink as well as showing it.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('says so at Bank Where Employed', async () => {
    server.use(http.get('*/api/banks', boom), ...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
    expect(await screen.findByText('Bank Where Employed could not be loaded.')).toBeInTheDocument()
  })

  /**
   * 🔑 TWO PICKERS, TWO LISTS, TWO SEPARATE FAILURES -- and that is the point of the table below rather
   * than one case per field. The tab used to hold two pickers reading ONE endpoint, so a single failure
   * covered both and the test could not tell whether each field was consulting its own source. Recruiter
   * Type reads the recruitment channels and Recruiter reads `/api/recruiters`. Driven as a table so that
   * failing one list and asserting the OTHER STAYS QUIET is what proves the wiring, not just the message.
   *
   * 🗣️ Area Recruited left this table on 18 Sep 2026: SASBO asked for it to be read off the recruiter rather
   * than chosen, so it has no list of its own to fail -- the case after the table holds that.
   */
  it.each([
    ['Recruiter Type', '*/api/recruitment-channels'],
    ['Recruiter', '*/api/recruiters'],
  ])('says so at %s, and only there', async (label, endpoint) => {
    server.use(http.get(endpoint, boom), ...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: /^Recruitment Channel/ }))
    expect(await screen.findByText(`${label} could not be loaded.`)).toBeInTheDocument()
    expect(
      screen.getAllByText(/could not be loaded/i),
      'one list failed; a second message means a field is reading a list that is not its own',
    ).toHaveLength(1)
  })

  it('🗣️ Area Recruited reads no list of its own any more: the suburbs failing says nothing on this tab', async () => {
    // The area is the recruiter's demarcation letter (SASBO, 18 Sep 2026), not a pick from `/api/areas`. If the tab
    // still read that list, failing it would put a message here.
    server.use(http.get('*/api/areas', boom), ...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: /^Recruitment Channel/ }))
    await screen.findByText(/^Area Recruited/)
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })

  it('⚠️ and the old shared endpoint no longer feeds this tab at all (S6/R5)', async () => {
    // The must-pass for 03.10: `/api/representatives` administers representatives and is Edit Member's.
    // If the capture screen still read it, failing it would put a message on this tab.
    server.use(http.get('*/api/representatives', boom), ...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: /^Recruitment Channel/ }))
    await screen.findByText(/^Recruiter Type/)
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })

  it('the must-pass: lists that loaded say nothing of the kind', async () => {
    server.use(http.get('*/api/banks', () => HttpResponse.json([{ name: 'Synthetic Bank' }])), ...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
    await screen.findByText(/^Bank Where Employed/)
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })
})
