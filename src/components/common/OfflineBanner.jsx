import { useEffect, useState } from 'react';
import { ServerCrash, WifiOff } from 'lucide-react';

/**
 * OFFLINE IS A STATE, NOT A KIND OF ERROR. EH-D11, `06.1b`.
 *
 * 🔴 **WHAT IT LOOKS LIKE WITHOUT THIS.** A member on a phone walks into a lift. Every
 * request now fails, and each screen renders its own honest per-panel failure: *"we could
 * not load the banks"*, *"we could not load the recruiters"*, *"the member register could
 * not be loaded"*. Three different sentences, each true, none of them the one fact that
 * explains all three and the only one the user can act on. Then they retry each panel in
 * turn, from a lift.
 *
 * 🔑 **SO IT IS A PAGE-LEVEL FACT ANNOUNCED ONCE**, above the panels rather than instead of
 * them. The panels keep their own states -- this does not suppress them, and suppressing
 * them would be wrong, because a panel that failed while ONLINE and a panel that failed
 * because the connection went are different situations that happen to look alike for a
 * moment.
 *
 * ---
 *
 * ⚠️ **`navigator.onLine` IS NOT A CONNECTIVITY CHECK AND MUST NOT BE TREATED AS ONE.** It
 * reports whether the machine has a network interface that thinks it is up. A laptop on a
 * café wifi that requires a login page is `onLine: true` and can reach nothing; a captive
 * portal, a VPN that dropped, a backend that is down -- all `onLine: true`.
 *
 * **`false` is reliable; `true` is not.** So this banner is driven by BOTH signals and they
 * do different jobs:
 *
 *   `navigator.onLine === false`   the browser is certain. Show the banner.
 *   a `network` rejection          a request genuinely failed to reach anyone. Show it too,
 *                                  because this is the case `onLine` misses -- and it is
 *                                  the common one.
 *
 * A banner driven by `onLine` alone would stay hidden through exactly the failures users
 * actually meet, which is worse than not having one: it would be a check that reports what
 * we hope to see.
 *
 * 🔑 **THE `online` EVENT IS A PROMPT, NOT A PROOF.** When it fires the banner offers to
 * retry rather than declaring everything fixed and silently re-running requests -- because
 * `online` firing means the interface came back, not that the backend is reachable. The
 * `onRetry` the app hands down is what actually re-runs anything.
 *
 * @param {{ lastNetworkFailureAt?: number|null, onRetry?: () => void }} props
 *        `lastNetworkFailureAt` is a timestamp rather than a boolean so that a SECOND
 *        network failure after a dismissal re-opens the banner. A boolean cannot express
 *        "it happened again".
 */
export default function OfflineBanner({ lastNetworkFailureAt = null, onRetry }) {
  // Read once, lazily, rather than at module scope: `navigator` is absent in the node half
  // of the suite, and a module-scope read would throw on import in files that never render.
  const [browserOffline, setBrowserOffline] = useState(
    () => typeof navigator !== 'undefined' && navigator.onLine === false,
  );
  const [dismissedAt, setDismissedAt] = useState(0);
  const [reconnected, setReconnected] = useState(false);

  useEffect(() => {
    const goOffline = () => {
      setBrowserOffline(true);
      setReconnected(false);
    };
    const goOnline = () => {
      setBrowserOffline(false);
      // Not "all clear": the interface is back and the backend may or may not be. The
      // banner changes what it OFFERS rather than disappearing, so the user gets one
      // deliberate retry instead of a silent storm of re-requests.
      setReconnected(true);
    };

    window.addEventListener('offline', goOffline);
    window.addEventListener('online', goOnline);
    return () => {
      window.removeEventListener('offline', goOffline);
      window.removeEventListener('online', goOnline);
    };
  }, []);

  const failedRecently = lastNetworkFailureAt != null && lastNetworkFailureAt > dismissedAt;
  const visible = browserOffline || failedRecently || reconnected;

  if (!visible) return null;

  const offline = browserOffline || (failedRecently && !reconnected);
  /*
   * 🔴 K2 -- THE SENTENCE MUST NOT CLAIM WHAT THE BROWSER CANNOT KNOW. The banner rightly shows for a
   * `network` rejection while `navigator.onLine` is true (the header above says why), but it said "You are
   * offline" there too: to a user on working wifi whose server had refused the connection, and above a
   * register that had just loaded when only the bank list failed (`06.3` critique, both captured). Only
   * `navigator.onLine === false` is certain enough for "offline"; a rejection says the server could not be
   * reached, and claims nothing about the rest of the page.
   */
  const unreachable = offline && !browserOffline;

  return (
    <div
      // 🔑 `alert`, not `status`: the user is about to act on screens that cannot save.
      role="alert"
      data-testid="offline-banner"
      data-offline={offline ? 'true' : 'false'}
      className="mx-auto mb-4 flex max-w-6xl items-start gap-2 rounded-lg border border-status-suspended-dot bg-status-suspended-bg p-3 text-sm text-status-suspended"
    >
      {unreachable ? (
        <ServerCrash aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.75} />
      ) : (
        <WifiOff aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.75} />
      )}
      <p className="flex-1">
        {unreachable
          ? 'SASBO Online could not reach its server just now. Anything that did not load can be tried again.'
          : offline
            ? 'You are offline. Nothing on this page can be saved or refreshed until the connection is back.'
            : 'The connection is back. Anything that failed while you were offline can be tried again.'}
      </p>
      {!offline && onRetry && (
        <button
          type="button"
          onClick={() => {
            setReconnected(false);
            onRetry();
          }}
          className="touch-target shrink-0 rounded-lg border border-status-suspended-dot px-3 py-1 font-medium hover-hover:hover:bg-status-suspended-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
        >
          Try again
        </button>
      )}
      <button
        type="button"
        onClick={() => {
          setDismissedAt(Date.now());
          setReconnected(false);
          // ⚠️ `browserOffline` is NOT cleared here. A banner the user can dismiss while
          // genuinely offline is a banner that lies about a fact that is still true; the
          // `offline` event is what set it and the `online` event is what clears it.
        }}
        className="touch-target shrink-0 rounded-lg px-2 py-1 underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
      >
        Dismiss
      </button>
    </div>
  );
}
