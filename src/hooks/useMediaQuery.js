import { useCallback, useSyncExternalStore } from 'react';

/**
 * Whether a media query matches, kept current. Moved out of `DateField` when the topbar needed the same answer
 * (`Header`'s narrow placeholder), which had been re-implemented there as state set inside an effect.
 *
 * 🔑 `useSyncExternalStore`, NOT an effect. A media query is an external store, which is
 * exactly what this hook is for: no synchronous `setState` inside an effect (which the
 * `react-hooks` rules flag, and rightly — it is a second render every mount), and no
 * window between the first paint and the listener attaching where the value can be stale.
 * The server snapshot is `false`, so a non-browser render gets the popover, not the sheet.
 */
// ⚠️ NOT CACHED IN A MODULE-LEVEL MAP, and the refactor pass proposed exactly that.
// `getSnapshot` runs at least once per render, so a form with three date fields re-parses
// this query on every keystroke -- a real, if small, waste. Caching the MediaQueryList by
// query string fixes it and was tried: it makes the component's viewport behaviour
// UNSTUBBABLE, because the cache outlives a test that replaces `window.matchMedia`, and
// `DateField.test.jsx`'s bottom-sheet case went red. DECLINED: one `matchMedia` parse per
// render is not worth a component that cannot be driven into its ≤760px state, which is
// the state the whole sheet contract lives in (contract ⑤).
export default function useMediaQuery(query) {
  const subscribe = useCallback(
    (onChange) => {
      if (typeof window === 'undefined' || !window.matchMedia) return () => {};
      const list = window.matchMedia(query);
      // `addListener` is the Safari < 14 spelling, and this back office runs on old iPads.
      if (list.addEventListener) {
        list.addEventListener('change', onChange);
        return () => list.removeEventListener('change', onChange);
      }
      list.addListener(onChange);
      return () => list.removeListener(onChange);
    },
    [query],
  );

  const getSnapshot = useCallback(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia(query).matches;
  }, [query]);

  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
