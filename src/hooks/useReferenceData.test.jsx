// @vitest-environment jsdom -- a hook with effects needs a host to render into
import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import useReferenceData from './useReferenceData'
import { apiErrorFrom, networkError } from '../services/parseApiError'
import { deferred } from '../test/deferred'

/**
 * THE FOURTH STATE. `06.1b`, EH-D9, and the DoD's first box.
 *
 * 🔑 **`partial` IS THE ONE THAT DOES NOT EXIST WITHOUT THE SET.** `loading`, `empty` and
 * `error` are properties of a single fetch. *"Three of four sources answered"* is not, and a
 * screen tracking four booleans has nowhere to put it — which is exactly how twelve reference
 * lists came to be loaded with `.catch(() => setThing([]))` across five screens.
 *
 * 🔴 **THE FAILURE THESE ASSERTIONS ARE ABOUT IS NOT A CRASH.** An administrator who cannot
 * find a member's bank in a silently-emptied dropdown does not conclude the backend is down;
 * they pick the closest bank they can see. The silent failure does not stop the work, it
 * corrupts the record.
 */

/** Renders the hook's whole return so each assertion reads one attribute. */
function Probe({ loaders }) {
  const refs = useReferenceData(loaders)
  return (
    <div>
      <span data-testid="state">{refs.state}</span>
      <span data-testid="failed">{refs.failed.join(',')}</span>
      <span data-testid="banks">{(refs.data.banks ?? []).join('|')}</span>
      <span data-testid="roles">{(refs.data.roles ?? []).join('|')}</span>
      <button type="button" onClick={refs.retry}>
        Try again
      </button>
    </div>
  )
}

/** Renders what `field(name)` hands a select, so each assertion reads one fact of it. */
function FieldProbe({ loaders }) {
  const refs = useReferenceData(loaders)
  const banks = refs.field('banks')
  const roles = refs.field('roles')
  return (
    <div>
      <span data-testid="state">{refs.state}</span>
      <span data-testid="banks-options">{banks.options.join('|')}</span>
      <span data-testid="banks-error">{banks.loadError ? 'failed' : 'none'}</span>
      <span data-testid="roles-error">{roles.loadError ? 'failed' : 'none'}</span>
      <span data-testid="roles-retryable">{String(roles.loadError?.retryable)}</span>
      <button type="button" onClick={roles.onRetryLoad}>
        Retry roles
      </button>
    </div>
  )
}

