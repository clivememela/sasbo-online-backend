/**
 * The one overflow probe, and the one way to put the page into a theme. `05.1`.
 *
 * 🔴 WHY THIS FILE EXISTS: THREE COPIES HAD ALREADY DRIFTED. `matrix.mjs`,
 * `matrix-canary.mjs` and `states.mjs` each carried their own inlined `overflowProbe`, and
 * hashing the three bodies returned three different digests. The canary therefore proved a
 * probe that neither measuring tool actually ran — which is the arc's recurring species
 * wearing a new hat: an instrument that verifies something adjacent to the thing in use.
 * One copy, imported by all three, is the only version of this that stays true.
 */

import { readFileSync } from 'node:fs';

/**
 * Every route the responsive tools sweep.
 *
 * 🔑 ONE LIST, IMPORTED. The matrix and the edge sweep must cover the same surface or the
 * pair proves less than either claims on its own — a route added to one and not the other
 * is a route whose ±1px behaviour nobody checked, reported inside a run that says "every
 * route". Kept here beside the probe so adding a route is one edit.
 */
export const ROUTES = [
  '/login', '/change-password', '/home', '/admin', '/admin/acb', '/admin/acb/debits', '/admin/acb/runs', '/admin/daily-menu',
  '/admin/monthly-menu', '/admin/maintenance', '/admin/maintenance/recruitment-channel',
  '/admin/maintenance/subscriptions', '/admin/maintenance/workplaces', '/recruiters', '/banks', '/banks/listing',
  // Sign-in's Maintenance screens: swept as nobody, they show what is needed (the signed-in states are surfaces).
  '/admin/maintenance/users', '/admin/maintenance/positions', '/admin/maintenance/roles', '/admin/maintenance/audit',
  '/members', '/members/apply', '/members/capture', '/members/recruiters',
  '/reports', '/reports/organizer-report', '/reports/forms', '/reports/locations',
  '/archives', '/archives/affiliations-other-trade-unions', '/archives/finance-houses',
  '/archives/research-information',
  // Month end `04.3`-`04.8`: the one route `/admin/monthly-menu/:statistic`, swept at each of its six addresses -- a
  // route with a parameter is a screen a person reaches only by a real value (`routeComplaints`, below).
  '/admin/monthly-menu/new-and-deleted', '/admin/monthly-menu/membership', '/admin/monthly-menu/leavers',
  '/admin/monthly-menu/branches', '/admin/monthly-menu/recruitment', '/admin/monthly-menu/representatives',
];

/**
 * A routed path as the addresses it answers: `/admin/monthly-menu/:statistic` answers any one segment where the
 * parameter is. A route's paths are letters, digits, hyphens and slashes, so nothing in one needs escaping.
 */
const answers = (routed) => new RegExp(`^${routed.replace(/:[^/]+/g, '[^/]+')}$`);

/**
 * 🔑 THE LIST ABOVE IS HELD AGAINST `App.jsx`, NOT REMEMBERED. "Every route" is a completeness
 * claim, and the floors doctrine is explicit that a completeness claim is asserted against the
 * set's own discovery: a route added to the router and not to this list is a route the matrix, the
 * edge sweep and the motion proof all skip, inside runs that each say "every route".
 *
 * A route with a parameter is swept by the addresses listed for it -- never by its pattern, which no person can visit:
 * it is covered when at least one listed address answers it, and a listed address is routed when any route answers it.
 *
 * Returns complaints; empty means the list is the router's.
 */
export function routeComplaints(appFile = 'src/App.jsx') {
  let source;
  try {
    source = readFileSync(appFile, 'utf8');
  } catch (e) {
    return [`cannot read ${appFile} to hold the route list against it (${e.message}) -- run from the client root`];
  }
  const routed = new Set();
  for (const [, path, element] of source.matchAll(/<Route\s+path="([^"]+)"\s+element=\{<(\w+)/g)) {
    // A redirect is not a page, and "/" is the layout the pages sit inside.
    if (element === 'Navigate' || path === '/') continue;
    routed.add(`/${path.replace(/^\//, '')}`);
  }
  if (routed.size < 20) return [`read only ${routed.size} routes from ${appFile} -- the scan is not seeing the router`];
  const listed = [...new Set(ROUTES)];
  const patterns = [...routed].map((r) => [r, answers(r)]);
  return [
    ...patterns.filter(([, re]) => !listed.some((l) => re.test(l))).map(([r]) => `${r} is routed in App.jsx and swept by nothing`),
    ...listed.filter((l) => !patterns.some(([, re]) => re.test(l))).map((l) => `${l} is swept and no longer routed`),
  ];
}

/**
 * The page has finished ARRIVING -- evaluate with `awaitPromise`.
 *
 * 🔴 IT REPLACES FIXED SLEEPS, WHICH WERE GUESSES ABOUT HOW FAST THIS MACHINE IS. The record
 * drawer slides in over 380ms, the card lands over 580ms and its sweep ends at 1.35s, and a probe
 * or a shutter inside them sees a panel half off the right edge (and calls it overflow) or a card
 * in mid-air (T5). So: fonts, every visible image DECODED (`complete` is metadata, not pixels), no
 * finite animation or transition running, then two frames.
 *
 * 🔑 INFINITE ANIMATIONS ARE EXCLUDED, AND THAT IS NOT A LOOPHOLE. A skeleton's shimmer and a
 * spinner never finish by design; waiting on them would wait forever on exactly the loading state.
 */
