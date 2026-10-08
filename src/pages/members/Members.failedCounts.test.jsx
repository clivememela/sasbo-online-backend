// @vitest-environment jsdom -- renders the member register with its list request failing
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerFails } from '../../test/msw/memberRegister'

/**
 * A47: THE FAILED REGISTER STILL COUNTS ZERO AROUND ITS OWN ERROR.
 *
 * A27 was retired by the design arc because the TABLE learned the difference between
 * "could not be reached" and "nothing matched" -- and it did, and `Members.states.test.jsx`
 * asserts it. What that file cannot see is the rest of the screen, because its empty-text
 * pattern is the TABLE's sentence, "No members match these criteria". The pager's sentence
 * is "No members match", and the figure strip above says "Total 0".
 *
 * 🔴 Found 2026-09-16 by the browser suite, with the API container genuinely stopped: the
 * table said "The register could not be reached", and beneath it the pager said "No members
 * match" while the figures read 0, 0, 0, 0. The confident wrong answer A27 was about, told
 * by two other parts of the same screen. (With the reference lists failing too, a banner
 * added "The register below is up to date" -- that half needs both failures and is the
 * browser suite's to assert.)
 *
 * ✅ RETIRED 2026-09-17 when main merged into the design arc's design/06-close: its K1 fix
 * (a95e7b9, 2026-09-14) already shows the figures as "not available" and the pager as "Could
 * not load members". The pin went red on that branch, as it said it would; it is deleted and
 * the standard-facing test un-skipped -- flipped, not repaired (BR-5).
 */
const failed = () =>
  registerFails(500)

/** The number the figure strip shows under a label, read from the definition list. */
const figure = (label) => screen.getByText(label, { selector: 'dt, dt *' }).closest('dt').nextElementSibling

describe('the member register when the member list cannot be fetched', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    server.use(failed())
  })

  it('nothing on the failed screen reports a count of members', async () => {
    renderWithProviders(<MembersPage />)
    expect(await screen.findByText('The register could not be reached')).toBeInTheDocument()

    // Written to the standard: an unknown is not a zero. The pager and the figures say
    // nothing, or say they are unavailable -- never that there are no members.
    expect(screen.queryByText('No members match')).not.toBeInTheDocument()
    expect(figure('Total')).not.toHaveTextContent(/^0$/)
  })
})
