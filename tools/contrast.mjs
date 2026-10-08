/**
 * Contrast on RENDERED PIXELS, every route, both themes. `05.1`'s last measurement box, and
 * the one MobiZone ticked for a whole arc without ever taking.
 *
 * 🔴 WHY NOT jsdom, WHICH IS WHERE THE SUITE'S axe RUNS. jsdom has no canvas, so axe cannot
 * compute contrast at all -- and, crucially, it does not FAIL when it cannot: it silently
 * reports nothing. `expectNoA11yViolations` disables `color-contrast` for exactly that
 * reason and says so. A rule that always passes because it never ran is worse than an absent
 * rule, because it reads as coverage. This is the run that actually looks.
 *
 * 🔴 T1 -- A PROBE THAT MISSES GRADIENTS PASSES EVERYTHING. MobiZone's first contrast probe
 * passed their entire application because it walked `background-color` up the tree while the
 * shell's background was a gradient; it fell through to white and reported dark-on-dark as
 * `11.37:1 PASS`. Their conclusion is this file's design rule: *"a measurement tool that
 * reports what you hope to see is worse than none."*
 *
 * So this does not walk `background-color`. It runs axe-core IN A REAL BROWSER, where axe
 * composites the actual painted backdrop -- and it treats axe's THIRD bucket as a first-class
 * result rather than a pass:
 *
 *   violations  a computed ratio below the AA threshold           -> a finding
 *   passes      a computed ratio at or above it                   -> fine
 *   incomplete  axe COULD NOT COMPUTE the backdrop (image,        -> REPORTED, never counted
 *               gradient, transform, overlap)                        as a pass
 *
 * ⚠️ `incomplete` IS THE WHOLE OF T1 IN ONE WORD. It is the case where a hand-rolled probe
 * would have guessed white and printed PASS. Every incomplete node is listed with the reason
 * axe gave, so a human decides rather than a default.
 *
 * 🔴 AND THE PROBE IS PROVED BEFORE ITS RESULTS ARE TRUSTED (P7 DoD, T1). Before the sweep,
 * a deliberately unreadable element is planted on a real page and the probe must report it.
 * If the canary does not go red the run ABORTS -- a green sweep from an unproven probe is
 * the failure this whole file exists to avoid.
 *
 *   node tools/contrast.mjs <baseUrl> [--widths 1440,390] [--routes a,b]
 */
import { readFileSync, writeFileSync } from 'node:fs';

import { launch, sleep } from './cdp.mjs';

import { AXE_CONTRAST as RUN_AXE, ROUTES as ALL_ROUTES, pageReady, populationComplaints, storeTheme, themeDimensionComplaints, themeReadout } from './probe.mjs';


const args = process.argv.slice(2);
const baseUrl = args[0] ?? 'http://127.0.0.1:4319';
const flag = (name) => {
  const i = args.indexOf(name);
  return i === -1 ? undefined : args[i + 1];
};

/**
 * 1440 and 390.
 *
 * 🔑 CONTRAST IS MEASURED AT TWO WIDTHS BECAUSE IT IS NOT WIDTH-INDEPENDENT, however much it
 * looks it. A colour that only appears in a layout a breakpoint gates -- a mobile drawer, a
 * card that replaces a table row below `md` -- is simply absent from a desktop-only sweep, and
 * "every route, both themes" would be true and still have missed it.
 */
const WIDTHS = (flag('--widths') ?? '1440,390').split(',').map(Number);
const ROUTES = (flag('--routes') ?? ALL_ROUTES.join(',')).split(',');
const THEMES = ['light', 'dark'];

const AXE = readFileSync('node_modules/axe-core/axe.min.js', 'utf8');


// The axe call -- `color-contrast` only, all three buckets, and why `resultTypes` is absent (T26) -- is probe.mjs's
// `AXE_CONTRAST`, shared with surfaces.mjs (`07.3` inside `07.2`'s states).

const rows = [];
let cdp;