export const SETTLED = `(async () => {
  await document.fonts.ready;
  const shown = [...document.images].filter((img) => img.getClientRects().length > 0);
  // A broken image is photographed broken -- that is the truth about the page -- so a rejected
  // decode is not a reason to wait.
  await Promise.all(shown.map((img) => img.decode().catch(() => null)));
  const moving = document.getAnimations().filter((a) =>
    (a.pending || a.playState === 'running') && a.effect?.getComputedTiming().iterations !== Infinity);
  if (moving.length) return false;
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  return true;
})()`;

/**
 * What the page is wider than the REQUESTED viewport by, and which elements do it.
 *
 * 🔴 `requested` IS PASSED IN, NEVER READ FROM THE PAGE. The first version compared against
 * `window.innerWidth` and reported ZERO overflow across 350 route×width×theme combinations.
 * The zero was vacuous: under `Emulation.setDeviceMetricsOverride` with `mobile: true`,
 * Chrome applies the mobile shrink-to-fit rule, so a page wider than its viewport zooms out
 * and `innerWidth` grows to match the content. Measured with a 1200px element planted in a
 * viewport requested at 390: `scrollWidth` 1200, `innerWidth` 1200, offenders 0.
 * `scrollWidth > innerWidth` compares two numbers that move together and can never be true
 * on a phone-sized viewport — the only width anyone was worried about. [T25]
 *
 * 🔑 `innerWidth` is still reported, as EVIDENCE rather than as the yardstick: an
 * `innerWidth` that has drifted above `requested` is itself the symptom of shrink-to-fit.
 */
export const overflowProbe = (requested) => `(() => {
  const asked = ${requested};
  const offenders = [];
  // 🔑 ONE STYLE RESOLUTION PER ANCESTOR, NOT ONE PER DESCENDANT-ANCESTOR PAIR. Every element
  // walks its ancestor chain asking the same question of the same parents, so on /members
  // (~1,000 elements under #root, ~700 of them table cells) the TableScroller div alone was
  // asked ~700 times and each shell container hundreds. Measured shape: ~5,500 forced style
  // resolutions where at most ~300 distinct ancestors exist -- and this probe runs ~700 times
  // across a full sweep, on a box that has been under 1.5 GB free.
  //
  // ⚠️ IT MEMOISES ONLY, AND THAT IS DELIBERATE. The walk order, the \`p !== document.body\`
  // stop and the early \`break\` are untouched, so the "inside a horizontal scroller is
  // allowed" rule is character-for-character the rule it was. A faster top-down pass exists
  // and would RESTATE that rule rather than cache it, which is the trade this file does not
  // make -- see this module's header on three copies of one probe drifting apart.
  const overflowX = new Map();
  const overflowXOf = (p) => {
    let v = overflowX.get(p);
    if (v === undefined) { v = getComputedStyle(p).overflowX; overflowX.set(p, v); }
    return v;
  };
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    // Anything inside a horizontal scroller is allowed to be wider than the page: that is
    // what the scroller is FOR. Only an element that pushes the DOCUMENT wide counts.
    let scrolled = false;
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const o = overflowXOf(p);
      if (o === 'auto' || o === 'scroll' || o === 'hidden') { scrolled = true; break; }
    }
    if (scrolled) continue;
    if (r.right > asked + 1 || r.width > asked + 1) {
      const cls = (typeof el.className === 'string' ? el.className : '').trim().split(/\\s+/).slice(0, 3).join('.');
      offenders.push(Math.round(r.right) + 'px w=' + Math.round(r.width) + ' ' + el.tagName.toLowerCase() + (cls ? '.' + cls : ''));
    }
  }
  // 🔴 A REGISTER PROMISES MORE THAN A PAGE DOES. The scroller rule above lets anything inside a
  // horizontal scroller run wide, which is right for a listing built to scroll -- and it made this
  // probe blind to the register, whose column ladder promises that its columns FIT and that Status
  // never yields (index.css \`.register\`; Table.jsx \`fit\`). At 1280 with the record closed, the members
  // table ran 103px past its own sheet, Status cut to "STA", and every sweep here reported 0. So each
  // register's scroller is measured on its own, and its count travels with the number.
  const registerOverflows = [...document.querySelectorAll('.register')].map((reg) => {
    const scroller = [reg, ...reg.querySelectorAll('*')].find((n) => {
      const o = overflowXOf(n);
      return o === 'auto' || o === 'scroll';
    });
    return scroller ? scroller.scrollWidth - scroller.clientWidth : 0;
  });
  // An offender like any other, so every consumer's existing verdict counts it without a second
  // rule to remember: matrix and edges both fail on \`offenderCount\`.
  for (const px of registerOverflows) {
    if (px > 1) offenders.push('.register table runs ' + px + 'px past its own sheet');
  }
  return {
    requested: asked,
    innerWidth: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    offenders: offenders.slice(0, 10),
    offenderCount: offenders.length,
    registerCount: registerOverflows.length,
    registerOverflow: registerOverflows.length ? Math.max(...registerOverflows) : 0,
    // 🔑 THE POPULATION TRAVELS WITH THE VERDICT, ALWAYS. "This page does not overflow" and
    // "this page is empty" are the same sentence otherwise, and an empty page is the most
    // reliably non-overflowing page there is. Carrying the count means every consumer can
    // tell a clean measurement from a vacuous one WITHOUT having to remember to ask -- which
    // is the whole reason \`edges.mjs\` reported 25 clean forced-colors rows over three blank
    // pages: nothing in the row said how much page there had been.
    elements: document.querySelectorAll('#root *').length,
  };
})()`;

