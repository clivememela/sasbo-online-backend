import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import reportError from '../lib/reportError';
import { ERROR_KINDS } from '../services/parseApiError.js';

/**
 * THE REFERENCE LISTS A SCREEN NEEDS, AND WHICH OF THEM DID NOT ARRIVE. `06.1b`, EH-D9.
 *
 * 🔴 **THE DEFECT CLASS THIS EXISTS FOR, COUNTED.** Twelve sites across five screens loaded a
 * dropdown's options on mount and handled failure with `.catch(err => console.error(err))` or
 * `.catch(() => setThing([]))`. In every one of them a backend that is down renders as a
 * dropdown with nothing in it -- which is A27's exact shape (*"failure is indistinguishable
 * from empty"*) reproduced once per reference list rather than once per screen. `Recruiters`
 * alone had five, so a single bad deploy could empty the bank, province, area, branch and
 * role selectors at once and the screen would say nothing at all.
 *
 * ⚠️ **AND IT IS WORSE THAN AN EMPTY LIST, BECAUSE THE USER CAN STILL SUBMIT.** An
 * administrator who cannot find a member's bank in the list does not conclude the backend is
 * down; they conclude the bank is missing, and they pick the closest one. The silent failure
 * does not block the work, it corrupts it.
 *
 * ---
 *
 * 🔑 **WHY A HOOK OVER N SEPARATE `useState`s: THE FOURTH STATE ONLY EXISTS ACROSS THE SET.**
 *
 * `loading`, `empty` and `error` are properties of one fetch and can be tracked one at a
 * time. **`partial` is not.** "Three of five sources answered" is not a fact any individual
 * fetch knows; it exists only where the set is known, and a screen that tracks five booleans
 * has nowhere to put it. That is why the DoD asks for four states and not three, and why
 * this returns the SET rather than being called five times.
 *
 * The automation arc's reporting tool is the model, and the rule is theirs: mark a section
 * `incomplete` and print `DID NOT RUN` rather than zero, because a zero meaning "we did not
 * look" and a zero meaning "we looked and found nothing" are different facts.
 *
 * ---
 *
 * 🔑 **A FAILED LIST DOES NOT BLOCK THE OTHERS** — `allSettled`, never `all`. `Promise.all`
 * rejects on the first failure and the four lists that DID answer are thrown away with it,
 * which turns one dead endpoint into a blank screen. That is the page-level error state this
 * unit is arguing against.
 *
 * 🔑 **AN ABORT IS NOT A FAILURE** (§16, EH-D3). A rejection carrying `kind: 'abort'` is a
 * superseded request and leaves the entry exactly as it was.
 *
 * @param {Record<string, () => Promise<unknown[]>>} loaders  name → fetcher. **Must be stable**
 *        across renders (module scope, or `useMemo`), for the reason an effect's dependency
 *        list always requires it: an object literal rebuilt each render re-fetches forever,
 *        which looks exactly like a slow backend.
 * @returns {{
 *   data: Record<string, unknown[]>,
 *   failed: string[],
 *   loading: boolean,
 *   state: 'loading'|'ready'|'partial'|'error',
 *   retry: () => void,
 *   field: (name: string) => { options: unknown[], loading: boolean, loadError: unknown, onRetryLoad: () => void },
 * }}
 */