try {
  // The browser, the socket, the strict `evaluate`, the viewport rule and T23's identity
  // check all live in `cdp.mjs` -- see its header for the four defects six hand-copies of
  // this block had already drifted into.
  cdp = await launch('contrast');
  const { send, evaluate, metrics, waitFor, assertBuiltClient } = cdp;

  // T23: prove the server is the built client before believing a single colour off it.
  console.log('identity OK — entry script', await assertBuiltClient(baseUrl));

  /*
   * 🔑 axe IS INSTALLED ONCE, BROWSER-SIDE, NOT PUSHED DOWN THE WIRE 97 TIMES.
   *
   * `axe.min.js` is 580 KB, and the `Runtime.evaluate` frame carrying it is 603 KB. Injecting
   * it per combination sent **55.8 MB** through the WebSocket per run and churned ~1.2 MB of
   * transient garbage per call in this process — on a box that has been under 1.5 GB free.
   *
   * `Page.addScriptToEvaluateOnNewDocument` is exactly the mechanism for this: Chrome holds the
   * source and evaluates it on every new document, so one 603 KB frame replaces 97.
   *
   * ⚠️ RE-EVALUATING IT PER DOCUMENT IS REQUIRED, NOT WASTE. `Page.navigate` builds a new
   * realm, so axe genuinely does not survive a navigation — and SPA-navigating inside one
   * document to avoid that would trade the cold-cache honesty `pageReady` exists to protect
   * (see probe.mjs) for speed. The per-document parse stays; only the transmission goes.
   */
  await send('Page.addScriptToEvaluateOnNewDocument', { source: AXE });

  /** Navigate, settle, and confirm axe arrived. One path for the canary and the sweep. */
  const openForMeasurement = async (route, theme, width) => {
    await metrics(width);
    // Before the navigation -- see probe.mjs. Applied after, it lands before React mounts and
    // ThemeContext's mount effect puts it back, so every "dark" row is the light page again.
    await evaluate(storeTheme(theme === 'dark'));
    await send('Page.navigate', { url: baseUrl + route });

    const ready = await waitFor(pageReady, { tries: 120 });
    // 🔴 T5: NEVER MEASURE MID-TRANSITION. The shell animates the drawer and the theme swap
    // over 300ms, and a colour sampled halfway through a transition is a colour that exists
    // for one frame and is on no design's palette.
    await sleep(500);
    // Still asserted per document. The install above is a promise about every FUTURE document;
    // this is the check that THIS one has it, and it is the difference between "we asked for
    // axe" and "axe is here".
    if (await evaluate('typeof window.axe === "object"') !== true) {
      throw new Error(`axe did not arrive on ${route} — the on-new-document install did not run`);
    }
    return ready;
  };

  // ── THE CANARY, AND THE RUN DOES NOT PROCEED WITHOUT IT ─────────────────────────────
  //
  // A known-broken case, planted on a real page in the real theme, measured through the
  // same `RUN_AXE` the sweep uses. Two halves, because either alone can be satisfied by a
  // broken probe: the deliberate failure must be SEEN, and the page's own nodes must still
  // be examined (a probe that reports one violation and looked at nothing else is equally
  // useless).
  await openForMeasurement('/home', 'light', 1440);
  await evaluate(`(() => {
    const el = document.createElement('p');
    el.id = '__contrast_canary';
    // #777 on #888: a ratio of about 1.4:1, unambiguously below AA's 4.5:1, and chosen
    // rather than a token so the canary cannot be fixed by a palette change.
    el.style.cssText = 'background:#888;color:#777;font-size:14px;padding:8px;position:relative;z-index:9999';
    el.textContent = 'deliberately unreadable canary text';
    document.body.appendChild(el);
    return true;
  })()`);
  const canary = await evaluate(RUN_AXE, { awaitPromise: true });
  const caughtCanary = canary.violations.some((n) => n.target.includes('__contrast_canary'));
  console.log(
    `canary: ${caughtCanary ? 'CAUGHT' : 'MISSED'} — ` +
    `${canary.violations.length} violation(s), ${canary.passCount} node(s) passed`,
  );
  if (!caughtCanary) {
    throw new Error(
      'the contrast probe did not report a 1.4:1 element that was planted for it. Its ' +
      'results are worthless -- a probe that reports what you hope to see is worse than none (T1).',
    );
  }
  if (canary.passCount === 0) {
    throw new Error(
      'the probe caught the canary and examined nothing else. It is not sweeping the page.',
    );
  }

  // ── THE SWEEP ───────────────────────────────────────────────────────────────────────
  for (const width of WIDTHS) {
    for (const theme of THEMES) {
      for (const route of ROUTES) {
        const ready = await openForMeasurement(route, theme, width);
        // The readout and the census on one tick, before the (slow) axe run. `elements` is
        // what makes this tool's rows legible to `populationComplaints` — without it every row
        // here fell out of that check, and a canary that silently skips its subject is the
        // thing this arc keeps finding. `passCount` is axe's own population and answers a
        // different question: how much TEXT it judged, not how much page there was.
        const { paint, elements } = await evaluate(
          `({ paint: ${themeReadout}, elements: document.querySelectorAll('#root *').length })`,
        );
        const r = await evaluate(RUN_AXE, { awaitPromise: true });
        rows.push({
          width, theme, route, ready, elements,
          violations: r.violations, incomplete: r.incomplete, passCount: r.passCount,
          theme_dark: paint?.dark, theme_bg: paint?.bg,
        });
        if (!ready) console.log(`  ⚠ NEVER READY  ${width} ${theme} ${route}`);
      }
      console.log(`  ${width} ${theme}: ${ROUTES.length} routes measured`);
    }
  }
} catch (e) {
  console.error('contrast failed:', e.message);
  process.exitCode = 1;
} finally {
  cdp?.close();
}

