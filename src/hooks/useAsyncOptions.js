import { useCallback, useEffect, useRef, useState } from 'react';

import { ERROR_KINDS } from '../services/parseApiError.js';

/** No options: one stable array, so a disabled or unanswered field is not handed a new one each render. */
const NO_OPTIONS = [];

/** The answer before any request has settled. `request: -1` matches no request, so the first one is loading. */
const NO_ANSWER = { request: -1, options: NO_OPTIONS, error: null };

/** Same inputs as last render, compared element by element with `Object.is` -- exactly as React compares deps. */
const sameInputs = (a, b) => a.length === b.length && a.every((value, index) => Object.is(value, b[index]));

/**
 * OPTIONS THAT ARRIVE OUT OF ORDER MUST NOT OVERWRITE NEWER ONES. `03.2b`, R-ROB-G.
 *
 * 🔴 **THE FAILURE THIS EXISTS FOR, and it is silent.** Type "Nedb" into a bank field and
 * two requests are in flight — one for `Ned`, one for `Nedb`. If the slower `Ned` answer
 * lands second, the list the user is looking at is the answer to a query they have already
 * moved past. Nothing errors. The options are real options. They are just **wrong**, and
 * the only symptom is a user who says the dropdown "flickers" or "shows the wrong thing
 * sometimes".
 *
 * Every list screen in this application fetches on mount and several fetch on typing, and
 * not one of them sequences. This is the fix as a hook rather than as a rule, because a
 * rule is something 27 rebuilt routes each have to remember (contract ⑮).
 *
 * 🔑 **SUPERSEDED, NOT ONLY CANCELLED.** `AbortController` cancels the *request*, which is
 * worth doing and is not sufficient: an already-resolved promise still runs its `.then`. So
 * each request's effect owns a `superseded` flag that its cleanup sets -- new inputs, a
 * refetch, or unmount -- and an answer is applied only while its request is still current.
 * Both are used: abort to stop the network, the flag to stop the write. *(Until `10.4` this
 * was a ref-held ticket number; the effect's own lifetime now does the same job.)*
 *
 * ⚠️ **AN ABORT IS NOT A FAILURE.** A superseded request is dropped silently and the state
 * is left exactly as it was — no error, no empty list, no spinner stuck on. That is the
 * `kind` vocabulary `06.1a` fixes (`http · network · nonJson · timeout · abort`), agreed
 * with the automation arc: *an abort is NOT a failure, a timeout IS one and offers retry*.
 * Getting this wrong renders a cancelled keystroke as "we could not load the banks".
 *
 * 🔴 **AND IT DISTINGUISHES FOUR STATES, NOT THREE** (conventions §8). `loading`, `error`,
 * `empty` and loaded are separate: a failed fetch that renders as an empty list is A27, and
 * A27 is the defect this arc counts by the class rather than by the row.
 *
 * @param fetcher   `(signal) => Promise<unknown[]>`. Receives an AbortSignal.
 * @param deps      re-fetch when these change, exactly like an effect's dependency list.
 * @param options   `{ enabled }` — skip entirely while false (a dependent field with no parent yet).
 */
