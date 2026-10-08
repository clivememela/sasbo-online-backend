// @vitest-environment jsdom -- renders a real password field and presses its toggle
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import PasswordInput from './PasswordInput'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

const classesOf = (element) => element.className.split(/\s+/).filter(Boolean)
/** A Tailwind position utility, with or without variants (`absolute`, `md:sticky`...). */
const POSITION = /^(?:[\w-]+:)*(?:static|relative|absolute|fixed|sticky)$/

const renderField = (props = {}) =>
  renderWithProviders(<PasswordInput label="Password" value="an-invented-pass" onChange={() => {}} {...props} />)

describe('PasswordInput -- the "Show password" toggle', () => {
  it('is one button, always named "Show password", pressed while the text shows', async () => {
    const user = setupUser()
    renderField()
    const input = screen.getByLabelText(/^password/i)
    expect(input).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Show password' })).toHaveAttribute('aria-pressed', 'false')

    await user.click(screen.getByRole('button', { name: 'Show password' }))

    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Show password' })).toHaveAttribute('aria-pressed', 'true')
  })

  /**
   * 🔴 INSIDE THE FIELD, AT ITS RIGHT EDGE. The presentation session's /login capture (7 Oct) had the eye UNDER the
   * field, at its left: the button wore `touch-target` and `absolute` together, and `touch-target` is
   * `position: relative` (its 44px overlay needs it), emitted by Tailwind after `absolute` -- so it won, and the button
   * fell into the flow below the input. The mechanism is asserted here, as `TextInput`'s 44px rule is; jsdom lays nothing
   * out, and the compiled order was read from Tailwind itself when this was fixed.
   */
  it('sits inside the field at its right edge: a wrapper places it, because touch-target is position: relative', () => {
    renderField()
    const input = screen.getByLabelText(/^password/i)
    const toggle = screen.getByRole('button', { name: 'Show password' })

    expect(classesOf(toggle), 'The toggle lost its 44px target on a phone.').toContain('touch-target')
    expect(
      classesOf(toggle).filter((name) => POSITION.test(name)),
      'The toggle places itself. Beside `touch-target` (position: relative, emitted later) its own position loses, ' +
        'and the eye drops out of the field.',
    ).toEqual([])

    const wrapper = toggle.parentElement
    expect(classesOf(wrapper)).toEqual(expect.arrayContaining(['absolute', 'inset-y-0', 'right-1']))
    expect(wrapper.parentElement, 'The wrapper is not in the field\'s own box.').toContainElement(input)
    expect(classesOf(wrapper.parentElement)).toContain('relative')
    // The typed text stops short of the toggle: 44px of padding for a 36px button 4px from the edge.
    expect(classesOf(input)).toContain('pr-11')
  })

  it('passes axe, hidden and shown', async () => {
    const user = setupUser()
    const { container } = renderField()
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('button', { name: 'Show password' }))
    await expectNoA11yViolations(container)
  })
})
