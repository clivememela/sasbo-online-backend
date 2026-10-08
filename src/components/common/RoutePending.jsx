/**
 * What fills the outlet while a route's chunk is still arriving.
 *
 * 🔴 IT CARRIES A TESTID ON PURPOSE, and that is the load-bearing part.
 *
 * `03.1` split every route with `lazy()`. The route smoke test in
 * `App.routes.test.jsx` asserts that a mounted route "produced something" -- and a
 * Suspense fallback IS something. Without a way to tell the fallback from the page,
 * code splitting would have silently converted that guard into one that passes on
 * every route whose module fails to load, which is the exact class of break the
 * guard exists to catch (a broken default export, a renamed named-export, a throw
 * during import). It would still have been green.
 *
 * So the fallback is identifiable, the test waits for it to GO, and the guard came
 * out of the change stronger than it went in: it now proves the chunk resolved and
 * the page rendered, where before it only proved the page rendered.
 *
 * Deliberately near-silent visually: a route chunk on a local network resolves in
 * milliseconds, and a spinner that flashes for 40ms reads as a glitch. `06.1` owns
 * the designed loading states; this is plumbing, and it says so.
 */
export default function RoutePending() {
  return (
    <div
      data-testid="route-pending"
      role="status"
      aria-live="polite"
      className="flex-1 min-h-24"
    >
      <span className="sr-only">Loading page…</span>
    </div>
  );
}
