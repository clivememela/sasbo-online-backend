// @vitest-environment jsdom -- a hook with effects needs a host to render into
import { describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'

import useAsyncOptions from './useAsyncOptions'
import { networkError } from '../services/parseApiError'
import { deferred } from '../test/deferred'

/** A promise whose settlement this test controls, so "out of order" is arranged, not raced. */

function Probe({ fetcher, query, enabled }) {
  const { options, loading, error, isEmpty } = useAsyncOptions(fetcher, [query], { enabled })
  return (
    <div>
      <span data-testid="state">
        {loading ? 'loading' : error ? 'error' : isEmpty ? 'empty' : 'loaded'}
      </span>
      <span data-testid="options">{options.join(',')}</span>
    </div>
  )
}

describe('useAsyncOptions — the out-of-order guarantee', () => {
  /**
   * 🔴 THE TEST THE PLAN NAMES, and the defect it stands for is silent. Typing "Nedb"
   * leaves two requests in flight. If the slower "Ned" answer lands SECOND, the list on
   * screen is the answer to a query the user has already moved past. Nothing errors. The
   * options are real options — they are just the wrong ones, and the only symptom is a
   * user reporting that the dropdown "sometimes shows the wrong thing".
   */
  it('a stale answer that lands LAST does not overwrite the newer one', async () => {
    const first = deferred()
    const second = deferred()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    function Host() {
      const [query, setQuery] = useState('Ned')
      return (
        <>
          <button type="button" onClick={() => setQuery('Nedb')}>
            type more
          </button>
          <Probe fetcher={fetcher} query={query} enabled />
        </>
      )
    }

    render(<Host />)
    expect(screen.getByTestId('state')).toHaveTextContent('loading')

    // The second query starts while the first is still in flight.
    act(() => {
      screen.getByRole('button', { name: 'type more' }).click()
    })
    expect(fetcher).toHaveBeenCalledTimes(2)

    // The NEWER request answers first...
    await act(async () => {
      second.resolve(['Nedbank'])
    })
    await waitFor(() => expect(screen.getByTestId('options')).toHaveTextContent('Nedbank'))

    // ...and the OLDER one answers afterwards. This is the moment the defect happens.
    await act(async () => {
      first.resolve(['Nedbank', 'Nedcor', 'Ned Investments'])
    })

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('loaded'))
    expect(
      screen.getByTestId('options').textContent,
      'A superseded request overwrote a newer answer. The list on screen is now the ' +
        'result of a query the user has already moved past — and nothing errored, which ' +
        'is why only a sequenced hook can catch it.',
    ).toBe('Nedbank')
  })

  it('aborts the superseded request as well as ignoring it', async () => {
    const signals = []
    const fetcher = vi.fn((signal) => {
      signals.push(signal)
      return new Promise(() => {})
    })

    function Host() {
      const [query, setQuery] = useState('a')
      return (
        <>
          <button type="button" onClick={() => setQuery('ab')}>
            type more
          </button>
          <Probe fetcher={fetcher} query={query} enabled />
        </>
      )
    }

    render(<Host />)
    act(() => {
      screen.getByRole('button', { name: 'type more' }).click()
    })

    // Both halves matter: the ticket stops the WRITE, the abort stops the NETWORK. A hook
    // that only did the first would leave every superseded request running to completion.
    expect(signals).toHaveLength(2)
    expect(signals[0].aborted).toBe(true)
    expect(signals[1].aborted).toBe(false)
  })
})