export default function useReferenceData(loaders) {
  const names = Object.keys(loaders);

  const [data, setData] = useState(() => Object.fromEntries(names.map((n) => [n, []])));
  // The rejection itself, per failed list -- so a field can say whether retrying could work
  // (`DataState` reads `retryable` off it) rather than always offering a button. The failed
  // NAMES are its keys: one fact, one state (RF -- a second `failed` array was set beside it).
  const [reasons, setReasons] = useState({});
  const failed = useMemo(() => Object.keys(reasons), [reasons]);
  const [loading, setLoading] = useState(names.length > 0);

  // Same pattern, and the same reason, as `useAsyncOptions`: a result that arrives after a
  // newer run started must not be written. A retry while a slow first attempt is still in
  // flight is exactly that race, and it is the one a user creates by clicking "Try again".
  const ticketRef = useRef(0);
  const loadersRef = useRef(loaders);

  // Assigned in an effect, never during render: writing a ref while rendering keeps a
  // discarded render's closure under concurrent rendering, and `react-hooks` reports it.
  useEffect(() => {
    loadersRef.current = loaders;
  });

  /*
   * 🔴 THE LOADING FLAG IS SET BY THE RETRY, NOT ON THE WAY INTO THE MOUNT EFFECT (`10.4`) -- AND THIS FILE
   * PASSED THE RULE BY ACCIDENT, WHICH IS WHY IT IS HERE AT ALL.
   *
   * `run` set `loading` synchronously before its first await, which is the exact shape of the eight loaders
   * `react-hooks/set-state-in-effect` flagged this phase -- and the rule reported this one CLEAN. Bisected
   * through `eslint --stdin`: an early `if (entries.length === 0) { setLoading(false); return }` that stood
   * above the reset (since removed) defeated the analysis. Remove that unrelated guard and the file was flagged;
   * put it back and it was not. So a "0 warnings" count was hiding one live instance of the class the phase was eliminating.
   * It is fixed the same way as the others rather than left standing on the linter's blind spot -- and the
   * blind spot is recorded ([F-P60]) so the count is never quoted as coverage.
   *
   * ⚠️ AND IT IS A TRAP FOR THE FIX AS WELL AS FOR THE COUNT: every other loader could have been "fixed" by
   * inserting a decoy early return. That is a suppression wearing a guard's clothes. Nothing here does that.
   *
   * Both resets were no-ops on mount -- `loading` initialises to whether there is anything to load -- so the
   * mount path simply loses them, and `retry` keeps the one that matters.
   */
  const load = useCallback(() => {
    const ticket = (ticketRef.current += 1);
    const current = loadersRef.current;
    const entries = Object.entries(current);

    if (entries.length === 0) return;

    Promise.allSettled(entries.map(([, load]) => load())).then((results) => {
      if (ticket !== ticketRef.current) return;

      const nextData = {};
      const nextReasons = {};

      results.forEach((result, index) => {
        const name = entries[index][0];

        if (result.status === 'fulfilled') {
          nextData[name] = Array.isArray(result.value) ? result.value : [];
          return;
        }

        // 🔴 A SUPERSEDED REQUEST IS NOT A FAILURE, AND NOT AN EMPTY LIST EITHER (§16, EH-D3:
        // "leaves the entry exactly as it was"). This wrote `[]` for it, so a list that had
        // loaded read as a list with nothing in it -- the lie this hook exists to prevent. The
        // entry is left out of `nextData`, and the merge below keeps what was there.
        if (result.reason?.kind === ERROR_KINDS.ABORT) return;

        nextData[name] = [];
        nextReasons[name] = result.reason;
        // 🔑 ONE SINK, AND THE CONTEXT IS A NAME. `reportError` emits `kind`, `status`,
        // `retryable` and the refused FIELD NAMES -- never the message, never the envelope.
        reportError(`reference:${name}`, result.reason);
      });

      setData((previous) => ({ ...previous, ...nextData }));
      setReasons(nextReasons);
      setLoading(false);
    });
  }, []);

  const run = useCallback(() => {
    if (Object.keys(loadersRef.current).length === 0) return;
    setLoading(true);
    load();
  }, [load]);

  useEffect(() => {
    load();
  }, [load]);

  /*
   * 🔑 THE STATE IS DERIVED, NOT STORED, so the illegal combinations cannot be reached.
   * `Members` learned this one layer down (`listState` as one value rather than two
   * booleans): four independent booleans have sixteen combinations and only four of them
   * mean anything, so the other twelve are bugs waiting for a render order to expose them.
   *
   * ⚠️ `error` IS "ALL OF THEM FAILED", NOT "ANY OF THEM DID". A screen whose every
   * reference list is gone is a screen that cannot do its job, and saying so is honest.
   * A screen missing one of five is `partial`, and blanking it would be the over-correction
   * this whole unit exists to avoid.
   */
  const state = loading
    ? 'loading'
    : failed.length === 0
      ? 'ready'
      : failed.length === names.length
        ? 'error'
        : 'partial';

  /*
   * 🔑 ONE SPREAD PER FIELD. A select fed by a reference list needs four facts -- its options,
   * whether they are still coming, whether they failed, and how to ask again -- and every screen
   * was wiring the first and dropping the other three, so a failed list rendered as "No options
   * available" (F-P54). `<SelectInput {...refs.field('banks')} />` cannot forget one.
   */
  const field = useCallback(
    (name) => ({
      options: data[name] ?? [],
      loading,
      loadError: failed.includes(name) ? (reasons[name] ?? `The ${name} list could not be loaded.`) : null,
      onRetryLoad: run,
    }),
    [data, loading, failed, reasons, run],
  );

  return { data, failed, loading, state, retry: run, field };
}
