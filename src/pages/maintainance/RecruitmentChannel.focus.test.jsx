// @vitest-environment jsdom -- where the caret goes when an inline editor is revealed
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import RecruitmentChannel from './RecruitmentChannel'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 WRITTEN BEFORE THE `autoFocus` ATTRIBUTES WERE TOUCHED, AND THAT ORDER IS THE POINT. `10.4`.
 *
 * `jsx-a11y/no-autofocus` is being flipped from `warn` to `error`, and this screen carries two of the four
 * `autoFocus` props in the application. **Both are on bare `<input>`s that no focus manager is watching** -- one in
 * the "add a channel" row revealed by a button, one in the rename cell revealed by the pencil. There is no Radix and
 * no dialog here, so DELETING THE ATTRIBUTE WOULD NOT MOVE THE FOCUS SOMEWHERE ELSE; it would leave the caret
 * NOWHERE. The user presses "Add a channel", starts typing, and nothing happens.
 *
 * 🔑 SO THESE TWO TESTS EXIST TO GO RED. They assert today's behaviour, which `autoFocus` currently provides; the
 * attribute is then removed and they must FAIL; and the ref-and-effect replacement must make them pass again. A test
 * written after the change would only have proved that the replacement does something, not that it does the same
 * thing. (The arc's rule: prove the guard by deleting what it guards.)
 *
 * ⚠️ AND THE RULE IS NOT WRONG HERE, WHICH IS WHY THE CODE MOVES RATHER THAN THE RULE. `no-autofocus` objects to the
 * DECLARATIVE attribute because it can fire on page load, stealing the caret from someone who never asked for it.
 * Focus moved in response to a user's own click is the thing it wants instead -- and it is what this screen means.
 * The replacement is `useRef` + an effect keyed on the revealing state, which is the shape `Sidebar.jsx`,
 * `AcbDebits.jsx` and this very file already use elsewhere.
 */

const CHANNELS = [
  { id: 1, name: 'Walk-in', enabled: true },
  { id: 2, name: 'Referral', enabled: true },
]

describe('Recruitment Channel, where the caret lands', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/recruitment-channels', () => HttpResponse.json(CHANNELS)))
  })

  it('revealing the add row puts the caret in it, so you can just type', async () => {
    const user = setupUser()
    renderWithProviders(<RecruitmentChannel />)

    await user.click(await screen.findByRole('button', { name: /Add a channel/i }))

    expect(await screen.findByLabelText('New recruitment channel name')).toHaveFocus()
  })

  it('revealing the rename cell puts the caret in it, on the row that was pressed', async () => {
    const user = setupUser()
    renderWithProviders(<RecruitmentChannel />)

    // The SECOND row, deliberately: a replacement that focuses "the first rename input on the
    // page" would pass against the first row and be wrong here.
    await user.click(await screen.findByRole('button', { name: 'Rename Referral' }))

    expect(await screen.findByLabelText('Rename Referral')).toHaveFocus()
  })

  it('the must-pass: nothing steals the caret before anything is revealed', async () => {
    renderWithProviders(<RecruitmentChannel />)
    await screen.findByText('Walk-in')

    // 🔑 THE HALF THAT KEEPS THE OTHER TWO HONEST. A replacement that focused its input on
    // every render -- or on mount -- would satisfy both tests above while reintroducing exactly
    // the defect `no-autofocus` exists to prevent: the caret taken from a user who never asked.
    expect(screen.queryByLabelText('New recruitment channel name')).not.toBeInTheDocument()
    expect(document.body).toHaveFocus()
  })
})