/**
 * Put the page into a theme — RUN THIS BEFORE NAVIGATING, NOT AFTER.
 *
 * 🔴 BOTH HARNESSES HAD THIS BACKWARDS AND HALF OF EVERY RUN WAS A DUPLICATE. They
 * navigated, waited for the body to have children, and then toggled `.dark` on
 * `documentElement`. But that wait is satisfied by the SPA shell — `<div id="root">` exists
 * in the served HTML — so the toggle landed before React mounted, and `ThemeContext`'s
 * mount effect then re-applied the class from ITS state, which is read from `localStorage`
 * and was still `false`. The class was set and then quietly unset a few milliseconds later.
 *
 * It was invisible because the tools reported combination COUNTS. The proof was in the
 * screenshots: all six light/dark pairs from the state harness were byte-identical.
 *
 * `index.html` carries a pre-paint script that reads `localStorage` and sets the class
 * before first paint, and `ThemeContext` seeds its state from the same key. So writing the
 * key and THEN navigating makes the theme correct from the first frame, with no race to
 * lose. The class toggle is kept as well, for the case where the caller is re-theming a
 * page it is not going to reload.
 */
export const storeTheme = (dark) => `(() => {
  try { localStorage.setItem('darkMode', ${dark ? "'true'" : "'false'"}) } catch { /* private mode */ }
  document.documentElement.classList.toggle('dark', ${dark});
  return true;
})()`;

/**
 * What theme the page is ACTUALLY in, read off the rendered result.
 *
 * Not `classList.contains('dark')` alone: the class is what we set, so asking for it back
 * proves only that the assignment happened. The computed background is what the stylesheet
 * did with it, which is the fact worth recording.
 */
export const themeReadout = `(() => {
  const body = getComputedStyle(document.body);
  const root = getComputedStyle(document.documentElement);
  const TRANSPARENT = 'rgba(0, 0, 0, 0)';
  return {
    dark: document.documentElement.classList.contains('dark'),
    // A transparent body means the stylesheet has not applied yet, or the ground is being
    // painted on the root instead. Falling through to the root keeps the reading honest in
    // both cases -- and if BOTH are transparent, the caller is reading too early, which is
    // itself worth seeing rather than papering over.
    bg: body.backgroundColor !== TRANSPARENT ? body.backgroundColor : root.backgroundColor,
    ink: body.color,
  };
})()`;

/**
 * The page is ready to be MEASURED — not merely ready to be looked at.
 *
 * 🔴 EVERY CLAUSE HERE IS A ROW THAT WAS MEASURED BEFORE ITS PAGE EXISTED. The stylesheet
 * clause came first: without it one row of a 350-row matrix (`/reports` at 1024, light) was
 * measured with a transparent body, and an unstyled page has an entirely different layout, so
 * that row's "no horizontal overflow" was a fact about a page nobody will ever see.
 *
 * 🔴 AND THEN THE `#root` CLAUSE, WHICH IS THE ONE THAT MATTERED MOST. Measured 2026-09-10:
 * `edges.mjs`'s forced-colors sweep reported **0 visible controls across all 25 routes**, and
 * the cause was that all four of the ORIGINAL clauses are satisfied by a page where **React
 * has not mounted at all**:
 *
 *   fonts loaded                   ✓ nothing has asked for a font yet
 *   no `route-pending` element     ✓ — AND THIS CLAUSE WAS WORSE THAN USELESS. Before React
 *                                    mounts there is no Suspense fallback EITHER, so the
 *                                    absence of the pending marker reads as "arrived" when it
 *                                    actually means "has not started".
 *   body has children              ✓ `<div id="root">` is in the served HTML
 *   body background is painted     ✓ index.html's pre-paint script and the CSS do that before
 *                                    a single component renders
 *
 * Confirmed by census: `{"matched":0,"rootKids":0,"firstBox":"none"}` on `/login`, `/home` and
 * `/admin` while `pageReady` returned `true` and the harness reported `landed=true`. The fourth
 * route onward worked, because by then the chunks were cached — **so this failure is a COLD
 * CACHE failure, which is why it hid**: any tool that happened to warm the browser first, or
 * that slept after landing, saw a populated page and agreed the predicate was fine.
 *
 * 🔑 THE CLAUSE IS `#root` HAVING CHILDREN, i.e. the application has rendered something. It is
 * the only one of the five that cannot be satisfied by the empty shell, and it is deliberately
 * a floor on the APP rather than on any one route's content — a per-route selector would be a
 * second thing to keep in step with 25 routes.
 */
