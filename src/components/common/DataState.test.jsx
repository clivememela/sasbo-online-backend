// @vitest-environment jsdom -- renders the states and reads roles off them
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import DataState from './DataState'
import {
  READ_CEILING_MS,
  SLOW_NOTICE_MS,
  apiErrorFrom,
  networkError,
} from '../../services/parseApiError'

afterEach(() => {
  vi.useRealTimers()
})

/**
 * THE FOUR STATES, AND THE RULES THAT KEEP THEM APART. `06.1b`.
 *
 * conventions §8 and EH-D4 are one sentence each, and a sentence in a document is something
 * twenty-seven surfaces have to remember. These are the same rules as assertions against the
 * one component they all render through.
 */

const rejectionFor = (status) =>
  apiErrorFrom(
    new Response(JSON.stringify({ message: 'The server could not complete that request.' }), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )

describe('DataState', () => {
  it('an error NEVER carries the empty copy', async () => {
    // 🔴 THIS IS A27 IN ONE ASSERTION. A backend that is down must not tell an administrator
    // there are no members. The branches are mutually exclusive by construction rather than
    // by four conditions agreeing with each other, and this is what says so.
    render(<DataState state="error" error={await rejectionFor(500)} />)

    expect(screen.queryByText(/nothing here yet/i)).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('prefers the server’s own sentence over ours', async () => {
    // The server's message is written about THIS failure by the handler that saw it; ours is
    // a fallback for a rejection carrying nothing worth reading.
    render(<DataState state="error" error={await rejectionFor(500)} />)

    expect(screen.getByText('The server could not complete that request.')).toBeInTheDocument()
  })

  it('offers retry on a 5xx and NOT on a 4xx', async () => {
    // 🔑 `retryable` IS DERIVED ONCE, IN `parseApiError`, and this is the assertion that
    // stops a consumer re-deriving it. A 4xx is the server saying the request was wrong:
    // retrying it unchanged produces the same refusal, and a button that cannot work is
    // worse than none.
    const onRetry = vi.fn()

    const { unmount } = render(
      <DataState state="error" error={await rejectionFor(500)} onRetry={onRetry} />,
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    unmount()

    render(<DataState state="error" error={await rejectionFor(400)} onRetry={onRetry} />)
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('retries a failure that carries no `retryable` at all', async () => {
    // A thrown TypeError has no `retryable`. Treating the unknown case as un-retryable would
    // put a dead end in front of the failures we understand least.
    const onRetry = vi.fn()
    const user = userEvent.setup()

    render(<DataState state="error" error={new TypeError('boom')} onRetry={onRetry} />)
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('keeps codes OUT of the sentence and behind a details line', async () => {
    // EH-D12: what happened and what to do next, in the reading voice. `status` and `kind`
    // are for whoever is diagnosing it, not for the member trying to finish a capture --
    // so they are present, and they are behind a control someone has to open.
    render(<DataState state="error" error={await rejectionFor(500)} />)

    const heading = screen.getByText('The server could not complete that request.')
    expect(heading.textContent).not.toMatch(/500|http/i)

    // Still reachable, because a diagnostic nobody can get to is the same as none.
    expect(screen.getByText('HTTP 500')).toBeInTheDocument()
  })

  it('shows the offline icon for a network failure, read off `kind`', () => {
    // Read off the closed vocabulary, never off the message -- matching on the sentence is
    // how a copy edit silently turns an offline state off.
    const { container } = render(<DataState state="error" error={networkError(new TypeError('x'))} />)

    expect(container.querySelector('.lucide-wifi-off')).toBeInTheDocument()
  })

  it('announces politely while loading and interrupts on failure', () => {
    // Not interchangeable. A loading state is an update a screen reader should hear when it
    // gets there; a failure interrupts, because the user is about to act on a list that is
    // not what they think it is.
    const { unmount } = render(<DataState state="loading" />)
    expect(screen.getByRole('status')).toBeInTheDocument()
    unmount()

    render(<DataState state="partial" />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  describe('the 5s notice -- EH-D2', () => {
    it('says nothing different before the threshold', () => {
      // The must-pass half. "Taking longer than expected" is only useful because it is rare;
      // a component that said it immediately would be a component nobody reads.
      vi.useFakeTimers()
      render(<DataState state="loading" />)

      act(() => { vi.advanceTimersByTime(SLOW_NOTICE_MS - 1) })

      expect(screen.getByRole('status')).toHaveTextContent('Loading…')
      expect(screen.queryByText(/taking longer/i)).not.toBeInTheDocument()
    })

    it('changes what it says once the load is late', () => {
      // 🔴 A SPINNER STOPS COMMUNICATING AFTER ABOUT TWO SECONDS, and after that it is
      // indistinguishable from a hang -- which is A14's whole failure mode: an administrator
      // reads a permanent spinner as "slow" rather than "down", so nobody is called.
      vi.useFakeTimers()
      render(<DataState state="loading" />)

      act(() => { vi.advanceTimersByTime(SLOW_NOTICE_MS) })

      expect(screen.getByText('This is taking longer than expected.')).toBeInTheDocument()
      // Still a polite update, not an alarm: nothing has failed yet.
      expect(screen.getByRole('status')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('takes its threshold from the helper, so the notice cannot outlive the ceiling', () => {
      // 🔑 §16 puts the notice at 5s and the read ceiling at 10s, and the ORDER is the
      // contract: a notice at or after the deadline never renders at all. This asserts the
      // relationship rather than either number, so lowering the ceiling reddens it.
      expect(SLOW_NOTICE_MS).toBeLessThan(READ_CEILING_MS)
    })

    it('does not carry a stale "late" into the next attempt', () => {
      // ⚠️ RETRY MAKES loading -> error -> loading THE COMMON CYCLE, not an edge one. A
      // component that kept `slow` across it would announce "taking longer than expected"
      // about a request a few milliseconds old, every single time someone clicked retry.
      vi.useFakeTimers()
      const { rerender } = render(<DataState state="loading" />)
      act(() => { vi.advanceTimersByTime(SLOW_NOTICE_MS) })
      expect(screen.getByText('This is taking longer than expected.')).toBeInTheDocument()

      rerender(<DataState state="error" error={new TypeError('boom')} />)
      rerender(<DataState state="loading" />)

      expect(screen.getByRole('status')).toHaveTextContent('Loading…')
      expect(screen.queryByText(/taking longer/i)).not.toBeInTheDocument()
    })

    it('is not something a CALLER can claim', () => {
      // `slow` is this component's own business -- `loading`, later. A caller able to pass it
      // could assert a request is late with none in flight, and it would have slipped in
      // silently the day the copy was added if the guard read the copy table instead of the
      // public vocabulary.
      const quiet = vi.spyOn(console, 'error').mockImplementation(() => {})
      expect(() => render(<DataState state="slow" />)).toThrow(/Known states/)
      quiet.mockRestore()
    })
  })

  it('refuses an unknown state rather than falling back to one', () => {
    // 🔴 A TYPO THAT FELL BACK TO A DEFAULT WOULD DECIDE, SILENTLY, WHETHER A FAILED FETCH
    // RENDERS AS "NOTHING FOUND" -- which is the whole of A27. So it throws.
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<DataState state="failed" />)).toThrow(/Known states/)
    quiet.mockRestore()
  })
})
