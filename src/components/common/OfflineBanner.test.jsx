// @vitest-environment jsdom -- listens for window online/offline events
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import OfflineBanner from './OfflineBanner'

/**
 * OFFLINE IS A STATE. EH-D11, `06.1b`.
 *
 * 🔴 **THE RULE THESE ASSERTIONS PROTECT IS THAT `navigator.onLine` IS NOT A CONNECTIVITY
 * CHECK.** It reports whether the machine has an interface that thinks it is up. A café wifi
 * behind a login page, a dropped VPN, a backend that is down — all `onLine: true`. `false` is
 * reliable; `true` is not. A banner driven by `onLine` alone stays hidden through exactly the
 * failures users meet, which is worse than not having one: a check that reports what we hope
 * to see.
 */

/** Drives `navigator.onLine`, which is a getter and cannot simply be assigned. */
const setBrowserOnline = (value) =>
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(value)

const fire = (name) => act(() => { window.dispatchEvent(new Event(name)) })

afterEach(() => {
  vi.restoreAllMocks()
})

describe('OfflineBanner', () => {
  it('`07.2`: both of its buttons give a coarse pointer a 44px target', () => {
    // Measured under real touch emulation, *Dismiss* offered a 63×27 target. Both buttons wear `touch-target`, the
    // utility that adds a 44px hit area on a coarse pointer without growing the button.
    setBrowserOnline(true)
    render(<OfflineBanner lastNetworkFailureAt={Date.now()} onRetry={() => {}} />)
    fire('online')

    for (const name of [/try again/i, /dismiss/i]) {
      expect(screen.getByRole('button', { name }).className).toMatch(/\btouch-target\b/)
    }
  })

  it('renders nothing at all when the connection is fine', () => {
    // The must-pass half. Every assertion below would also pass against a banner that was
    // permanently visible, and a permanent banner is one people stop reading.
    setBrowserOnline(true)
    const { container } = render(<OfflineBanner />)

    expect(container).toBeEmptyDOMElement()
  })

  it('appears on a `network` rejection even while the browser says it is online', () => {
    // 🔑 THE CASE `navigator.onLine` MISSES, AND IT IS THE COMMON ONE. A request that
    // genuinely failed to reach anyone is evidence; the interface's opinion is not.
    setBrowserOnline(true)
    render(<OfflineBanner lastNetworkFailureAt={Date.now()} />)

    expect(screen.getByTestId('offline-banner')).toHaveAttribute('data-offline', 'true')
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('K2: on a rejection while the browser is online, it says the server could not be reached -- not that you are offline', () => {
    // 🔴 `06.3` CRITIQUE, CAPTURED TWICE. A server that refused the connection, on working wifi, was told
    // "You are offline" -- and so was a register that had just loaded, when only its bank list failed. The
    // browser cannot tell a down server from a dropped connection, so the sentence must not pick one.
    setBrowserOnline(true)
    render(<OfflineBanner lastNetworkFailureAt={Date.now()} />)

    const banner = screen.getByTestId('offline-banner')
    expect(banner.textContent).toMatch(/could not reach its server/i)
    expect(banner.textContent).not.toMatch(/you are offline/i)
  })

  it('K2, the must-pass: when the browser itself says offline, the banner still says so', () => {
    setBrowserOnline(true)
    render(<OfflineBanner />)
    fire('offline')

    const banner = screen.getByTestId('offline-banner')
    expect(banner.textContent).toMatch(/you are offline/i)
    expect(banner.textContent).not.toMatch(/could not reach its server/i)
  })

  it('appears on the browser’s own `offline` event', () => {
    setBrowserOnline(true)
    render(<OfflineBanner />)
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument()

    fire('offline')

    expect(screen.getByTestId('offline-banner')).toHaveAttribute('data-offline', 'true')
  })

  it('offers a retry when the connection comes back, rather than declaring it fixed', async () => {
    // 🔑 `online` FIRING MEANS THE INTERFACE CAME BACK, NOT THAT THE BACKEND IS REACHABLE.
    // Silently re-running every failed request on that signal is a request storm on a
    // guess; offering one deliberate retry is not.
    const user = userEvent.setup()
    const onRetry = vi.fn()
    setBrowserOnline(true)

    render(<OfflineBanner lastNetworkFailureAt={Date.now()} onRetry={onRetry} />)
    fire('online')

    const banner = screen.getByTestId('offline-banner')
    expect(banner).toHaveAttribute('data-offline', 'false')
    expect(banner.textContent).toMatch(/connection is back/i)
    expect(onRetry).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('a SECOND failure re-opens a banner the user dismissed', async () => {
    // ⚠️ THIS IS WHY THE PROP IS A TIMESTAMP AND NOT A BOOLEAN. A boolean cannot express
    // "it happened again", so a user who dismissed the banner once would never see it a
    // second time — and the second time is the one that means the first was not a blip.
    const user = userEvent.setup()
    setBrowserOnline(true)

    const { rerender } = render(<OfflineBanner lastNetworkFailureAt={1_000} />)
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument()

    rerender(<OfflineBanner lastNetworkFailureAt={Date.now() + 5_000} />)
    expect(screen.getByTestId('offline-banner')).toBeInTheDocument()
  })

  it('cannot be dismissed away while the browser is genuinely offline', async () => {
    // A banner the user can dismiss while offline is a banner that lies about a fact that
    // is still true. The `offline` event set it; only the `online` event clears it.
    const user = userEvent.setup()
    setBrowserOnline(true)
    render(<OfflineBanner />)
    fire('offline')

    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    expect(screen.getByTestId('offline-banner')).toBeInTheDocument()
  })

  it('starts visible when the page loads while already offline', () => {
    // The banner mounts with the shell, so a user who opens the app offline gets the fact
    // before they try anything, rather than after their first failed request.
    setBrowserOnline(false)
    render(<OfflineBanner />)

    expect(screen.getByTestId('offline-banner')).toHaveAttribute('data-offline', 'true')
  })
})