/*
 * 🔴 AND EVERY CLAUSE IS NULL-SAFE, BECAUSE MID-NAVIGATION THERE IS NO `document.body`.
 *
 * `document.body.children.length` and `getComputedStyle(document.body)` both THROW on a document
 * that has not built its body yet — which is the state a poll started immediately after
 * `Page.navigate` can catch. This was latent from the day the predicate was written and invisible
 * because the old hand-copied `evaluate()` in four of six tools SWALLOWED page exceptions and
 * returned `undefined`, which the readiness loops read as "not ready yet" and simply retried.
 *
 * 🔑 THE STRICT `evaluate` IN `cdp.mjs` FOUND IT WITHIN ONE RUN. Once a thrown expression became
 * a real error instead of a silent `undefined`, `edges` and `contrast` both aborted with
 * `Cannot read properties of null (reading 'children')` — the fifth defect the refactor pass
 * turned up, and the clearest possible argument for having stopped swallowing exceptions.
 */
/**
 * 🔑 ONE FLOOR FOR "A PAGE": what readiness waits for and what the population canary below complains under (RF, F-P41).
 * Readiness used to ask for ONE child of `#root` while the canary complained under EIGHT elements, so a row could pass
 * readiness and be disowned afterwards. Now a page is ready only once it holds as much as the canary asks of it.
 */
export const PAGE_FLOOR = 8;

export const pageReady = `(() => (
  document.fonts.status === 'loaded'
  && !document.querySelector('[data-testid="route-pending"]')
  && (document.body?.children.length ?? 0) > 0
  && document.querySelectorAll('#root *').length >= ${PAGE_FLOOR}
  && (document.body ? getComputedStyle(document.body).backgroundColor : 'rgba(0, 0, 0, 0)') !== 'rgba(0, 0, 0, 0)'
))()`;

/** Where a row was taken, for a complaint message. Rows are shaped differently per tool. */
const rowWhere = (r) => `${r.route ?? r.name ?? '?'}@${r.width ?? r.requested ?? '?'}`;

/**
 * 🔑 THE PAGE-POPULATION CANARY. Given every row a run produced, complain about any that was
 * taken against a page with almost nothing in it.
 *
 * A row with 3 elements under `#root` is not evidence about a layout; it is evidence that the
 * tool measured before the application rendered. `edges.mjs` shipped 25 such rows and called
 * them clean, and nothing in its output could have shown that — see `pageReady` above for the
 * five conditions a bare SPA shell satisfies.
 *
 * The floor is deliberately LOW (a non-vacuity canary, not a coverage target — the same rule
 * `src/test/floors.js` states for the suite's own floors). The lightest real route in this
 * application renders 11 elements at its emptiest, so 8 separates "a page" from "no page"
 * without reddening for a route that is legitimately sparse.
 *
 * 🔴 A ROW WITH NO `elements` FIELD IS A COMPLAINT, NOT A SKIP — AND THE FIRST VERSION HAD
 * THAT BACKWARDS. It read `typeof r.elements === 'number' && r.elements < floor`, so a row
 * that never carried the field simply fell out of the filter and the run came back clean.
 * Two live cases, both in the commit that introduced this function: `contrast.mjs`, whose rows
 * come from axe and never touch `overflowProbe`, and `states.mjs`'s never-ready path, which
 * hand-built a row listing eight fields and omitted this one. **The vacuity checker was
 * itself vacuous**, which is the exact species it exists to catch, one level up.
 */
export function populationComplaints(rows, floor = PAGE_FLOOR) {
  const complaints = [];

  const missing = rows.filter((r) => typeof r.elements !== 'number');
  if (missing.length) {
    complaints.push(
      `${missing.length} of ${rows.length} rows carry no \`elements\` count ` +
        `(${missing.slice(0, 5).map(rowWhere).join(', ')}${missing.length > 5 ? ', …' : ''}), so ` +
        `this canary cannot tell whether they were measured against a real page. Give the row ` +
        `an element census — do NOT let it fall out of the check, which is how the check ` +
        `becomes the thing it was written to catch.`,
    );
  }

  const empty = rows.filter((r) => typeof r.elements === 'number' && r.elements < floor);
  if (empty.length) {
    complaints.push(
      `${empty.length} of ${rows.length} rows were measured against a page with fewer than ` +
        `${floor} elements under #root (${empty.slice(0, 5).map((r) => `${rowWhere(r)}=${r.elements}`).join(', ')}` +
        `${empty.length > 5 ? ', …' : ''}) — the application had not rendered, so those rows are ` +
        `facts about a blank page. An empty page never overflows.`,
    );
  }

  return complaints;
}

/**
 * 🔑 THE THEME CANARY. Given every row a run produced, prove the two themes rendered
 * DIFFERENTLY. A run whose dark rows are pixel-identical to its light rows has measured one
 * theme twice and reported it as two, and every "× 2 themes" claim built on it is inflated.
 *
 * Returns a list of complaints; empty means the dimension is real.
 */

