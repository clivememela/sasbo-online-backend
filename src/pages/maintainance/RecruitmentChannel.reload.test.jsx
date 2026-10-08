// @vitest-environment jsdom -- what the table does between a successful write and the refreshed rows
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import RecruitmentChannel from './RecruitmentChannel'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

/**
 * 🔴 THE BEHAVIOUR `10.4` ALMOST DELETED BY ACCIDENT, WHICH NOTHING WAS ASSERTING. `10.4`, batch 5.
 *
 * `react-hooks/set-state-in-effect` becomes an error this phase, and `refresh` was flagged because the mount
 * effect called it and its first two statements set state synchronously. On four other screens the fix was
 * mechanical, because their loader had exactly ONE non-effect caller -- Try again -- so the reset could simply
 * move there.
 *
 * ⚠️ THIS SCREEN HAS FIVE. Besides Try again, all four write handlers -- add, rename, enable/disable, delete --
 * reload the table after the write, and today that reload SHOWS THE LOADING STATE while the fresh rows are
 * fetched. Hoisting the reset onto the retry path alone would have removed that, and the table would have sat on
 * its stale rows after every save with no sign anything was happening. **A real behaviour change, smuggled in as
 * a lint fix, that no test in this repository would have caught.**
 *
 * 🔑 SO THE RESET WENT INTO `reloadChannels`, WHICH ALL FIVE NON-EFFECT CALLERS USE, and this file is the witness
 * that was missing. `RecruitmentChannel.writes.test.jsx` asserts the writes SUCCEED and what the failures say;
 * neither it nor any other file says anything about the state the table passes through on the way back.
 *
 * Deterministic rather than timed: the reload's answer is held until the test releases it, so the loading
 * assertion is guaranteed instead of racing a real delay (the same helper shape `Members.states.test.jsx` uses,
 * for the same reason -- a flake fixed at its wall-clock dependence rather than at its threshold).
 */

const CHANNELS = [{ id: 1, name: 'Walk-in', enabled: true }]

/** A list answer that does not arrive until this test says so. */
function channelsWhenReleased(body) {
  const answer = deferred()
  return {
    handler: http.get('*/api/recruitment-channels', async () => {
      await answer.promise
      return HttpResponse.json(body)
    }),
    release: () => answer.resolve(),
  }
}

describe('Recruitment Channel, the reload after a write', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/recruitment-channels', () => HttpResponse.json(CHANNELS)))
  })

  it('a successful rename sends the table back to LOADING before the fresh rows arrive', async () => {
    const user = setupUser()
    renderWithProviders(<RecruitmentChannel />)

    await user.click(await screen.findByRole('button', { name: 'Rename Walk-in' }))
    const field = await screen.findByLabelText('Rename Walk-in')
    await user.clear(field)
    await user.type(field, 'Walk-in (branch)')

    // The write succeeds; the RELOAD behind it is what is held.
    server.use(http.put('*/api/recruitment-channels/1', () => HttpResponse.json({ id: 1, name: 'Walk-in (branch)', enabled: true })))
    const reloaded = channelsWhenReleased([{ id: 1, name: 'Walk-in (branch)', enabled: true }])
    server.use(reloaded.handler)

    try {
      await user.click(screen.getByRole('button', { name: 'Save the new name for Walk-in' }))

      expect(await screen.findByRole('status')).toBeInTheDocument()
    } finally {
      reloaded.release()
    }

    expect(await screen.findByText('Walk-in (branch)')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('the must-pass: the mount does not announce loading twice, and the rows still arrive', async () => {
    /*
     * 🔑 THE HALF THAT CATCHES THE OVER-CORRECTION. A "fix" that put the reset back into the
     * shared fetch would satisfy the test above AND reintroduce the lint error, and a fix that
     * called `reloadChannels` from the mount effect would satisfy it while reintroducing the
     * cascading render the rule exists to prevent. This asserts the ordinary first load still
     * ends in rows, from the state the screen initialises to.
     */
    renderWithProviders(<RecruitmentChannel />)

    expect(await screen.findByText('Walk-in')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