writeFileSync('reports/contrast.json', JSON.stringify(rows, null, 2));

const failing = rows.filter((r) => r.violations.length);
const unclear = rows.filter((r) => r.incomplete.length);
const examined = rows.reduce((n, r) => n + r.passCount, 0);

console.log(`\nmeasured ${rows.length} route×width×theme combinations on rendered pixels`);
console.log(`text nodes examined and PASSING: ${examined}`);
console.log(`combinations with a violation:   ${failing.length}`);
console.log(`combinations axe could not fully compute: ${unclear.length}`);

for (const r of failing.slice(0, 40)) {
  console.log(`\n  ✗ ${r.width} ${r.theme} ${r.route}`);
  for (const n of r.violations.slice(0, 6)) {
    const d = n.data[0];
    console.log(
      `      ${d ? `${d.contrastRatio}:1 (needs ${d.expectedContrastRatio}) ${d.fgColor} on ${d.bgColor}` : n.summary}` +
      `\n        ${n.target}`,
    );
  }
}
// Listed, never counted as a pass. This is the bucket a hand-rolled probe would have
// guessed white for and printed PASS -- which is T1 exactly.
for (const r of unclear.slice(0, 10)) {
  console.log(`\n  ? ${r.width} ${r.theme} ${r.route} — axe could not compute the backdrop for:`);
  for (const n of r.incomplete.slice(0, 4)) console.log(`        ${n.target}  ${n.summary.slice(0, 140)}`);
}

const populationIssues = populationComplaints(rows);
for (const complaint of populationIssues) console.log(`  POPULATION  ${complaint}`);
const themeComplaints = themeDimensionComplaints(rows);
for (const complaint of themeComplaints) console.log(`  THEME  ${complaint}`);
console.log(`body background by theme: ${[...new Set(rows.map((r) => `${r.theme}=${r.theme_bg}`))].sort().join('  ')}`);

// 🔑 A NON-VACUITY FLOOR ON THE SWEEP ITSELF. Zero violations across 100 combinations is
// only good news if the combinations contained text. axe examining fewer than a few hundred
// nodes across 25 routes means it ran against shells, not pages.
if (examined < 200) {
  console.log(`\n  ⚠ VACUOUS: only ${examined} text nodes were examined across ${rows.length} combinations.`);
  process.exitCode = 1;
}
if (failing.length || rows.some((r) => !r.ready) || themeComplaints.length || populationIssues.length) {
  process.exitCode = 1;
}