export function themeDimensionComplaints(rows) {
  const complaints = [];
  const light = rows.filter((r) => r.theme === 'light' && r.theme_bg);
  const dark = rows.filter((r) => r.theme === 'dark' && r.theme_bg);

  if (!light.length || !dark.length) {
    complaints.push('no theme readouts were recorded, so the theme dimension is unproven');
    return complaints;
  }
  // A transparent ground means the stylesheet had not applied when the row was measured,
  // so that row's layout — and therefore its overflow reading — is not the real page's.
  const unstyled = rows.filter((r) => r.theme_bg === 'rgba(0, 0, 0, 0)');
  if (unstyled.length) {
    complaints.push(
      `${unstyled.length} rows were measured before the stylesheet applied ` +
        `(${unstyled.slice(0, 3).map((r) => `${r.route ?? r.name}@${r.width ?? r.requested}`).join(', ')}` +
        `${unstyled.length > 3 ? ', …' : ''}) — their layout is not the real page's`,
    );
  }
  const wrongClass = dark.filter((r) => r.theme_dark !== true);
  if (wrongClass.length) {
    complaints.push(`${wrongClass.length} dark rows did not carry the .dark class`);
  }
  const lightBgs = new Set(light.map((r) => r.theme_bg));
  const darkBgs = new Set(dark.map((r) => r.theme_bg));
  const shared = [...darkBgs].filter((bg) => lightBgs.has(bg));
  if (shared.length) {
    complaints.push(
      `light and dark rendered the SAME body background (${shared.join(', ')}) — ` +
        'the dark half of this run measured the light page',
    );
  }
  return complaints;
}

/*
 * THE TWO DRIVERS, SHARED. They lived in `states.mjs` until `review.mjs` needed them too, and a
 * second copy of a driver drifts exactly the way three copies of the overflow probe did.
 */

/**
 * Press a control that opens on `mousedown`, and say whether it was found.
 *
 * 🔴 `element.click()` DISPATCHES A `click` EVENT AND NOTHING ELSE. It does not synthesise
 * the mouse sequence a real press produces, so a control wired to `onMouseDown` never
 * hears it. `DateField` is wired that way on purpose — *"the whole field is the trigger"*,
 * the owner's complaint about the control it replaces being that only the icon opened it —
 * and the driver reported the step as simply not landing, which reads exactly like a
 * missing control. It was the driver. Playwright's `page.click()` is unaffected; this is a
 * property of raw CDP `Runtime.evaluate`, so it bites here and not in the journeys.
 */
export const mouseDownOn = (selector, pattern) => `(() => {
  const re = ${pattern};
  const el = [...document.querySelectorAll(${JSON.stringify(selector)})]
    .find((n) => re.test((n.labels?.[0]?.textContent || n.getAttribute('aria-label') || '').trim()));
  // A disabled control ignores the gesture, so it did not land -- saying so names the race instead of timing out on it.
  if (!el || el.disabled) return false;
  for (const type of ['pointerdown', 'mousedown', 'mouseup', 'click']) {
    el.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
  }
  return true;
})()`;

/** Click the first element whose visible text matches, and say whether anything matched. */
export const clickByText = (selector, pattern) => `(() => {
  const re = ${pattern};
  const el = [...document.querySelectorAll(${JSON.stringify(selector)})]
    .find((n) => re.test((n.getAttribute('aria-label') || n.textContent || '').trim()));
  if (!el || el.disabled) return false;
  el.click();
  return true;
})()`;

/*
 * THE DRIVERS AND PROBES `surfaces.mjs` (`07.2`) SHARES WITH THE OTHER TOOLS -- for the reason this module's header
 * gives. Some `review.mjs` had kept to itself (the census, typing, scrolling, the boundary), the axe call `contrast.mjs`
 * made, and the ones `07.2` and `07.3` added (labelled typing, named dialogs, the pixel sampler's targets, the touch
 * probe).
 */

/**
 * What a page that never became ready WAS showing. A readiness failure is diagnosed from this,
 * never "fixed" by running the shot again (the owner's anti-flake rule).
 */
export const PAGE_CENSUS = `(() => ({
  href: location.href,
  readyState: document.readyState,
  fonts: document.fonts.status,
  routePending: !!document.querySelector('[data-testid="route-pending"]'),
  rootChildren: document.getElementById('root')?.children.length ?? 0,
  rows: document.querySelectorAll('tr[data-row]').length,
  skeletons: document.querySelectorAll('tr[data-skeleton]').length,
  states: [...document.querySelectorAll('[data-state]')].map((n) => n.dataset.state),
  dialogs: [...document.querySelectorAll('[role="dialog"]')].map((d) => document.getElementById(d.getAttribute('aria-labelledby') || '')?.textContent || '(unnamed)'),
  text: (document.body?.innerText || '').replace(/\\s+/g, ' ').slice(0, 240),
}))()`;

export const typeInto = (selector, value) => `(() => {
  const input = document.querySelector(${JSON.stringify(selector)});
  if (!input) return false;
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)});
  input.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()`;

/**
 * Type into the field whose LABEL matches, inside a scope, the way React hears it. By label and never by position: the
 * first input in the document is the topbar's search, and a driver that typed there measured the register instead of
 * the page it named (states.mjs, `06.3`).
 */
export const typeIntoLabelled = (scope, pattern, value) => `(() => {
  const re = ${pattern};
  const scope = ${JSON.stringify(scope)};
  const input = [...document.querySelectorAll(scope + ' input, ' + scope + ' textarea')]
    .find((n) => re.test((n.labels?.[0]?.textContent || n.getAttribute('aria-label') || '').trim()));
  if (!input || input.disabled || input.readOnly) return false;
  const proto = input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value').set.call(input, ${JSON.stringify(value)});
  input.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()`;

