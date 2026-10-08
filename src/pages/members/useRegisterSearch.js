import { useEffect, useRef } from 'react';

import { SEARCH_PAUSE_MS, searchTerm } from './memberRegister.js';

/**
 * THE TOPBAR'S SEARCH AND THE ADDRESS BAR'S `q`, KEPT IN STEP (register row A10).
 *
 * The box is the shell's (`Header`); the register's question is the address bar's. So:
 *   - TYPING, once it pauses, goes to the address bar -- REPLACED, not pushed, so Back steps over the keystrokes to the
 *     page before -- and from the first page, as every new question does;
 *   - the address bar moving ON ITS OWN -- Back, Forward, a link, a refresh -- puts its question back in the box;
 *   - ON ARRIVAL the address bar's question wins where it has one; where it has none, the box's -- typed on another page,
 *     which brought the clerk here -- goes to the address bar when the pause comes.
 *
 * `synced` is the question the two last agreed on, so neither side's change is mistaken for the other's echo. Without the
 * shell (`setSearch` absent -- every test that mounts the page alone) the address bar alone asks.
 *
 * @param {string} search the box's text
 * @param {(text: string) => void} [setSearch] the shell's setter
 * @param {string} q the address bar's question
 * @param {(changes: object, options?: object) => void} showView writes the register's view to the address bar
 */
export default function useRegisterSearch(search, setSearch, q, showView) {
  const synced = useRef(null);

  // On arrival: the address bar's question wins where it has one.
  useEffect(() => {
    if (!setSearch) return;
    synced.current = q;
    if (q && searchTerm(search) !== q) setSearch(q);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- arrival only; what follows keeps them in step
  }, []);

  // Typing -> the address bar, once it pauses.
  useEffect(() => {
    if (!setSearch || synced.current === null) return undefined;
    const asked = searchTerm(search);
    if (asked === synced.current) return undefined;
    const pause = setTimeout(() => {
      if (asked === synced.current) return;
      synced.current = asked;
      showView({ q: asked, page: 0 }, { replace: true });
    }, SEARCH_PAUSE_MS);
    return () => clearTimeout(pause);
  }, [search, setSearch, showView]);

  // The address bar moving on its own -> the box.
  useEffect(() => {
    if (!setSearch || synced.current === null || q === synced.current) return;
    synced.current = q;
    if (searchTerm(search) !== q) setSearch(q);
  }, [q, search, setSearch]);
}
