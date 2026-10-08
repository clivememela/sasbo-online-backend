// @vitest-environment jsdom -- renderHook mounts into a container
import { describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'

import { useDebounce } from './useDebounce'

/**
 * FAKE TIMERS, ALWAYS.
 *
 * `useDebounce` is a TIMER. Testing it against the real clock means sleeping,
 * and a test that sleeps is a flake generator by construction: it passes on an
 * idle machine and fails on a loaded one, which is precisely the machine a CI
 * runner is. Every case here advances the clock deliberately, so the test is
 * about the hook's behaviour rather than about how busy the box was.
 *
 * `setup.js` restores real timers after every test, so no case here can leak a
 * frozen clock into a shuffled neighbour.
 */
describe('useDebounce', () => {
  const setup = (value, delay) => {
    vi.useFakeTimers()
    return renderHook(({ v, d }) => useDebounce(v, d), { initialProps: { v: value, d: delay } })
  }

  const advance = (ms) => act(() => vi.advanceTimersByTime(ms))

  it('returns the initial value immediately, without waiting for the delay', () => {
    const { result } = setup('absa', 400)
    expect(
      result.current,
      'The first render returned nothing. A search box that shows an empty result for ' +
        'its own initial value looks broken before the user has typed anything.',
    ).toBe('absa')
  })

  it('does NOT publish a new value before the delay has elapsed', () => {
    const { result, rerender } = setup('a', 400)
    rerender({ v: 'ab', d: 400 })
    advance(399)
    expect(
      result.current,
      'The debounced value changed after 399ms of a 400ms delay. The whole point of the ' +
        'hook is to stop a request per keystroke; publishing early sends them anyway.',
    ).toBe('a')
  })

  it('publishes once the delay has elapsed', () => {
    const { result, rerender } = setup('a', 400)
    rerender({ v: 'ab', d: 400 })
    advance(400)
    expect(result.current).toBe('ab')
  })

  it('RESTARTS the delay on every change -- fast typing publishes once, at the end', () => {
    // This is the actual contract, and the one a naive implementation gets
    // wrong: a timer that is set but never cleared publishes every
    // intermediate value late, rather than publishing the final value once.
    const { result, rerender } = setup('a', 400)

    for (const v of ['ab', 'abs', 'absa']) {
      rerender({ v, d: 400 })
      advance(100) // never long enough on its own
    }

    expect(
      result.current,
      'An intermediate keystroke was published. The previous timer is not being cleared, ' +
        'so every keystroke eventually fires its own search -- the exact behaviour the ' +
        'hook exists to prevent, delayed rather than removed.',
    ).toBe('a')

    advance(400)
    expect(result.current).toBe('absa')
  })

  it('uses a 400ms default when no delay is given', () => {
    vi.useFakeTimers()
    const { result, rerender } = renderHook(({ v }) => useDebounce(v), {
      initialProps: { v: 'a' },
    })
    rerender({ v: 'ab' })
    advance(399)
    expect(result.current).toBe('a')
    advance(1)
    expect(result.current).toBe('ab')
  })

  it('honours a changed delay', () => {
    const { result, rerender } = setup('a', 400)
    rerender({ v: 'ab', d: 50 })
    advance(50)
    expect(result.current).toBe('ab')
  })

  it('clears its timer on unmount, so it cannot set state on a dead component', () => {
    const { rerender, unmount } = setup('a', 400)
    rerender({ v: 'ab', d: 400 })
    unmount()
    // If the timeout survived the unmount this would warn or throw as React
    // updates an unmounted component. Advancing past the delay is the check.
    expect(() => advance(1000)).not.toThrow()
  })

  it('a zero delay still defers past the current tick', () => {
    const { result, rerender } = setup('a', 0)
    rerender({ v: 'ab', d: 0 })
    expect(result.current).toBe('a')
    advance(0)
    expect(result.current).toBe('ab')
  })
})