export const scrollIntoView = (selector) => `(() => {
  const el = document.querySelector(${JSON.stringify(selector)});
  if (!el) return false;
  el.scrollIntoView({ block: 'start' });
  return true;
})()`;

/** The route's error boundary is on screen (ErrorBoundary.jsx). */
export const BOUNDARY_SHOWN = `!!document.querySelector('[data-testid="route-error"]')`;

/**
 * An open dialog whose ACCESSIBLE NAME matches. The surface is asserted PRESENT after the gesture, never inferred from
 * the gesture returning (T30) -- and by the name a screen reader announces, so a dialog that opened unnamed does not
 * count as the one that was asked for.
 */
export const dialogNamed = (pattern) => `(() => {
  const re = ${pattern};
  return [...document.querySelectorAll('[role="dialog"], [role="alertdialog"]')].some((d) => {
    const labelledBy = d.getAttribute('aria-labelledby');
    const name = (d.getAttribute('aria-label') || (labelledBy && document.getElementById(labelledBy)?.textContent) || '').trim();
    return re.test(name) && d.getClientRects().length > 0;
  });
})()`;

/**
 * Hide one node's GLYPHS -- and its descendants', with their text shadows -- or restore them, so its box can be
 * photographed without its letters (png.mjs `pixelContrast` compares that with the box as it is). Resolves after two
 * frames, so the photograph sees it.
 */
export const hideText = (selector, hide) => `(() => {
  let el = null;
  try { el = document.querySelector(${JSON.stringify(selector)}); } catch { return false; }
  if (!el) return false;
  if (!document.getElementById('__hide_text')) {
    const style = document.createElement('style');
    style.id = '__hide_text';
    // The glyphs ONLY. The colour property stays, so a border, a ring or an icon drawn in currentColor is in both
    // photographs alike and is never mistaken for a letter.
    style.textContent = '[data-hide-text], [data-hide-text] * { -webkit-text-fill-color: transparent !important; '
      + 'text-shadow: none !important; text-decoration-color: transparent !important; transition: none !important; }';
    document.head.append(style);
  }
  if (${hide ? 'true' : 'false'}) el.setAttribute('data-hide-text', ''); else el.removeAttribute('data-hide-text');
  return new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))));
})()`;

/**
 * Why `contrastTargets` could not see a node yet -- a caller scrolls to it and asks again. Shared constants, so a
 * reworded reason cannot quietly turn "not yet seen" (which is scrolled to, and fails the run if it stays unseen) into
 * an ordinary skip.
 */
export const UNSEEN = { OFF_SCREEN: 'off screen', CLIPPED: 'clipped by a scroller' };

/**
 * The nodes axe could not judge, as the pixel sampler needs them (`tools/png.mjs` `pixelContrast`, driven by
 * surfaces.mjs): each one's box, its text colour as RGBA, and the AA threshold its size and weight put it under.
 *
 * 🔴 ONLY WHAT A PERSON SEES. A node whose centre another element covers -- page text under a dialog's scrim is most of
 * what axe calls "partially obscured" -- is OCCLUDED: not measured, and never counted as passing. A node off screen, or
 * too small to hold a glyph, is SKIPPED with the reason.
 *
 * 🔑 THE COLOUR IS READ BY PAINTING IT, NOT BY PARSING IT (T28): `getComputedStyle().color` is `oklch(...)` in this
 * design, and a 1×1 canvas turns any CSS colour into the bytes that would actually be drawn.
 */
export const contrastTargets = (selectors) => `(() => {
  const canvas = document.createElement('canvas');
  canvas.width = 1;
  canvas.height = 1;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const rgba = (colour) => {
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = '#000';
    ctx.fillStyle = colour;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;
    return [r, g, b, Number((a / 255).toFixed(3))];
  };
  return ${JSON.stringify(selectors)}.map((selector) => {
    let el = null;
    try { el = document.querySelector(selector); } catch { return { selector, skipped: 'the selector does not parse here' }; }
    if (!el) return { selector, skipped: 'gone from the page' };
    const box = el.getBoundingClientRect();
    if (box.width < 4 || box.height < 4) return { selector, skipped: 'too small to hold a glyph' };
    if (box.bottom <= 0 || box.right <= 0 || box.top >= innerHeight || box.left >= innerWidth) return { selector, skipped: ${JSON.stringify(UNSEEN.OFF_SCREEN)} };
    // 🔴 CLIPPED BY A SCROLLER IS NOT ON SCREEN. A table cell scrolled out of its table's sideways box can still lie
    // inside the viewport -- and at its centre the browser finds the ancestor that clips it, which the check below
    // accepts as the element. The fourth sweep photographed 280 such boxes, each showing only its scroller's
    // background, and read the Locations dates at 1:1. Every ancestor that clips overflow bounds what can be seen.
    let seen = { left: box.left, top: box.top, right: box.right, bottom: box.bottom };
    for (let p = el.parentElement; p && p !== document.documentElement; p = p.parentElement) {
      const ps = getComputedStyle(p);
      if (ps.overflowX === 'visible' && ps.overflowY === 'visible') continue;
      const pr = p.getBoundingClientRect();
      seen = { left: Math.max(seen.left, pr.left), top: Math.max(seen.top, pr.top), right: Math.min(seen.right, pr.right), bottom: Math.min(seen.bottom, pr.bottom) };
    }
    if (seen.right - seen.left < box.width - 1 || seen.bottom - seen.top < box.height - 1) return { selector, skipped: ${JSON.stringify(UNSEEN.CLIPPED)} };
    const centre = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
    if (!centre || !(centre === el || el.contains(centre) || centre.contains(el))) return { selector, occluded: true };
    const style = getComputedStyle(el);
    const size = parseFloat(style.fontSize);
    const bold = Number(style.fontWeight) >= 700;
    return {
      selector,
      text: (el.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 40),
      box: { x: Math.max(0, box.left), y: Math.max(0, box.top), width: Math.min(box.width, innerWidth - Math.max(0, box.left)), height: Math.min(box.height, innerHeight - Math.max(0, box.top)) },
      fg: rgba(style.color),
      needs: size >= 24 || (bold && size >= 18.66) ? 3 : 4.5,
      ariaHidden: !!el.closest('[aria-hidden="true"]'),
    };
  });
})()`;

