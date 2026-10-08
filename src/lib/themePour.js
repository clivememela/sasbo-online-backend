/**
 * The theme switch's pour -- card.html's one cinematic moment, transcribed from
 * mockups/design/core.js.
 *
 * The new appearance pours out from the control that changed it: a soft-edged iris
 * grown from the button's centre to the farthest corner of the window, feather
 * included, so the whole duration is visible and the pour completes exactly as the
 * last corner turns. The CSS half lives in index.css under "The theme switch".
 *
 * `apply(next)` must change the document SYNCHRONOUSLY -- it runs inside
 * `startViewTransition`'s callback, between the old snapshot and the new one, so a
 * change that lands a frame later is captured on neither side. The caller passes
 * `flushSync` around its React update for exactly that reason.
 *
 * Where the browser has no view transitions, where the user asked for reduced motion,
 * or where there is no control to pour from, the theme simply changes: the pour is
 * the presentation of the switch, never its mechanism.
 */
export function pourTheme({ origin, next, apply }) {
  const reduce =
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (typeof document.startViewTransition !== 'function' || reduce || !origin) {
    apply(next);
    return;
  }

  const html = document.documentElement;
  const rect = origin.getBoundingClientRect();
  const x = rect.left + rect.width / 2;
  const y = rect.top + rect.height / 2;
  const w = window.innerWidth;
  const h = window.innerHeight;
  // the pour reaches the farthest corner, feather included, exactly as its animation ends
  const feather = Math.max(w, h) * 0.3;
  const reach = Math.hypot(Math.max(x, w - x), Math.max(y, h - y)) + feather + 8;

  html.style.setProperty('--vt-x', `${Math.round(x)}px`);
  html.style.setProperty('--vt-y', `${Math.round(y)}px`);
  html.style.setProperty('--vt-feather', `${Math.round(feather)}px`);
  html.style.setProperty('--vt-end', `${Math.round(reach)}px`);
  // the soft iris needs a registered property to animate; the hard clip is the fallback
  html.classList.toggle('vt-soft', Boolean(window.CSS && CSS.registerProperty));
  // exposure follows the direction: dusk for the outgoing light page, dawn for the incoming one
  html.classList.toggle('vt-to-dark', next);
  html.classList.toggle('vt-to-light', !next);
  html.classList.add('vt-theming');

  const transition = document.startViewTransition(() => apply(next));
  const settle = () => html.classList.remove('vt-theming', 'vt-to-dark', 'vt-to-light');
  (transition.finished ?? Promise.resolve()).then(settle, settle);
}