const http500 = () =>
  apiErrorFrom(
    new Response(JSON.stringify({ message: 'nope' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    }),
  )

describe('useReferenceData', () => {
  it('is `partial` when SOME lists fail, and names the ones that did', async () => {
    const loaders = {
      banks: vi.fn().mockResolvedValue(['Absa', 'Nedbank']),
      roles: vi.fn().mockRejectedValue(await http500()),
    }

    render(<Probe loaders={loaders} />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('partial'))

    // 🔑 BOTH HALVES, AND THE FIRST IS THE ONE THAT MATTERS MOST. A "partial" that threw
    // away the lists that DID arrive would be an error state wearing a different name --
    // and blanking a screen over one dead endpoint is the over-correction this whole unit
    // exists to avoid.
    expect(screen.getByTestId('banks')).toHaveTextContent('Absa|Nedbank')
    expect(screen.getByTestId('failed')).toHaveTextContent('roles')
  })

  it('is `error` only when EVERY list fails', async () => {
    const failure = await http500()
    const loaders = {
      banks: vi.fn().mockRejectedValue(failure),
      roles: vi.fn().mockRejectedValue(failure),
    }

    render(<Probe loaders={loaders} />)

    // A screen with no reference data at all cannot do its job, and saying so is honest.
    // The line between this and `partial` is the hook's whole reason for existing.
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('error'))
    expect(screen.getByTestId('failed')).toHaveTextContent('banks,roles')
  })

  it('is `ready` when they all arrive, and reports nothing failed', async () => {
    // The must-pass half. Every assertion above would also pass against a hook that
    // reported `partial` unconditionally.
    const loaders = {
      banks: vi.fn().mockResolvedValue(['Absa']),
      roles: vi.fn().mockResolvedValue(['Organiser']),
    }

    render(<Probe loaders={loaders} />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))
    expect(screen.getByTestId('failed')).toHaveTextContent('')
  })

  it('an EMPTY list is not a failure', async () => {
    // A bank with no branches on file is a real answer. Counting it as a failure would put
    // a "some option lists are missing" banner above a screen where nothing is missing --
    // and a banner that cries wolf is one administrators learn to scroll past.
    const loaders = {
      banks: vi.fn().mockResolvedValue([]),
      roles: vi.fn().mockResolvedValue(['Organiser']),
    }

    render(<Probe loaders={loaders} />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))
  })

  it('one failing list does NOT prevent the others from arriving', async () => {
    // 🔴 THE `allSettled` ASSERTION, AND IT IS THE STRUCTURAL ONE. `Promise.all` rejects on
    // the first failure and discards the results that succeeded -- so a single dead endpoint
    // would blank every dropdown on the screen. This would go red the day someone "tidies"
    // allSettled into all, and nothing else here would.
    const loaders = {
      banks: vi.fn().mockRejectedValue(await http500()),
      roles: vi.fn().mockResolvedValue(['Organiser', 'Shop Steward']),
    }

    render(<Probe loaders={loaders} />)

    await waitFor(() => expect(screen.getByTestId('roles')).toHaveTextContent('Organiser|Shop Steward'))
  })

  it('an abort is NOT counted as a failure', async () => {
    // §16, EH-D3. A superseded request is the caller's own doing. Counted as a failure it
    // would raise a "some option lists are missing" banner over a screen where nothing is
    // wrong -- and the shape is built by the helper, never by hand, because a hand-written
    // `name: 'AbortError'` is the exact mistake that let this branch rot in `useAsyncOptions`.
    const abort = networkError(new DOMException('superseded', 'AbortError'))
    expect(abort.kind, 'the helper no longer produces `abort`; this test is now vacuous').toBe(
      'abort',
    )

    const loaders = {
      banks: vi.fn().mockRejectedValue(abort),
      roles: vi.fn().mockResolvedValue(['Organiser']),
    }

    render(<Probe loaders={loaders} />)

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))
    expect(screen.getByTestId('failed')).toHaveTextContent('')
  })

  it('an abort on a later run keeps the list that had loaded -- it is not an empty list (06.4)', async () => {
    // 🔴 EH-D3 says a superseded request "leaves the entry exactly as it was", and the hook wrote `[]`
    // for it instead: a bank list that had arrived read as a list with nothing in it, reported `ready`
    // -- the confident wrong answer this hook exists to prevent. It needs a list that loaded FIRST, which
    // is why the test above, whose only run is the abort, could not see it.
    const user = userEvent.setup()
    const abort = networkError(new DOMException('superseded', 'AbortError'))
    expect(abort.kind, 'the helper no longer produces `abort`; this test is now vacuous').toBe('abort')
    const banks = vi.fn().mockResolvedValueOnce(['Absa', 'Nedbank']).mockRejectedValueOnce(abort)
    const loaders = { banks, roles: vi.fn().mockResolvedValue(['Organiser']) }

    render(<Probe loaders={loaders} />)
    await waitFor(() => expect(screen.getByTestId('banks')).toHaveTextContent('Absa|Nedbank'))

    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(banks).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))

    expect(screen.getByTestId('banks')).toHaveTextContent('Absa|Nedbank')
    expect(screen.getByTestId('failed')).toHaveTextContent('')
  })

  it('field(name) hands a select its options, its own failure and a retry -- and the failure is the rejection itself', async () => {
    // 🔑 ONE SPREAD PER SELECT (F-P54). Every screen wired a list's options and dropped the other three
    // facts, so a failed list rendered as "No options available". The rejection travels whole, so the
    // select's retry is offered only where retrying could work (`retryable`); the loaded list reports none.
    const user = userEvent.setup()
    const roles = vi.fn().mockRejectedValueOnce(await http500()).mockResolvedValue(['Organiser'])
    const loaders = { banks: vi.fn().mockResolvedValue(['Absa']), roles }

    render(<FieldProbe loaders={loaders} />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('partial'))

    expect(screen.getByTestId('banks-options')).toHaveTextContent('Absa')
    expect(screen.getByTestId('banks-error')).toHaveTextContent('none')
    expect(screen.getByTestId('roles-error')).toHaveTextContent('failed')
    expect(screen.getByTestId('roles-retryable')).toHaveTextContent('true')

    await user.click(screen.getByRole('button', { name: 'Retry roles' }))
    await waitFor(() => expect(screen.getByTestId('roles-error')).toHaveTextContent('none'))
  })

  it('retry re-runs every loader and can recover the screen', async () => {
    const user = userEvent.setup()
    const banks = vi
      .fn()
      .mockRejectedValueOnce(await http500())
      .mockResolvedValue(['Absa'])
    const loaders = { banks, roles: vi.fn().mockResolvedValue(['Organiser']) }

    render(<Probe loaders={loaders} />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('partial'))

    await user.click(screen.getByRole('button', { name: 'Try again' }))

    // EH-D4: an error state with no way out is a dead end, and asserting the button EXISTS
    // would not have proven it works.
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))
    expect(screen.getByTestId('banks')).toHaveTextContent('Absa')
  })

  it('retry goes back to LOADING while the loaders run again -- the half the recovery test cannot see', async () => {
    /*
     * 🔴 `10.4` MOVED THIS. `run` set `loading` synchronously on the way into the mount effect -- the exact shape
     * `react-hooks/set-state-in-effect` flags, which this file passed only because an unrelated early `return`
     * blinds the rule (F-P60). The reset now lives on the retry alone. The recovery test above cannot witness it:
     * it asserts the screen ends `ready`, which is true whether or not it ever went back to loading. So the
     * retry's answer is HELD here, and the state is read while it is.
     */
    const user = userEvent.setup()
    const held = deferred()
    const banks = vi
      .fn()
      .mockRejectedValueOnce(await http500())
      .mockImplementationOnce(() => held.promise)
    const loaders = { banks, roles: vi.fn().mockResolvedValue(['Organiser']) }

    render(<Probe loaders={loaders} />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('partial'))

    try {
      await user.click(screen.getByRole('button', { name: 'Try again' }))
      expect(screen.getByTestId('state')).toHaveTextContent('loading')
    } finally {
      held.resolve(['Absa'])
    }
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))
  })

  it('does not re-fetch on every render', async () => {
    // 🔴 THE INFINITE-REQUEST LOOP, ASSERTED. A fetching effect that depends on a rebuilt
    // object re-fires forever, and on screen that is indistinguishable from a slow backend --
    // it is why the hook documents that its `loaders` must be stable. This proves the hook
    // holds up its half: given a stable object, one mount is one fetch.
    const banks = vi.fn().mockResolvedValue(['Absa'])
    const loaders = { banks }

    const { rerender } = render(<Probe loaders={loaders} />)
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('ready'))

    rerender(<Probe loaders={loaders} />)
    rerender(<Probe loaders={loaders} />)

    expect(banks).toHaveBeenCalledTimes(1)
  })
})