/**
 * Run axe with ONLY `color-contrast` on, and return all three buckets. Moved here from `contrast.mjs` when
 * `surfaces.mjs` (`07.3` inside `07.2`'s states) needed the same call.
 *
 * The pass count is how a run proves it examined anything: zero violations out of zero
 * examined nodes is the vacuous green every guard in this repository carries a floor against.
 *
 * 🔴 AND `resultTypes` IS DELIBERATELY ABSENT, HAVING BEEN THERE ONCE AND LIED. The first
 * version passed `resultTypes: ['violations', 'incomplete']` as an optimisation. That option
 * does not merely choose what to REPORT -- for every bucket not listed, axe keeps **one node
 * and discards the rest**. So `passCount` came back as exactly 1 per combination, 100 across
 * 100 runs, and the non-vacuity floor fired on a number that was an artefact of the
 * caller's own option rather than a fact about the pages (T26).
 *
 * 🔑 THE FLOOR CAUGHT IT, WHICH IS THE ONLY REASON IT WAS SEEN -- and it caught a defect in
 * the INSTRUMENT, not in the application, which is the third time this arc has had that exact
 * result. Without the floor the run would have reported 100 combinations swept and one
 * violation found, and the missing 4,000-odd examined nodes would have gone unnoticed
 * entirely, because a small number and a large one both read as "it ran".
 */
export const AXE_CONTRAST = `(async () => {
  const r = await window.axe.run(document, {
    runOnly: { type: 'rule', values: ['color-contrast'] },
  });
  const node = (n) => ({
    target: Array.isArray(n.target) ? n.target.join(' ') : String(n.target),
    summary: (n.failureSummary || '').replace(/\\s+/g, ' ').slice(0, 300),
    data: (n.any || []).map((c) => c.data).filter(Boolean),
  });
  return {
    violations: r.violations.flatMap((v) => v.nodes.map(node)),
    incomplete: r.incomplete.flatMap((v) => v.nodes.map(node)),
    passCount: r.passes.reduce((n, p) => n + p.nodes.length, 0),
  };
})()`;

/** What a finger can press, as the touch probe enumerates it -- one list, so the probe's two selectors cannot drift. */
const TOUCH_CONTROLS = ['a[href]', 'button', 'input', 'select', 'textarea', 'summary', '[role="button"]', '[role="tab"]',
  '[role="checkbox"]', '[role="radio"]', '[role="switch"]', '[role="combobox"]', '[role="option"]', '[role="menuitem"]'];

/**
 * How many `touch-target` hit-area overlays the page DRAWS. Under a mouse it must be 0 on every row -- desk density
 * provably untouched -- and under touch it must not be: surfaces.mjs runs this same expression as its sweep check and
 * as both halves of its canary, so the canary proves the count the sweep relies on.
 */
export const TOUCH_OVERLAYS_DRAWN = `[...document.querySelectorAll('.touch-target')].filter((el) => getComputedStyle(el, '::after').content !== 'none').length`;

/**
 * 🔴 T22 -- THE HIT AREA A FINGER GETS, NOT THE BOX THE ELEMENT DRAWS. `touch-target` (index.css) gives a control a
 * 44px target with an `::after` overlay on a coarse pointer, so `getBoundingClientRect()` -- the element's own box --
 * reports exactly the number the utility was written NOT to change. P3's probe read 373 of 489 controls "under 44px"
 * that way, and a measurement that indicts everything indicts itself.
 *
 * Two readings, because either alone can pass a broken control:
 *   ① DECLARED: the larger of the element's box and its `::after` box, per axis;
 *   ② ASKED OF THE PAGE: `elementFromPoint` at the four corners of a 44px square centred on the control must land on
 *      the control or inside it. A target another element sits over is not a target a finger gets.
 * A control is short when ① is under 44px on either axis, or ② lands somewhere else at any on-screen corner.
 *
 * ⚠️ RUN IT UNDER `Emulation.setTouchEmulationEnabled`, NEVER `setEmulatedMedia` (T6): emulated media do not emulate
 * the pointer. The probe returns whether `(pointer: coarse)` matched, and a caller that asked for touch and got a
 * mouse must abort -- the whole of T6 in one boolean.
 *
 * Exempt, as WCAG 2.5.8 exempts them: a link inside a line of text. A native checkbox or radio inside its label is
 * measured through the label, which is what a finger hits. Controls off screen are skipped, never guessed at: a
 * surface that needs them measured scrolls to them.
 *
 * 🔴 TWO CORRECTIONS FROM ITS FIRST RUN, EACH WITH A CANARY IN surfaces.mjs:
 *   ⓐ A REPLACED ELEMENT DRAWS NO `::after`. `input`, `select` and `textarea` report a computed `::after` box that is
 *     never rendered, so reading it credited a 38px select with a 100px target. They are measured by their own box.
 *   ⓑ A CONTROL WHOSE CENTRE ANOTHER ELEMENT COVERS IS NOT A SHORT TARGET, it is a control not reachable where it is:
 *     rows scrolled under a dialog's sticky footer were reported "4/4 corners land on the footer". Such a control is
 *     counted as OCCLUDED and skipped; a surface that needs it scrolls to it.
 * Each short target now says why: `undersized` (its hit area is under 44px) or `crowded` (44px, but another control
 * sits inside the square a finger covers).
 */
