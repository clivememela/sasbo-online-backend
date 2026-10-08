/**
 * The four conditions the width matrix cannot express. `05.1`.
 *
 * The matrix measures 25 routes at six widths and finds nothing. That is a statement about
 * six numbers. This file covers the rest of what `05.1` asks for:
 *
 *   ① THE EDGE SWEEP — both sides of every breakpoint, on every route. Assert only, no screenshots.
 *      🔴 A LAYOUT BUG LIVES ON ONE SIDE OF A BREAKPOINT, AND THE MATRIX SAMPLES NEITHER
 *      SIDE. Its widths are 320 · 390 · 768 · 1024 · 1349 · 1440. Two of those sit exactly
 *      ON a breakpoint, where the rule being tested has just switched — so the matrix reads
 *      the state AFTER the change and never the state 1px BEFORE it, which is where a
 *      three-column grid with no `sm:` prefix is still three columns in 639px.
 *
 *   ② 125% ROOT FONT — WCAG 1.4.4 in its cheapest form. Every `rem` grows by a quarter and
 *      the viewport does not, so a row that fitted at 100% has 25% more text in the same
 *      box. Nothing in the matrix moves the root font.
 *
 *   ③ `forced-colors: active` — Windows High Contrast. The token layer is the whole design
 *      here, and forced colours REPLACE it wholesale. What matters is not that it looks the
 *      same (it will not) but that nothing DISAPPEARS: a control whose only affordance is a
 *      background colour becomes invisible, and this app has several.
 *
 *   ④ RESIZE MID-STATE — the state check. A record or a dialog laid out at 1440 and then dragged
 *      to 390 is not the same as one opened at 390: the second is what every test does, and the
 *      first is what a user does when they narrow a window with a member open. R-RESP-O's rule:
 *      the panel keeps its member across dock ↔ drawer, and a dialog stays whole and on screen.
 *
 *   node tools/edges.mjs <baseUrl>
 */
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { launch } from './cdp.mjs';

import {
  ROUTES, SETTLED, clickByText, overflowProbe, pageReady, populationComplaints, routeComplaints, storeTheme,
} from './probe.mjs';

const baseUrl = process.argv[2] ?? 'http://127.0.0.1:4319';

/**
 * The two widths that straddle a width query: the last at which it is false, and the first at
 * which it is true (or the other way round). Which side of the number the flip sits on depends on
 * how the query is WRITTEN, and that is the whole reason this is a function.
 */
const straddle = (op, value, unit) => {
  const px = Math.round(parseFloat(value) * (unit === 'px' ? 1 : 16));
  if (op === 'min' || op === '>=' || op === '<') return [px - 1, px];
  if (op === 'max' || op === '<=' || op === '>') return [px, px + 1];
  throw new Error(`a width query compared with "${op}", which this does not know how to straddle`);
};

/**
 * The breakpoints this design actually has — READ OFF THE BUILD, not remembered.
 *
 * 🔴 THE LIST USED TO BE TYPED HERE, AND IT WAS WRONG IN FOUR PLACES. `[640, 760, 768, 1024,
 * 1280, 1536]` swept each ±1, so it measured 639 and 641 but never 640 — the first width at which
 * `sm:` applies — and 759 and 761 but never 760, the last width at which the phone rules of
 * `@media (max-width: 760px)` hold. And the topbar's `min-[1101px]:` crumbs and `Header`'s
 * `matchMedia('(max-width: 639px)')` were in no list at all. A breakpoint lives where the RULE
 * flips, and a list typed from memory drifts the moment someone writes a new one.
 *
 * So the edges come from every `@media` in the stylesheets the build emitted and every
 * `matchMedia` in the source, and each is swept on the two widths that straddle its own flip.
 * Container queries are not here on purpose: they answer to their container, not to the viewport.
 */
