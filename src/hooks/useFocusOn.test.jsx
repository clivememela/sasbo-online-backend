// @vitest-environment jsdom -- the hook focuses a rendered element
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { act, render, screen } from '@testing-library/react'

import useFocusOn from './useFocusOn.js'

/**
 * The shared "a notice takes focus when it appears" rule (RF: six hand-written copies). Each case beside its
 * must-pass: a hook that focused on every render would pass the "takes focus" rows and fail the "leaves focus" ones.
 */
function Screen({ when }) {
  const [notice, setNotice] = useState(null)
  const ref = useFocusOn(notice, when?.(notice))
  return (
    <>
      <button type="button" onClick={() => setNotice({ text: 'It failed.' })}>Save</button>
      <input aria-label="Elsewhere" />
      {notice && <div ref={ref} tabIndex={-1} role="alert">{notice.text}</div>}
    </>
  )
}

describe('useFocusOn', () => {
  it('the notice takes focus once it EXISTS -- not before, from the handler', () => {
    render(<Screen />)
    act(() => screen.getByRole('button', { name: 'Save' }).click())
    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('a second identical failure moves focus back again -- keyed on the value, not a boolean', () => {
    render(<Screen />)
    act(() => screen.getByRole('button', { name: 'Save' }).click())
    act(() => screen.getByRole('textbox', { name: 'Elsewhere' }).focus())
    act(() => screen.getByRole('button', { name: 'Save' }).click())
    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('the must-pass: with the condition false, focus stays where the user is', () => {
    render(<Screen when={() => false} />)
    act(() => screen.getByRole('textbox', { name: 'Elsewhere' }).focus())
    act(() => screen.getByRole('button', { name: 'Save' }).click())
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Elsewhere' })).toHaveFocus()
  })
})