export const touchTargetProbe = (min = 44) => `(() => {
  const MIN = ${min};
  const SELECTOR = ${JSON.stringify(TOUCH_CONTROLS.map((s) => (s === 'input' ? 'input:not([type="hidden"])' : s)).join(', '))};
  const px = (v) => { const n = parseFloat(v); return Number.isFinite(n) ? n : 0; };
  const describe = (el) => {
    const name = (el.getAttribute('aria-label') || el.textContent || el.getAttribute('title') || el.getAttribute('placeholder') || '')
      .replace(/\\s+/g, ' ').trim().slice(0, 48);
    return el.tagName.toLowerCase() + (name ? ' "' + name + '"' : '');
  };
  let measured = 0;
  let occluded = 0;
  const REPLACED = new Set(['INPUT', 'SELECT', 'TEXTAREA']);
  // Every control, hidden inputs included: a label wrapping a hidden input as well as its field wraps two controls.
  const INTERACTIVE = ${JSON.stringify(TOUCH_CONTROLS.join(', '))};
  const short = [];
  for (const el of document.querySelectorAll(SELECTOR)) {
    const box = el.getBoundingClientRect();
    if (box.width === 0 || box.height === 0) continue;
    if (getComputedStyle(el).visibility === 'hidden' || el.closest('[aria-hidden="true"], [inert]')) continue;
    if (box.bottom <= 0 || box.right <= 0 || box.top >= innerHeight || box.left >= innerWidth) continue;
    if (el.tagName === 'A' && getComputedStyle(el).display === 'inline'
      && [...(el.parentElement?.childNodes ?? [])].some((n) => n !== el && n.nodeType === 3 && n.textContent.trim())) continue;
    // What a finger actually presses. A control inside its own <label> is pressed through the label -- a checkbox, and
    // equally a text field the label wraps (the topbar's search: a 24px input in a 38px label). Credited only when the
    // label wraps no other control, so one label cannot lend its size to two.
    const wrap = REPLACED.has(el.tagName) ? el.closest('label') : null;
    const hit = wrap && wrap.querySelectorAll(INTERACTIVE).length === 1 ? wrap : el;
    const r = hit.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    const centre = document.elementFromPoint(cx, cy);
    if (!centre || !(centre === hit || hit.contains(centre) || centre.closest('label') === hit)) { occluded += 1; continue; }
    measured += 1;
    const after = getComputedStyle(hit, '::after');
    const overlay = !REPLACED.has(hit.tagName) && after.content !== 'none' && after.position === 'absolute';
    const w = Math.max(r.width, overlay ? Math.max(px(after.width), px(after.minWidth)) : 0);
    const h = Math.max(r.height, overlay ? Math.max(px(after.height), px(after.minHeight)) : 0);
    const half = MIN / 2 - 1;
    // Each corner of the square a finger covers, hit-tested once: a corner is missed when what lies there is not the
    // control, and what lies there is what stole it.
    const corners = [[cx - half, cy - half], [cx + half, cy - half], [cx - half, cy + half], [cx + half, cy + half]]
      .filter(([x, y]) => x >= 0 && y >= 0 && x < innerWidth && y < innerHeight)
      .map(([x, y]) => document.elementFromPoint(x, y));
    const missed = corners.filter((at) => !(at && (at === hit || hit.contains(at))));
    const undersized = w < MIN - 0.5 || h < MIN - 0.5;
    const stolenBy = missed.filter(Boolean);
    const crowded = !undersized && stolenBy.some((at) => at.closest(INTERACTIVE));
    if (undersized || crowded) {
      const to = [...new Set(stolenBy.map(describe))].slice(0, 2);
      short.push((undersized ? 'undersized ' : 'crowded ') + describe(el) + ' ' + Math.round(w) + 'x' + Math.round(h)
        + (missed.length ? ' · ' + missed.length + '/' + corners.length + ' corners land on ' + (to.join(', ') || 'nothing') : ''));
    }
  }
  return { coarse: matchMedia('(pointer: coarse)').matches, measured, occluded, shortCount: short.length, short: short.slice(0, 40) };
})()`;