describe('useAsyncOptions — the four states, kept apart', () => {
  it('an abort is NOT reported as a failure', async () => {
    /*
     * 🔴 THIS TEST USED TO BUILD ITS OWN ABORT -- `new Error(...)` with `name` set to
     * `'AbortError'` -- AND THAT IS WHY IT WENT ON PASSING OVER A DEAD BRANCH.
     *
     * `06.1a` put every service on `requestJson`, which catches the DOM's `AbortError` at
     * the boundary and rejects with an ordinary `Error` carrying `kind: 'abort'`. Its
     * `name` is `"Error"`. So from that commit the hook's `error.name === 'AbortError'`
     * guard could never fire, every superseded keystroke would have rendered "we could
     * not load the banks" under a field the user was still typing into -- and this test
     * stayed green, because it was feeding the hook a shape the application had stopped
     * producing.
     *
     * ⚠️ SO IT IS BUILT BY THE HELPER NOW, not by hand. A test that constructs its
     * subject's input from a remembered contract cannot notice the contract changing;
     * one that constructs it from the code that defines the contract goes red the day
     * the vocabulary moves. This is the same class as the vacuous-scan rule and it is
     * why `06.1b` found it at all: the hook had zero consumers, so nothing else could.
     */
    const error = networkError(new DOMException('superseded', 'AbortError'))
    expect(error.kind, 'the helper no longer produces `abort`; this test is now vacuous').toBe(
      'abort',
    )
    expect(error.name, 'if this is AbortError again, the old guard is live and this is moot').toBe(
      'Error',
    )

    const fetcher = vi.fn().mockRejectedValue(error)

    render(<Probe fetcher={fetcher} query="x" enabled />)

    // ⚠️ `06.1a`'s vocabulary: an abort is not a failure. Rendering one as an error puts
    // "we could not load the banks" under a field the user is still happily typing into.
    await waitFor(() => expect(fetcher).toHaveBeenCalled())
    expect(screen.getByTestId('state')).toHaveTextContent('loading')
  })

  it('a genuine failure is an error, and is NEVER the empty state', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('500'))
    render(<Probe fetcher={fetcher} query="x" enabled />)

    // 🔴 A27 at the hook layer. "The backend is down" and "there are no banks" are
    // different facts and must not collapse into one state.
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('error'))
    expect(screen.getByTestId('state')).not.toHaveTextContent('empty')
  })

  it('a genuinely empty result is `empty`, not `loaded` and not an error', async () => {
    const fetcher = vi.fn().mockResolvedValue([])
    render(<Probe fetcher={fetcher} query="x" enabled />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('empty'))
  })

  it('disabled: does not fetch at all, and does not sit on a spinner forever', async () => {
    const fetcher = vi.fn()
    render(<Probe fetcher={fetcher} query="x" enabled={false} />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('empty'))
    expect(
      fetcher,
      'A dependent field with no parent selected fetched anyway. That is a request per ' +
        'render for a list nobody can use yet.',
    ).not.toHaveBeenCalled()
  })

  it('a non-array result is normalised rather than crashing the consumer', async () => {
    // A backend that answers `{ content: [...] }` instead of a bare array is a real shape
    // in this codebase. `.map` on that throws inside the render of whichever select
    // consumed it, which is a blank screen rather than a bad list.
    const fetcher = vi.fn().mockResolvedValue({ content: ['Absa'] })
    render(<Probe fetcher={fetcher} query="x" enabled />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('empty'))
  })
})

describe('useAsyncOptions — what `10.4` rebuilt, witnessed', () => {
  /*
   * 🔴 THE HOOK WAS REBUILT IN `10.4`, AND THESE ARE THE PARTS THE CONTRACT ABOVE DID NOT LOOK AT.
   *
   * `react-hooks/set-state-in-effect` made the old shape an error: it flipped `{ loading: true }` on the way INTO
   * the fetch effect. It now keys each answer by request and DERIVES `loading`. The tests above cover the ticket, the
   * abort and the four states; three behaviours of the old shape were covered by nothing, and each is a way the
   * rebuild could have been wrong while every existing test stayed green:
   *
   *   ① a RELOAD keeps the options it already has while it waits (the old `{ ...current, loading: true }`);
   *   ② DISABLING empties the options, so re-enabling never flashes a list from before;
   *   ③ a fetcher that THROWS before returning a promise becomes the error state (the old try/catch, now a Promise
   *      executor -- which still calls the fetcher synchronously).
   */
  function Harness({ fetcher }) {
    const [query, setQuery] = useState('a')
    const [enabled, setEnabled] = useState(true)
    return (
      <>
        <button type="button" onClick={() => setQuery('b')}>next query</button>
        <button type="button" onClick={() => setEnabled((e) => !e)}>toggle</button>
        <Probe fetcher={fetcher} query={query} enabled={enabled} />
      </>
    )
  }

  it('① a reload keeps the options it already has while the new answer is on its way', async () => {
    const second = deferred()
    const fetcher = vi.fn().mockResolvedValueOnce(['Absa']).mockReturnValueOnce(second.promise)
    render(<Harness fetcher={fetcher} />)
    await waitFor(() => expect(screen.getByTestId('options')).toHaveTextContent('Absa'))

    act(() => screen.getByRole('button', { name: 'next query' }).click())

    expect(screen.getByTestId('state')).toHaveTextContent('loading')
    expect(screen.getByTestId('options')).toHaveTextContent('Absa')
    await act(async () => second.resolve(['Capitec']))
    expect(screen.getByTestId('options')).toHaveTextContent('Capitec')
  })

  it('② disabling empties the options, and re-enabling does not flash the old list', async () => {
    const again = deferred()
    const fetcher = vi.fn().mockResolvedValueOnce(['Absa']).mockReturnValueOnce(again.promise)
    render(<Harness fetcher={fetcher} />)
    await waitFor(() => expect(screen.getByTestId('options')).toHaveTextContent('Absa'))

    act(() => screen.getByRole('button', { name: 'toggle' }).click())
    expect(screen.getByTestId('options')).toBeEmptyDOMElement()

    act(() => screen.getByRole('button', { name: 'toggle' }).click())
    expect(screen.getByTestId('state')).toHaveTextContent('loading')
    expect(screen.getByTestId('options'), 'the list from before disabling came back').toBeEmptyDOMElement()
    await act(async () => again.resolve(['Nedbank']))
  })

  it('③ a fetcher that throws before it returns a promise is the error state, not a crash', async () => {
    const fetcher = vi.fn(() => {
      throw new Error('bad input')
    })
    render(<Probe fetcher={fetcher} query="x" enabled />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('error'))
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
