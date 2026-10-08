import { useEffect, useRef } from 'react';

/**
 * A ref that takes focus each time `value` changes while `when` holds -- the notice, summary or banner a screen puts
 * up after something went wrong. Six screens wrote this effect by hand (RF); the reasons it has this shape were
 * written at each of them and live here now.
 *
 * 🔴 FOCUSED IN AN EFFECT, NEVER IN THE HANDLER THAT SETS THE STATE. The element renders only after that state
 * re-renders the component, so at the moment the handler runs the ref is still `null` -- and optional chaining makes
 * the failure a silent no-op rather than a throw. Focus stays on the button that was pressed, which is exactly the
 * "the form did not react" experience a focused summary exists to prevent. (`Auth.test.jsx` caught the first draft.)
 *
 * ⚠️ KEYED ON THE VALUE ITSELF, NOT ON A BOOLEAN. A caller replaces its notice with a FRESH object on every attempt, so
 * a second failure moves focus again even when it says the same thing; a `hasErrors` dependency would fire once and
 * then never again.
 *
 * `when` is the caller's condition, and it is a dependency too: Recruiters focuses a failure only once no dialog is
 * open, so the moment the dialog closes over an unresolved notice is a moment to take focus.
 *
 * @template T
 * @param {T} value what the ref's element shows
 * @param {boolean} [when] whether it should take focus now -- by default, whenever there is something to show
 * @returns {import('react').RefObject<HTMLElement | null>}
 */
export default function useFocusOn(value, when = Boolean(value)) {
  const ref = useRef(null);
  useEffect(() => {
    if (when) ref.current?.focus();
  }, [value, when]);
  return ref;
}