export default function useAsyncOptions(fetcher, deps = [], { enabled = true } = {}) {
  const fetcherRef = useRef(fetcher);

  // The fetcher is almost always an inline arrow, so it is a new function every render.
  // Keeping it in a ref means the fetch effect below depends on `deps` — the caller's
  // actual inputs — rather than re-firing on every parent render, which would be an
  // infinite request loop that looks exactly like a slow backend.
  //
  // ⚠️ ASSIGNED IN AN EFFECT, NOT DURING RENDER. Writing a ref while rendering is a
  // violation the `react-hooks` rules catch as an error, and it is not pedantry: under
  // concurrent rendering a render can be thrown away, and the ref would keep the
  // discarded render's closure. This effect is declared BEFORE the fetch effect, so it
  // runs first on every commit and the fetcher is always current by the time it is called.
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  /*
   * 🔴 ONE ANSWER PER REQUEST, AND `loading` IS DERIVED FROM WHETHER THE CURRENT REQUEST HAS ONE (`10.4`).
   *
   * This hook used to store `{ options, loading, error }` and flip it on the way INTO the fetch effect --
   * `setState({ loading: true, error: null })` before the request, and the disabled state likewise -- which
   * `react-hooks/set-state-in-effect` makes an error this phase. So nothing is flipped any more:
   *
   *   `request`  counts requests. It moves when the inputs change (`enabled` or any of `deps`), noticed during
   *              render against the inputs last seen, and when `refetch` is pressed.
   *   `answer`   is the last settled result, stamped with the request it answers.
   *   loading    = enabled, and the answer is not for the current request. Nobody sets it; it cannot stick.
   *
   * 🔑 THE TICKET IS STILL HERE, IT IS JUST THE EFFECT'S OWN LIFETIME NOW. A request is superseded when the
   * effect for it is cleaned up -- new inputs, a refetch, or unmount -- which sets `superseded` and aborts the
   * network, so a stale answer is dropped exactly as before. Unlike the old ref ticket it also covers unmount,
   * and a refetch now aborts the request it replaces rather than only ignoring its answer.
   *
   * ⚠️ WHAT HAD TO BE PRESERVED, and is: a reload KEEPS the options it already had while it waits (the old
   * `{ ...current, loading: true }`); disabling EMPTIES them, so re-enabling never flashes a list from before;
   * and an abort leaves everything as it was. `useAsyncOptions.test.jsx` is the contract.
   */
  const inputs = [enabled, ...deps];
  const [seenInputs, setSeenInputs] = useState(inputs);
  const [request, setRequest] = useState(0);
  const [answer, setAnswer] = useState(NO_ANSWER);
  if (!sameInputs(inputs, seenInputs)) {
    setSeenInputs(inputs);
    setRequest((n) => n + 1);
    if (!enabled) setAnswer(NO_ANSWER);
  }

  useEffect(() => {
    if (!enabled) return undefined;

    let superseded = false;
    const controller = typeof AbortController === 'undefined' ? null : new AbortController();

    // 🔑 CALLED SYNCHRONOUSLY, not from a microtask: a Promise EXECUTOR runs at once. Deferring the call by even
    // one tick delays every request by a render, and makes "was it called?" depend on when a test looks. The
    // executor is also what the old try/catch was for -- a fetcher that throws before it returns a promise
    // becomes a rejection, and so the error state, rather than an exception inside an effect. And it means no
    // state is set synchronously here at all, which is the point of the phase.
    new Promise((resolve) => resolve(fetcherRef.current(controller?.signal)))
      .then((result) => {
        // 🔑 THE ONE LINE THE HOOK EXISTS FOR. A stale answer is discarded, not applied.
        if (superseded) return;
        setAnswer({ request, options: Array.isArray(result) ? result : [], error: null });
      })
      .catch((error) => {
        if (superseded) return;
        /*
         * An abort is the caller's own doing. Reporting it as a failure would put an
         * error under a field the user is still happily typing into.
         *
         * 🔴 THIS TESTED `error.name === 'AbortError'` UNTIL `06.1b`, AND BY THEN THAT
         * COULD NEVER BE TRUE. `06.1a` put every service on `requestJson`, which rejects
         * with `Object.assign(new Error(message), { kind, ... })` -- an ordinary `Error`,
         * whose `name` is `"Error"`. The DOM's `AbortError` never reaches a consumer any
         * more; the helper translates it into `kind: 'abort'` at the boundary, which is
         * exactly what EH-D3 asks for ("no consumer tests for AbortError").
         *
         * ⚠️ AND THE TEST DID NOT CATCH IT, which is the part worth remembering. It built
         * its own rejection with `error.name = 'AbortError'` -- a shape no service in this
         * application produces -- so it went on passing while the branch it guarded had
         * become dead code. A test that constructs its subject's input from the old
         * contract cannot notice the contract changing. It now rejects with the shape
         * `parseApiError` actually builds.
         *
         * The consequence in the running app was not cosmetic: every superseded keystroke
         * in a searchable field would have rendered "we could not load the banks" under a
         * field the user was still typing into, and then cleared the options.
         */
        if (error?.kind === ERROR_KINDS.ABORT) return;
        setAnswer({ request, options: [], error });
      });

    return () => {
      superseded = true;
      controller?.abort();
    };
  }, [enabled, request]);

  const refetch = useCallback(() => {
    setRequest((n) => n + 1);
  }, []);

  const current = answer.request === request;
  const loading = enabled && !current;
  const options = enabled ? answer.options : NO_OPTIONS;
  const error = enabled && current ? answer.error : null;

  return {
    options,
    loading,
    error,
    /** The fourth state, named rather than inferred at each call site. */
    isEmpty: !loading && !error && options.length === 0,
    refetch,
  };
}
