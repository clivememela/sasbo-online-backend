/**
 * A promise the TEST settles, when it chooses.
 *
 * 🔑 THE SUITE'S ANSWER TO "SLOW". A request that must be observed mid-flight -- a loading state, a dialog
 * that must refuse to close while a write runs, a stale answer that must not land -- is HELD by a handler
 * that awaits `deferred().promise`, and released by the test. Nothing is timed, so nothing depends on how fast
 * the machine is. The alternative this replaced, a real `setTimeout(r, 50)` in the handler, was a reproduced
 * flake: it made the state observable by being slower than the assertions, which is a race won on an idle box
 * (`Members.states.test.jsx` records the diagnosis).
 *
 * Until P10's refactor pass the same four lines were written out ten times under six names (`release`,
 * `answer`, `releaseSecond`, `held`, ...). Wrappers that say WHAT is held -- `membersRespondWhenReleased`,
 * `channelsWhenReleased` -- keep their names; only the latch inside them is this.
 *
 * ⚠️ RELEASE IN A `finally`. A test that throws before releasing leaves the request pending for the rest of
 * the file, and the next failure reads as a hang it did not cause.
 */
export function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

export default deferred
