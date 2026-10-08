import { ERROR_KINDS } from '../services/parseApiError.js';

/**
 * THE ONE SINK. EH-D10, `06.1b`.
 *
 * 🔴 **WHAT THIS REPLACES, AND WHY IT IS A PRIVACY FIX RATHER THAN A TIDINESS ONE.**
 *
 * Before this file the application had fourteen bare `console.error` calls in pages and
 * components, most of them `console.error(err)` with the whole rejection. `06.1a` made every
 * rejection carry `body` -- the server's parsed envelope -- precisely so a failure could be
 * diagnosed. Those two changes together mean a failed member save now prints the request's
 * echo, and the envelope of a member endpoint contains the member: names, identity number,
 * bank account.
 *
 * A browser console is not a private place. It survives in a saved HAR, in a screen share,
 * in a support ticket screenshot, and in any extension the user has installed. `Auth.jsx`
 * is this application's own lesson -- it logged `{ email, password }` on every login attempt
 * -- and the fix there was to delete the line. The fix here cannot be to delete the line,
 * because the diagnosis is genuinely needed. So it is to make the sink the only exit and let
 * the sink decide what leaves.
 *
 * 🔑 **IT PAIRS WITH THE CAPTURE MEMBER ARC'S B4 FROM THE OTHER SIDE.** B4 keeps PII out of
 * the response BODY; this keeps it out of the LOG. Same data, two exits, and neither fix
 * covers the other -- which is why both rows exist.
 *
 * ---
 *
 * 🔑 **AN ALLOWLIST, NOT A DENYLIST, AND THAT IS THE WHOLE DESIGN.** A denylist -- "strip
 * `identityNumber`, `firstNames`, `bankAccountNumber`" -- is a list somebody has to extend
 * every time the server adds a field, and the failure mode of forgetting is that the new
 * field is logged. The failure mode of forgetting to allow a field is that a diagnostic line
 * is less useful. Only the second is acceptable, so only `kind`, `status`, `retryable` and
 * the field NAMES from `validationErrors` are ever emitted.
 *
 * ⚠️ **THE MESSAGE IS NOT ALLOWED THROUGH EITHER, AND THAT IS DELIBERATE AND SLIGHTLY
 * PAINFUL.** `error.message` is the server's own sentence, written for a person, and it is
 * the single most useful thing here. It is also written by a handler this arc does not own,
 * over entities that are people: *"Member 8801015009087 already exists"* is a real shape for
 * a uniqueness violation. A sink that is safe only while another repository keeps a promise
 * is not safe. The message is rendered ON SCREEN, where the user it concerns is the one
 * reading it; it is the LOG it stays out of.
 *
 * 🔑 **`validationErrors` CONTRIBUTES ITS KEYS AND NEVER ITS VALUES.** Knowing that
 * `identityNumber` was refused is the diagnostically valuable half; the value it was refused
 * for is the member's identity number.
 */

/**
 * The fields that may leave. Frozen, so the allowlist cannot be widened by accident at a
 * call site that "just needs one more field for this one bug".
 */
const ALLOWED = Object.freeze(['kind', 'status', 'retryable']);

/**
 * Builds the line that is safe to emit.
 *
 * Exported for the test that proves the redaction: asserting on `console.error`'s arguments
 * would test the spy, and a sink whose safety is only ever checked through a mock is a sink
 * nobody has actually read the output of.
 *
 * @param {string} context where it happened -- a component or operation name, never data
 * @param {unknown} error
 * @returns {object}
 */
export function redact(context, error) {
  const safe = { context };

  for (const field of ALLOWED) {
    if (error?.[field] !== undefined) safe[field] = error[field];
  }

  /*
   * ⚠️ A ZERO STATUS IS DROPPED, AND ONLY A ZERO STATUS.
   *
   * `apiError` defaults `status` to 0 for anything that never got an answer -- a network
   * failure, a timeout, an abort. Emitted, it reads as `status: 0` in a log, and "HTTP 0" is
   * a number somebody will try to look up. `kind` already says exactly what happened for all
   * three, so the zero adds nothing and costs a wrong lead.
   *
   * 🔑 IT IS NOT A GENERAL FALSY CHECK. `retryable: false` is a real and important value
   * -- it is the difference between a failure worth retrying and one that never will be --
   * and a `if (value)` filter would have silently dropped it. That is the bug this comment
   * exists to stop the next reader from introducing while tidying.
   */
  if (safe.status === 0) delete safe.status;

  const fields = Object.keys(error?.validationErrors ?? {});
  if (fields.length) safe.refusedFields = fields;

  // The absence of a `kind` is itself diagnostic: it means this rejection did not come from
  // `requestJson`, so it is a programming error rather than a failed request. Said in the
  // one word that distinguishes them, rather than by logging the object to find out.
  if (!error?.kind) safe.kind = 'unhandled';

  return safe;
}

/**
 * Report a failure.
 *
 * @param {string} context where it happened. A NAME, never data -- `'Members.mount'`, not
 *                 the member. The lint rule cannot enforce that, so it is said here.
 * @param {unknown} error the rejection, in `conventions.md` §16's shape
 */
/*
 * 🔑 THE SINK IS ALSO WHERE "WE ARE OFFLINE" IS LEARNED, and putting it here rather than in
 * a second module is the point rather than a convenience.
 *
 * `navigator.onLine === false` is reliable when true but almost never fires for the failures
 * users actually meet -- a captive portal, a dropped VPN, a backend that is down all report
 * `onLine: true`. The signal that DOES fire is a `kind: 'network'` rejection, and every one of
 * those already passes through here on its way to being reported. A separate detector would be
 * a second place that has to be called, and the failure mode of forgetting to call it is a
 * banner that never appears.
 *
 * ⚠️ A TIMESTAMP, NOT A BOOLEAN. A boolean cannot say "it happened AGAIN", so a user who
 * dismissed the banner would never see it a second time.
 */
const listeners = new Set();
let lastNetworkFailureAt = null;

/** @returns {number|null} when a `network` rejection was last reported. */
export const getLastNetworkFailureAt = () => lastNetworkFailureAt;

/**
 * Subscribe to network failures.
 * @param {() => void} listener
 * @returns {() => void} unsubscribe
 */
export function onNetworkFailure(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export default function reportError(context, error) {
  if (error?.kind === ERROR_KINDS.NETWORK) {
    lastNetworkFailureAt = Date.now();
    // Copied before iterating: a listener that unsubscribes itself while being notified
    // would otherwise mutate the set mid-iteration.
    for (const listener of [...listeners]) listener(lastNetworkFailureAt);
  }

  // THE one permitted console call in the application. `no-console` is `error` for all of
  // `src/**`, precisely so that this disable comment is the only one and a second one
  // anywhere is a visible decision rather than a habit.
  // eslint-disable-next-line no-console
  console.error('[sasbo]', redact(context, error));
}