const discoverBreakpoints = () => {
  const found = new Map();
  const read = (query) => {
    const note = ([below, above]) => {
      const key = `${below}/${above}`;
      if (!found.has(key)) found.set(key, new Set());
      found.get(key).add(query.trim());
    };
    for (const [, op, value, unit] of query.matchAll(/\((min|max)-width\s*:\s*([\d.]+)(px|rem|em)\)/g)) note(straddle(op, value, unit));
    for (const [, op, value, unit] of query.matchAll(/\(width\s*(>=|<=|>|<)\s*([\d.]+)(px|rem|em)\)/g)) note(straddle(op, value, unit));
  };

  const stylesheets = readdirSync('dist/assets').filter((f) => f.endsWith('.css'));
  for (const file of stylesheets) {
    for (const [, query] of readFileSync(join('dist/assets', file), 'utf8').matchAll(/@media([^{]+)\{/g)) read(query);
  }
  // The breakpoints no stylesheet can see: a component that asks the viewport itself.
  const sources = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sources(path);
    return /\.(jsx?|mjs)$/.test(entry.name) && !entry.name.includes('.test.') ? [path] : [];
  });
  for (const file of sources('src')) {
    for (const [, query] of readFileSync(file, 'utf8').matchAll(/matchMedia\(\s*['"`]([^'"`]+)['"`]\s*\)/g)) read(query);
  }
  return { found, stylesheets: stylesheets.length };
};

let BREAKPOINTS;
let stylesheetCount;
try {
  ({ found: BREAKPOINTS, stylesheets: stylesheetCount } = discoverBreakpoints());
} catch (e) {
  console.error(`edges: could not read the build's breakpoints (${e.message}) — build the client first, and run from its root`);
  process.exit(1);
}
// 🔑 THE DISCOVERY HAS A FLOOR, BECAUSE A SCAN THAT READS NOTHING SWEEPS NOTHING AND PASSES.
if (!stylesheetCount || BREAKPOINTS.size < 4) {
  console.error(`edges: read ${BREAKPOINTS.size} breakpoints from ${stylesheetCount} stylesheets in dist/assets — the scan is not seeing the build`);
  process.exit(1);
}
const EDGE_WIDTHS = [...new Set([...BREAKPOINTS.keys()].flatMap((key) => key.split('/').map(Number)))].sort((a, b) => a - b);

/*
 * 🔴 `metrics()` USED TO LIVE HERE, WITH A DIFFERENT ANSWER TO "IS THIS A PHONE".
 *
 * This file's copy used `width < 500`; `matrix.mjs` and `contrast.mjs` used `width < 700`, and
 * one of the three hard-coded `deviceScaleFactor: 1`. So the matrix and this edge sweep were
 * NOT measuring the same 640±1 condition on the same 25 routes — which is the one thing a
 * matrix and an edge sweep exist to do together. It is `cdp.mjs`'s now, with one threshold.
 */

const findings = [];
const record = (kind, where, r, extra = {}) => {
  const overflows = r.scrollWidth > r.requested + 1 || r.offenderCount > 0;
  findings.push({ kind, where, ...r, ...extra, overflows });
  return overflows;
};

let cdp;

try {
  // The browser, the socket, the strict `evaluate`, the viewport rule and T23's identity
  // check all live in `cdp.mjs` -- see its header for the four defects six hand-copies of
  // this block had already drifted into.
  cdp = await launch('edges');
  const { send, evaluate, metrics, waitFor, assertBuiltClient } = cdp;

  // 🔴 THE BREAKPOINTS WERE READ FROM `dist/`, SO THE SERVER HAS TO BE SERVING `dist/`. An edge
  // list taken from one build and swept against another is T23 wearing a new hat.
  const served = await assertBuiltClient(baseUrl);
  const built = readFileSync('dist/index.html', 'utf8').match(/assets\/(index-[A-Za-z0-9_-]+\.js)/)?.[1];
  if (served !== built) {
    throw new Error(`the server at ${baseUrl} serves ${served}, but dist/ holds ${built} — the breakpoints were read from a different build than the one being swept`);
  }
  console.log('identity OK — entry script', served, '= dist/');

  /** Navigate and wait until the page is genuinely ready — and still — to measure. */
  const land = async (route) => {
    await send('Page.navigate', { url: baseUrl + route });
    return await waitFor(pageReady) && await waitFor(SETTLED, { tries: 120, awaitPromise: true });
  };
  const settle = () => waitFor(SETTLED, { tries: 120, awaitPromise: true });

  let swept = 0;
  let missed = 0;

  /**
   * One sweep: every route at every width, recorded under `kind`.
   *
   * 🔑 WRITTEN ONCE BECAUSE THE `missed` DISCIPLINE IS THE POINT OF THIS FILE. The three
   * sweeps below were the same eight lines three times, differing in the kind string, the
   * width list and one extra per-route step — and the `if (!await land(route)) { missed += 1 }`
   * clause, which is the whole reason this tool exists (a route that never became ready was
   * NOT swept, and counting it as clean is the failure the family is written against), had to
   * be re-typed in each. A fourth condition would have had to remember it again.
   */
  const sweep = async (kind, widths, beforeMeasure) => {
    for (const width of widths) {
      await metrics(width);
      for (const route of ROUTES) {
        if (!await land(route)) { missed += 1; continue; }
        if (beforeMeasure) await beforeMeasure(route, width);
        record(kind, `${width}px ${route}`, await evaluate(overflowProbe(width)));
        swept += 1;
      }
      console.log(`  ${width}: ${ROUTES.length} routes`);
    }
  };

  // Establish the origin so `storeTheme` has somewhere to write, then stay in light: an
  // edge is a LAYOUT fact, and the matrix has already covered both themes at six widths.
  await metrics(1024);
  await land('/');
  await evaluate(storeTheme(false));

  // ---------------------------------------------------------------- ① edge sweep
  console.log(`\n① edge sweep — ${ROUTES.length} routes × ${EDGE_WIDTHS.length} widths, straddling ${BREAKPOINTS.size} breakpoints read off the build`);
  for (const [pair, queries] of BREAKPOINTS) console.log(`    ${pair.padEnd(10)} ${[...queries].slice(0, 3).join(' · ')}`);
  await sweep('edge', EDGE_WIDTHS);
  if (missed) console.log(`  ⚠ ${missed} route×width combinations never became ready`);
  console.log(`  swept ${swept}`);

  // ------------------------------------------------------- ② 125% root font-size
  console.log('\n② root font-size 125% — WCAG 1.4.4');
  await sweep('125%', [390, 1024], async () => {
    await evaluate(`document.documentElement.style.fontSize = '125%'; true`);
    // The reflow is not instant, and measuring before it lands measures the old layout.
    await settle();
  });

  // -------------------------------------------- ②b 125% root font, a record docked
  // 🔴 THE CASE ② NEVER SAW. Enlarged text grows the rail and the page padding, but the record
  // panel docks by the VIEWPORT, so the register between them narrows past the width its three
  // essential columns need: at 1280 it was 480px and Status was cut by 162px, while ② -- which
  // measures with no record open -- passed. So the record is docked at each laptop width it docks
  // at, and a record that never opened is a width NOT measured, never a clean one.
  console.log('\n②b root font-size 125%, a record docked');
  for (const width of [1280, 1366, 1440, 1536]) {
    await metrics(width);
    if (!await land('/members') || !await waitFor(`document.querySelectorAll('tr[data-row]').length > 1`)) { missed += 1; continue; }
    await evaluate(`document.documentElement.style.fontSize = '125%'; true`);
    const docked = await evaluate(clickByText('[data-row-action]', '/open the record for/i')) === true
      && await waitFor(`!!document.querySelector('[data-testid="member-panel"][data-open="true"] .mcard')`)
      && await settle();
    if (!docked) { missed += 1; continue; }
    record('125% docked', `${width}px /members`, await evaluate(overflowProbe(width)));
    swept += 1;
  }
  console.log('  1280 · 1366 · 1440 · 1536');

  // ------------------------------------------------- ③ forced-colors: active
  console.log('\n③ forced-colors: active — Windows High Contrast');
  await send('Emulation.setEmulatedMedia', {
    features: [{ name: 'forced-colors', value: 'active' }],
  });
  const invisible = [];
  let controlsSeen = 0;
  await sweep('forced-colors', [1024], async (route) => {
    // 🔑 THE POINT IS NOT THAT IT LOOKS THE SAME. Forced colours replace the token layer
    // outright; what must not happen is a control losing its only affordance. A button
    // whose shape came entirely from a background colour becomes an invisible hit area.
    const seen = await evaluate(`(() => {
      const bad = [];
      let examined = 0;
      for (const el of document.querySelectorAll('button, a, [role="button"]')) {
        const r = el.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) continue;
        examined += 1;
        const s = getComputedStyle(el);
        const named = (el.getAttribute('aria-label') || el.textContent || '').trim();
        // No text, no border, no outline: nothing is left to see.
        const hasEdge = s.borderStyle !== 'none' || s.outlineStyle !== 'none';
        if (!named && !hasEdge) bad.push(el.tagName.toLowerCase() + '.' + (typeof el.className === 'string' ? el.className.split(/\\s+/)[0] : ''));
      }
      return { bad: bad.slice(0, 6), examined };
    })()`);
    controlsSeen += seen.examined;
    if (seen.bad.length) invisible.push(`${route}: ${seen.bad.join(', ')}`);
  });
  await send('Emulation.setEmulatedMedia', { features: [] });
  // 🔴 THE POPULATION, NOT JUST THE VERDICT. "0 offenders" and "the selector matched
  // nothing" print identically, and this arc has now been caught by that shape a dozen
  // times. If `controlsSeen` is small, the zero below is about an empty query.
  console.log(`  ${ROUTES.length} routes; ${controlsSeen} visible controls examined; with no name and no edge: ${invisible.length}`);
  for (const line of invisible) console.log(`    ${line}`);
  if (controlsSeen < 100) {
    console.log('  ⚠ too few controls examined for that zero to mean anything — check the selector');
    missed += 1;
  }
  // ⚠️ WHAT THIS DOES *NOT* PROVE. `Emulation.setEmulatedMedia` makes the
  // `forced-colors: active` media query MATCH; it does not install the Windows High
  // Contrast palette, so the colours on screen here are still this design's own. What is
  // being checked is therefore the STRUCTURE the app falls back to — that no control's
  // only affordance was a background fill — and not the rendered result. The rendered
  // result needs a real Windows session, and that is P8's, on `devicePass.md`.

  // ------------------------------------------------------ ④ resize mid-state
  console.log('\n④ resize mid-state — laid out wide, then narrowed');
  const resized = [];
  const ROWS = `document.querySelectorAll('tr[data-row]').length > 1`;
  const RECORD_OPEN = `!!document.querySelector('[data-testid="member-panel"][data-open="true"] .mcard')`;
  /** On screen after the resize: its left edge in view, and no wider than the phone. */
  const onScreen = (selector) => `(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return false;
    const box = el.getBoundingClientRect();
    return box.left >= -1 && box.right <= 391 && box.width <= 391;
  })()`;

  /**
   * Lay a state out at 1440, narrow the viewport to 390 WITHOUT re-mounting, and measure.
   *
   * 🔴 THE RESIZE IS THE TEST. React does not re-mount on a viewport change, so any layout the
   * surface computed once — a measured position, a captured width, a class chosen at open —
   * survives into a viewport it was never meant for.
   */
  const acrossTheEdge = async ({ label, route, open, isOpen, box, same }) => {
    await metrics(1440, 900);
    if (!await land(route) || !await waitFor(ROWS)) { resized.push(`${route} never showed real rows`); return; }
    if (await evaluate(open) !== true || !await waitFor(isOpen) || !await settle()) {
      resized.push(`${label} never opened at 1440, so nothing was resized`);
      return;
    }
    const before = same ? await evaluate(same) : null;
    await metrics(390, 844);
    if (!await settle()) resized.push(`${label} never settled after the resize`);
    const r = await evaluate(overflowProbe(390));
    const stillOpen = await evaluate(isOpen);
    const reachable = await evaluate(onScreen(box));
    const unchanged = same ? await evaluate(same) === before : true;
    record('resize', `1440 → 390 ${label}`, r, { stillOpen, reachable, unchanged });
    if (!stillOpen) resized.push(`${label} closed when the viewport narrowed`);
    if (!reachable) resized.push(`${label} is off-screen or wider than the viewport after the resize`);
    if (!unchanged) resized.push(`${label} showed different content after the resize`);
  };

  // The record: DOCKED at 1440, a DRAWER at 390, and the same member on both sides of the change.
  await acrossTheEdge({
    label: 'the member record',
    route: '/members',
    open: clickByText('[data-row-action]', '/open the record for/i'),
    isOpen: RECORD_OPEN,
    box: '[data-testid="member-panel"]',
    same: `document.querySelector('[data-testid="member-panel"] .mcard-name')?.textContent ?? null`,
  });
  // A dialog, which is still a dialog at both widths.
  await acrossTheEdge({
    label: 'the recruiters dialog',
    route: '/recruiters',
    open: clickByText('button', '/show the members recruited/i'),
    isOpen: `!!document.querySelector('[role="dialog"]')`,
    box: '[role="dialog"]',
  });
  for (const line of resized) console.log(`  ⚠ ${line}`);
  if (!resized.length) console.log('  the record and the dialog both survived the resize, on screen and within the viewport');

  writeFileSync('reports/edges.json', JSON.stringify(findings, null, 2));

  // ------------------------------------------------------------------ verdict
  // 🔴 THE POPULATION CHECK, AND THIS TOOL IS WHY IT EXISTS. Its forced-colors sweep
  // reported 25 clean rows over three pages where React had not rendered at all. Every row
  // now carries its own element count and a blank one is a FAILURE rather than a clean
  // measurement -- see `pageReady` in probe.mjs for the five conditions a bare shell met.
  const populationIssues = populationComplaints(findings);
  for (const complaint of populationIssues) console.log(`  POPULATION  ${complaint}`);
  const routeIssues = routeComplaints();
  for (const complaint of routeIssues) console.log(`  ROUTES  ${complaint}`);
  const bad = findings.filter((f) => f.overflows);
  console.log(`\n${findings.length} measurements, ${bad.length} overflowing`);
  for (const f of bad.slice(0, 30)) {
    console.log(`  ${f.kind.padEnd(14)} ${f.where}`);
    console.log(`      scroll=${f.scrollWidth} asked=${f.requested} offenders=${f.offenderCount}`);
    for (const o of f.offenders.slice(0, 4)) console.log(`      ${o}`);
  }
  if (bad.length || invisible.length || resized.length || missed || populationIssues.length || routeIssues.length) process.exitCode = 1;
} catch (e) {
  console.error('edges failed:', e.message);
  process.exitCode = 1;
} finally {
  cdp?.close();
}
