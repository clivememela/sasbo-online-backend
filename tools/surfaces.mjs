/**
 * `07.2` — EVERY STATE-GATED SURFACE, DRIVEN; and `07.3`'s contrast and touch readings taken INSIDE them.
 *
 * 🔴 L2: A SURFACE BEHIND A GESTURE ESCAPES EVERY PASS THAT ONLY NAVIGATES. The matrix, the edge sweep and the contrast
 * sweep all land on routes. Not one of them opens a dialog, fails a request, empties a list or answers the Yes that
 * reveals a date field -- and those are the surfaces MobiZone's live pass found shipped invisible.
 *
 * Each surface is reached by steps that each report whether they landed, and is then ASSERTED PRESENT by named
 * predicates (T30) before anything is measured. A surface that was not reached is a FAILED row, never a skipped one.
 * At each reached surface, on one frame that held still and read the same twice (T5, F-P61):
 *   ① overflow -- `overflowProbe` against the width asked for (T25);
 *   ② contrast on rendered pixels -- `AXE_CONTRAST`; a violation fails, `incomplete` is listed and never passed (T1);
 *   ③ at a phone width, touch targets under REAL touch emulation, reading the `::after` hit area (T6, T22); at a desk
 *      width, under a mouse, no `touch-target` overlay is drawn at all -- desktop density provably untouched;
 *   ④ a screenshot, so every finding travels with the picture it came from.
 *
 * 🔑 THE CATALOGUE IS HELD AGAINST THE SOURCE -- the guard that an undriven surface cannot pass again. What it counts,
 * and how, is `surfaceCatalogue.mjs` `coverageComplaints` (one description, beside the code). A dialog added tomorrow
 * is a complaint tomorrow.
 *
 * 🔑 THE PROBES ARE PROVED BEFORE THE SWEEP (T1). A 1.4:1 paragraph planted INSIDE an open dialog must be caught (axe
 * sees into the portal); a 20px button with no hit area must be reported short under touch while a 20px
 * `touch-target` button beside it is not; and under a mouse that same button must draw no overlay. Any of them
 * failing aborts the run.
 *
 * ⚠️ SYNTHETIC DATA ONLY (R-SEC-H): against `serve-dist.mjs`. The output syncs through OneDrive.
 *
 *   node tools/surfaces.mjs <baseUrl> <outDir> [--only name,name] [--widths 1440,390]
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { isPhone, launch } from './cdp.mjs';
import { AXE_CONTRAST, PAGE_CENSUS, SETTLED, TOUCH_OVERLAYS_DRAWN, UNSEEN, contrastTargets, hideText, overflowProbe, pageReady, populationComplaints, storeTheme, themeDimensionComplaints, themeReadout, touchTargetProbe } from './probe.mjs';
import { decodePng, pixelContrast } from './png.mjs';
// The catalogue and the guard that holds it against src/ -- also imported by the suite (stateGatedSurfaces.structural.test.js).
import { ANY_DIALOG, HEADING, OPEN_CHANNEL, ROWS, SURFACES, UNREACHABLE, coverageComplaints } from './surfaceCatalogue.mjs';
// A surface's `session: [<permissions>]`, planted before the page's scripts run (surfaceSession.mjs).
import { storeSession } from './surfaceSession.mjs';

const args = process.argv.slice(2);
const [baseUrl, outDir] = args;
if (!baseUrl || !outDir || baseUrl.startsWith('--')) {
  console.error('usage: node tools/surfaces.mjs <baseUrl> <outDir> [--only name,name] [--widths 1440,390]');
  process.exit(2);
}
const flag = (name) => {
  const i = args.indexOf(name);
  return i === -1 ? undefined : args[i + 1];
};
const WIDTHS = (flag('--widths') ?? '1440,390').split(',').map(Number);
const only = flag('--only') ? new Set(flag('--only').split(',')) : null;
const THEMES = ['light', 'dark'];
const AXE = readFileSync('node_modules/axe-core/axe.min.js', 'utf8');

/* ── the run ─────────────────────────────────────────────────────────────────────────────────────── */

const selected = SURFACES.filter((s) => !only || only.has(s.name));
if (only) {
  const unknown = [...only].filter((n) => !SURFACES.some((s) => s.name === n));
  if (unknown.length) {
    console.error(`--only names ${unknown.join(', ')}, and no surface is called that`);
    process.exit(2);
  }
}

const coverage = coverageComplaints();
mkdirSync(outDir, { recursive: true });

const rows = [];
const canaries = [];
let cdp;

try {
  cdp = await launch('surfaces', { still: true });
  const { send, evaluate, metrics, waitFor, assertBuiltClient, intercept, steady } = cdp;
  console.log('identity OK — entry script', await assertBuiltClient(baseUrl));
  await send('Page.addScriptToEvaluateOnNewDocument', { source: AXE });

  /**
   * Measure every node axe could not judge on the PIXELS BEHIND ITS GLYPHS: its box photographed as it is and again with
   * its glyphs hidden, its glyphs put back, and the text colour composited over what the glyphs covered (png.mjs
   * `pixelContrast` says why neither one photograph nor the whole box will do).
   *
   * 🔴 EVERY NODE, NO BUDGET. The third sweep measured at most 60 a row and counted the rest -- 5,988 nodes, 2,260 on each
   * phone-width Locations row alone -- as unmeasured, which fails the run: a budget is a place for a failure to sit
   * unexamined. A node off screen is scrolled to first; one that still cannot be seen is UNMEASURED, and fails the run.
   */
  const measurePixels = async (selectors) => {
    const out = [];
    for (const selector of new Set(selectors.map(String))) {
      // Off screen, or clipped by a scroller, is not yet seen: scroll to it -- which scrolls the scroller too -- and look
      // again. One that still cannot be seen whole is UNMEASURED, and fails the run.
      const unseen = (x) => x.skipped === UNSEEN.OFF_SCREEN || x.skipped === UNSEEN.CLIPPED;
      let [t] = await evaluate(contrastTargets([selector]));
      if (unseen(t)) {
        await evaluate(`(() => { try { document.querySelector(${JSON.stringify(selector)})?.scrollIntoView({ block: 'center', inline: 'center' }); } catch { /* measured as it stands */ } return true; })()`);
        [t] = await evaluate(contrastTargets([selector]));
        if (unseen(t)) { out.push({ selector, unmeasured: `${t.skipped} after scrolling to it` }); continue; }
      }
      if (!t.box) { out.push(t); continue; }
      /*
       * 🔴 A PHOTOGRAPH IS TAKEN WHEN IT HOLDS STILL, NOT WHEN IT IS ASKED FOR. After the glyphs are hidden, the browser can
       * hand back a frame from a LOWER-RESOLUTION raster of the same box: on a settled page, with nothing animating, the
       * record's "Active" chip read 5.26 in 22 of 24 measurements and 2.67 in 2 -- its second photograph blurred
       * throughout, ring and dot included, so the sampler counted 2,151 pixels "behind the glyphs" instead of 846 and
       * compared the text with a softened gold ring. So each photograph is repeated, two frames apart, until two
       * captures are byte-identical; one that never holds still is UNMEASURED, and fails the run.
       */
      const photograph = async () => {
        let last = null;
        for (let i = 0; i < 6; i++) {
          const { data } = await send('Page.captureScreenshot', { format: 'png', clip: { ...t.box, scale: 1 }, captureBeyondViewport: false });
          if (data === last) return decodePng(Buffer.from(data, 'base64'));
          last = data;
          await evaluate('new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))))', { awaitPromise: true });
        }
        return null;
      };
      const shown = await photograph();
      if (!shown) { out.push({ ...t, unmeasured: 'its photograph never held still' }); continue; }
      if (await evaluate(hideText(selector, true), { awaitPromise: true }) !== true) { out.push({ selector, unmeasured: 'gone before its text could be hidden' }); continue; }
      let hidden;
      try {
        hidden = await photograph();
      } finally {
        await evaluate(hideText(selector, false), { awaitPromise: true });
      }
      if (!hidden) { out.push({ ...t, unmeasured: 'its photograph with the text hidden never held still' }); continue; }
      const measured = pixelContrast(shown, hidden, t.fg);
      out.push(measured ? { ...t, ...measured } : { ...t, unmeasured: 'its two photographs were not the same size' });
    }
    return out;
  };

  /*
   * 🔴 EVERY SURFACE IS PHOTOGRAPHED WITH MOTION OFF, AND WITHOUT THAT THE CAPTURES DO NOT REPEAT (T44).
   *
   * Now `launch('surfaces', { still: true })` turns it off, with the matrix's repeatable rasteriser (cdp.mjs). Before
   * that, `matrix --still` had emulated `prefers-reduced-motion: reduce` since `05.1`; this tool never did, and the
   * record panel is the arc's most animated surface -- the column travels over `--duration-panel`, the card lands
   * and sweeps, the rows stagger, the tabs slide. Measured 2026-09-17 on ONE unchanged build: two runs of the same
   * four record surfaces differed by up to 7,344 pixels, while the real change being measured moved 26. An
   * instrument whose noise floor is larger than its signal cannot answer the question it is there for, and
   * 'photograph it again' is precisely what the anti-flake rule forbids.
   *
   * ⚠️ IT IS ALSO THE MORE HONEST MEASUREMENT. Contrast and touch targets are properties of the SETTLED surface;
   * a ratio read off a frame that is still fading is a ratio of something nobody sees. The reduced-motion
   * treatment is the arc's own (06.2), not an absence of design.
   */
  /*
   * `session` is the surface's `session: [<permissions>]` (surfaceSession.mjs). Planted, like the theme, BEFORE the
   * navigation -- sessionStorage is the tab's, so the new document reads it on its first script -- and removed for a
   * surface that declares none, so no surface is measured as the previous surface's user.
   */
  const arrive = async (route, theme, width, session) => {
    await metrics(width);
    await send('Emulation.setTouchEmulationEnabled', isPhone(width) ? { enabled: true, maxTouchPoints: 5 } : { enabled: false });
    await evaluate(storeTheme(theme === 'dark'));
    await evaluate(storeSession(session));
    await send('Page.navigate', { url: baseUrl + route });
    return waitFor(pageReady, { tries: 120 });
  };

  // ── THE CANARIES, AND THE RUN DOES NOT PROCEED WITHOUT THEM ─────────────────────────────────────
  // ① contrast, INSIDE a portalled dialog.
  if (!await arrive('/recruiters', 'light', 1440) || !await waitFor(ROWS, { tries: 120 })) throw new Error('the contrast canary\'s page never became ready');
  await evaluate(OPEN_CHANNEL.run);
  if (!await waitFor(ANY_DIALOG)) throw new Error('the contrast canary could not open a dialog to plant into');
  await waitFor(SETTLED, { tries: 120, awaitPromise: true });
  await evaluate(`(() => {
    const el = document.createElement('p');
    el.id = '__surface_contrast_canary';
    el.style.cssText = 'background:#888;color:#777;font-size:14px;padding:8px';
    el.textContent = 'deliberately unreadable canary text';
    document.querySelector('[role="dialog"]').append(el);
    return true;
  })()`);
  const axeCanary = await evaluate(AXE_CONTRAST, { awaitPromise: true });
  canaries.push({ name: 'contrast inside a dialog', ok: axeCanary.violations.some((n) => n.target.includes('__surface_contrast_canary')) && axeCanary.passCount > 0 });
  // ①b the pixel sampler, on the two cases axe cannot compute: text on a GRADIENT (T1's case), one unreadable, one fine.
  await evaluate(`(() => {
    const dialog = document.querySelector('[role="dialog"]');
    for (const [id, css, text] of [
      ['__pix_bad', 'background:linear-gradient(90deg,#888,#999);color:#777', 'unreadable on a gradient'],
      ['__pix_ok', 'background:linear-gradient(90deg,#101010,#303030);color:#fff', 'readable on a gradient'],
      // The membership card's shape, which the third sweep failed at 1.4:1 though it reads plainly: SMALL text with NO
      // padding, so its box is mostly anti-aliased glyph edges -- pale pixels far from the text colour, which a sampler
      // that guesses the background from the text's own box counts as background.
      ['__pix_tight', 'background:linear-gradient(135deg,#123f48,#2b6f7c);color:#f6fbfb;padding:0;font-size:10.5px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;width:max-content', 'THE FINANCE UNION'],
      // The card's status chip: readable gold on dark metal, inside a 1px gold RING drawn as an inset box-shadow. The ring
      // lies along the box's edge, never under a glyph -- a sampler that takes the whole box as backdrop counts it.
      ['__pix_ring', 'background:linear-gradient(135deg,#0b3b45,#0b5d69);color:#f1d98a;box-shadow:inset 0 0 0 1px rgba(241,217,138,0.5);border-radius:999px;padding:4px 10px;font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;width:max-content', 'ACTIVE'],
      // Text in its own background's colour: nothing to read, so it must FAIL -- not be skipped for having changed no pixel.
      ['__pix_invisible', 'background:#2b6f7c;color:#2b6f7c', 'invisible canary text'],
    ]) {
      const p = document.createElement('p');
      p.id = id;
      p.style.cssText = 'font-size:14px;padding:8px;margin:4px 0;' + css;
      p.textContent = text;
      dialog.prepend(p);
    }
    // Locations' shape at a phone width: readable text in a narrow sideways scroller, OUTSIDE the scroller's visible
    // box but inside the viewport. At its centre the browser finds the ancestor that clips it, not the text.
    const wrap = document.createElement('div');
    // Wider than the cell, so the cell CAN be seen whole once scrolled to -- as a Locations date (84px) can in its 310px table.
    // And flex-shrink 0: the dialog it is planted in is a height-capped flex column, which squeezed an earlier
    // version of this scroller to 18px around a 29px row -- clipping the cell vertically for good.
    wrap.style.cssText = 'width:260px;overflow-x:auto;margin:4px 0;flex-shrink:0';
    const row = document.createElement('div');
    row.style.cssText = 'width:600px;display:flex;background:#101010';
    const cell = document.createElement('span');
    cell.id = '__pix_clipped';
    cell.style.cssText = 'margin-left:400px;color:#fff;font-size:14px;padding:4px;white-space:nowrap';
    cell.textContent = 'clipped but readable';
    row.append(cell);
    wrap.append(row);
    dialog.prepend(wrap);
    return true;
  })()`);
  const pixelCanary = await measurePixels(['#__pix_bad', '#__pix_ok', '#__pix_tight', '#__pix_ring', '#__pix_invisible', '#__pix_clipped']);
  const bad = pixelCanary.find((m) => m.selector === '#__pix_bad');
  const fine = pixelCanary.find((m) => m.selector === '#__pix_ok');
  const tight = pixelCanary.find((m) => m.selector === '#__pix_tight');
  canaries.push({ name: 'the pixel sampler fails unreadable text on a gradient', ok: !!bad?.ratio && bad.ratio < bad.needs });
  canaries.push({ name: 'the pixel sampler passes readable text on a gradient', ok: !!fine?.ratio && fine.ratio >= fine.needs });
  canaries.push({ name: `the pixel sampler passes small, unpadded readable text -- the membership card's shape (read ${tight?.ratio ?? tight?.skipped})`, ok: !!tight?.ratio && tight.ratio >= tight.needs });
  const ring = pixelCanary.find((m) => m.selector === '#__pix_ring');
  const invisible = pixelCanary.find((m) => m.selector === '#__pix_invisible');
  canaries.push({ name: `the pixel sampler passes readable text inside a ring -- the card's status chip (read ${ring?.ratio ?? ring?.skipped})`, ok: !!ring?.ratio && ring.ratio >= ring.needs });
  canaries.push({ name: `the pixel sampler fails invisible text rather than skipping it (read ${invisible?.ratio ?? invisible?.skipped})`, ok: !!invisible?.ratio && invisible.ratio < invisible.needs });
  const clipped = pixelCanary.find((m) => m.selector === '#__pix_clipped');
  canaries.push({ name: `the pixel sampler measures readable text its scroller had clipped, not the scroller (read ${clipped?.ratio ?? clipped?.skipped ?? clipped?.unmeasured})`, ok: !!clipped?.ratio && clipped.ratio >= clipped.needs });

  // ② touch, both halves, under real touch emulation at a phone width.
  const PLANT_TOUCH = `(() => {
    const box = document.createElement('div');
    box.id = '__touch_canary';
    // Laid out to fit a 320px phone with 56px between controls, so no control is off screen (the probe skips those) and
    // no 44px square reaches a neighbour. A first layout ran 488px wide and put the short button off screen.
    box.style.cssText = 'position:fixed;left:24px;right:24px;bottom:120px;display:flex;flex-wrap:wrap;align-items:center;column-gap:56px;row-gap:56px;z-index:2147483647';
    box.innerHTML = '<button id="__short" style="width:20px;height:20px;padding:0">s</button>'
      + '<button id="__covered" class="touch-target" style="width:20px;height:20px;padding:0">c</button>'
      + '<input id="__replaced" class="touch-target" aria-label="replaced" style="width:120px;height:30px">'
      + '<span style="position:relative;display:inline-block"><button id="__hidden" style="width:20px;height:20px;padding:0">h</button>'
      + '<span style="position:absolute;inset:-10px;background:#fff"></span></span>'
      + '<label style="display:inline-flex;align-items:center;height:48px;width:120px"><input aria-label="wrapped-tall" style="height:20px;width:100px;border:0"></label>'
      + '<label style="display:inline-flex;align-items:center;height:30px;width:120px"><input aria-label="wrapped-short" style="height:20px;width:100px;border:0"></label>';
    document.body.append(box);
    return true;
  })()`;
  if (!await arrive('/home', 'light', 390) || !await waitFor(HEADING, { tries: 120 })) throw new Error('the touch canary\'s page never became ready');
  await evaluate(PLANT_TOUCH);
  const touch = await evaluate(touchTargetProbe());
  canaries.push({ name: 'touch emulation gives a coarse pointer (T6)', ok: touch.coarse === true });
  canaries.push({ name: 'a 20px button with no hit area is reported short', ok: touch.short.some((s) => /button "s" /.test(s)) });
  canaries.push({ name: 'a 20px touch-target button is not (T22)', ok: !touch.short.some((s) => /button "c" /.test(s)) });
  canaries.push({ name: 'a 30px touch-target INPUT is reported -- a replaced element draws no ::after', ok: touch.short.some((s) => /undersized input "replaced" /.test(s)) });
  canaries.push({ name: 'a covered button is skipped as occluded, not reported short', ok: !touch.short.some((s) => /button "h" /.test(s)) && touch.occluded > 0 });
  canaries.push({ name: 'a 20px input in a 48px label is pressed through the label, and not reported', ok: !touch.short.some((s) => /input "wrapped-tall" /.test(s)) });
  canaries.push({ name: 'the same input in a 30px label is reported undersized', ok: touch.short.some((s) => /undersized input "wrapped-short" /.test(s)) });
  canaries.push({ name: 'under touch, the sweep\'s overlay count sees the planted touch-target overlay', ok: await evaluate(TOUCH_OVERLAYS_DRAWN) > 0 });
  // ③ and on a mouse, the same utility draws nothing.
  if (!await arrive('/home', 'light', 1440) || !await waitFor(HEADING, { tries: 120 })) throw new Error('the mouse canary\'s page never became ready');
  await evaluate(PLANT_TOUCH);
  canaries.push({ name: 'on a mouse, touch-target draws no overlay', ok: await evaluate(`getComputedStyle(document.getElementById('__covered'), '::after').content === 'none' && !matchMedia('(pointer: coarse)').matches`) });
  canaries.push({ name: 'on a mouse, the sweep\'s overlay count reads 0 with the planted touch-target present', ok: await evaluate(`!!document.getElementById('__covered') && (${TOUCH_OVERLAYS_DRAWN}) === 0`) });
  // ④ a verdict needs a frame that holds still (F-P61): a page that repaints on every frame gets NONE, and the same page,
  // held still, gets one. Without the second half a `steady` that never answered would pass the first.
  await evaluate(`(() => {
    const el = document.createElement('p');
    el.id = '__unsteady';
    el.style.cssText = 'position:fixed;top:0;left:0;z-index:99999;font-size:16px;padding:8px;background:#fff;color:#000';
    document.body.append(el);
    let n = 0;
    const tick = () => { if (!el.isConnected) return; el.textContent = 'unsteady canary ' + (n += 1); requestAnimationFrame(tick); };
    tick();
    return true;
  })()`);
  const unsteady = await steady(() => evaluate(AXE_CONTRAST, { awaitPromise: true }));
  await evaluate(`(() => { document.getElementById('__unsteady').remove(); return true; })()`);
  await waitFor(SETTLED, { tries: 120, awaitPromise: true });
  const heldStill = await steady(() => evaluate(AXE_CONTRAST, { awaitPromise: true }));
  canaries.push({ name: 'a page that repaints every frame gets no contrast verdict (F-P61)', ok: unsteady === null });
  canaries.push({ name: 'the same page, held still, gets one', ok: heldStill !== null && heldStill.value.passCount > 0 });
  for (const c of canaries) console.log(`canary: ${c.ok ? 'CAUGHT' : 'MISSED'} — ${c.name}`);
  if (canaries.some((c) => !c.ok)) throw new Error('a probe failed its canary; its results would be worthless (T1)');

  // ── THE SWEEP ─────────────────────────────────────────────────────────────────────────────────
  for (const surface of selected) {
    for (const width of surface.widths ?? WIDTHS) {
      for (const theme of THEMES) {
        const name = `${surface.name}-${width}-${theme}`;
        const row = { name, surface: surface.name, route: surface.route, width, theme, missed: [], elements: 0 };
        const release = surface.intercept ? await intercept(surface.intercept) : null;
        let networkTouched = false;
        try {
          if (!await arrive(surface.route, theme, width, surface.session)) row.missed.push('pageReady never held');
          else if (surface.ready && !await waitFor(surface.ready, { tries: 120 })) row.missed.push(`never ready: ${surface.ready.slice(0, 120)}`);
          else {
            for (const s of surface.steps ?? []) {
              if (s.network !== undefined) {
                networkTouched = true;
                await send('Network.enable');
                await send('Network.emulateNetworkConditions', { offline: s.network, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
              } else if (await evaluate(s.run) !== true) { row.missed.push(`step did not land: ${s.what}`); break; }
              if (s.until && !await waitFor(s.until)) { row.missed.push(`step landed and never took effect: ${s.what}`); break; }
            }
          }
          if (!row.missed.length && !await waitFor(SETTLED, { tries: 120, awaitPromise: true })) row.missed.push('never settled');
          if (!row.missed.length) {
            const broken = [];
            for (const [claim, expression] of Object.entries(surface.present ?? {})) if (await evaluate(expression) !== true) broken.push(claim);
            if (broken.length) row.missed.push(`not present: ${broken.join(', ')}`);
          }
          if (row.missed.length) {
            row.diagnosis = await evaluate(PAGE_CENSUS).catch((e) => `the census threw: ${e.message}`);
          } else {
            // The readout, the probes and the shutter on ONE frame, which held still and read the same twice (F-P61).
            const held = await steady(async () => ({
              ...await evaluate(`({ paint: ${themeReadout}, probe: ${overflowProbe(width)} })`),
              contrast: await evaluate(AXE_CONTRAST, { awaitPromise: true }),
              targets: isPhone(width)
                ? await evaluate(touchTargetProbe())
                : { overlaysOnMouse: await evaluate(TOUCH_OVERLAYS_DRAWN) },
            }));
            if (!held) {
              row.missed.push('its readings never held still on one frame (F-P61)');
              row.diagnosis = await evaluate(PAGE_CENSUS).catch((e) => `the census threw: ${e.message}`);
            } else {
              const { paint, probe, contrast, targets } = held.value;
              Object.assign(row, {
                reached: true, elements: probe.elements, theme_dark: paint.dark, theme_bg: paint.bg,
                overflow: { count: probe.offenderCount, offenders: probe.offenders },
                contrast,
                targets,
              });
              if (isPhone(width) && !targets.coarse) row.missed.push('asked for touch and the page had a mouse (T6)');
              row.file = `${name}.png`;
              writeFileSync(join(outDir, row.file), Buffer.from(held.frame, 'base64'));
              // Every node axe could not judge, judged on the pixels behind it -- incomplete is never a pass (T1). LAST, because
              // it scrolls: the touch probe and the shutter above see the settled page, not one scrolled to a node.
              const judged = contrast.incomplete.length ? await measurePixels(contrast.incomplete.map((n) => n.target)) : [];
              // 🔴 `pixels` TRAVELS WITH THE ROW. The second sweep built it and dropped it on the way here, so every node axe
              // could not judge was measured and then thrown away: "measured on pixels: 0" over 86 rows that had them.
              row.contrast.pixels = {
                measured: judged.filter((m) => m.ratio).length,
                occluded: judged.filter((m) => m.occluded).length,
                skipped: judged.filter((m) => m.skipped).length,
                unmeasured: judged.filter((m) => m.unmeasured).length,
                failing: judged.filter((m) => m.ratio && m.ratio < m.needs)
                  .map((m) => ({ text: m.text, ratio: m.ratio, needs: m.needs, ariaHidden: m.ariaHidden, selector: m.selector.slice(-90) })),
              };
            }
          }
        } finally {
          if (networkTouched) {
            await send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
            await send('Network.disable');
          }
          if (release) for (const problem of await release()) row.missed.push(`interception failed: ${problem}`);
        }
        rows.push(row);
        const verdict = row.missed.length ? `FAIL — ${row.missed.join('; ')}` : 'reached';
        console.log(`  ${row.missed.length ? '✗' : '·'} ${name}  ${verdict}`);
        if (row.diagnosis) console.log(`      the page was showing: ${JSON.stringify(row.diagnosis)}`);
      }
    }
  }
} catch (e) {
  console.error('surfaces failed:', e.message);
  process.exitCode = 1;
} finally {
  cdp?.close();
}

/* ── the report ──────────────────────────────────────────────────────────────────────────────────── */

writeFileSync(join(outDir, 'surfaces.json'), JSON.stringify({ canaries, coverage, unreachable: UNREACHABLE, rows }, null, 2));

const reached = rows.filter((r) => r.reached && !r.missed.length);
const unreached = rows.filter((r) => r.missed.length);
const overflowing = reached.filter((r) => r.overflow.count);
const lowContrast = reached.filter((r) => r.contrast.violations.length);
const unclear = reached.filter((r) => r.contrast.incomplete.length);
const pixelFailing = reached.filter((r) => r.contrast.pixels?.failing.length);
const pixelTotals = reached.reduce((t, r) => ({
  measured: t.measured + (r.contrast.pixels?.measured ?? 0),
  occluded: t.occluded + (r.contrast.pixels?.occluded ?? 0),
  skipped: t.skipped + (r.contrast.pixels?.skipped ?? 0),
  unmeasured: t.unmeasured + (r.contrast.pixels?.unmeasured ?? 0),
}), { measured: 0, occluded: 0, skipped: 0, unmeasured: 0 });
/*
 * 🔑 UNDERSIZED FAILS THE RUN; CROWDED IS A FINDING. WCAG 2.5.5 asks that a target be 44px -- it says nothing of two
 * targets' squares overlapping. An undersized control breaks the rule; a crowded one (44px, with a neighbour inside the
 * square a finger covers) is a usability finding each of which is fixed or filed with its reason. The two are counted
 * apart so neither hides in the other.
 */
const shortTargets = reached.filter((r) => r.targets.short?.some((s) => s.startsWith('undersized')));
const crowdedTargets = reached.filter((r) => r.targets.short?.some((s) => s.startsWith('crowded')));
const overlaysOnMouse = reached.filter((r) => r.targets.overlaysOnMouse);
const examined = reached.reduce((n, r) => n + r.contrast.passCount, 0);

console.log(`\n${selected.length} surfaces · ${rows.length} surface×width×theme rows · reached ${reached.length} · not reached ${unreached.length}`);
console.log(`overflow: ${overflowing.length} · contrast violations: ${lowContrast.length} rows · axe could not compute: ${unclear.length} rows · text nodes passing: ${examined}`);
console.log(`touch targets short at a phone width: ${shortTargets.length} rows · touch overlays drawn on a mouse: ${overlaysOnMouse.length} rows`);
console.log(`touch targets crowded at a phone width (findings, not failures): ${crowdedTargets.length} rows`);
for (const r of overflowing) console.log(`  OVERFLOW  ${r.name}\n      ${r.overflow.offenders.join('\n      ')}`);
for (const r of lowContrast) {
  console.log(`  CONTRAST  ${r.name}`);
  for (const n of r.contrast.violations.slice(0, 6)) {
    const d = n.data[0];
    console.log(`      ${d ? `${d.contrastRatio}:1 (needs ${d.expectedContrastRatio}) ${d.fgColor} on ${d.bgColor}` : n.summary}\n        ${n.target}`);
  }
}
console.log(`axe could not judge, so measured on pixels: ${pixelTotals.measured} nodes · occluded (not visible, never passed): ${pixelTotals.occluded} · skipped: ${pixelTotals.skipped} · UNMEASURED (could not be brought into view): ${pixelTotals.unmeasured}`);
for (const r of pixelFailing) {
  console.log(`  PIXELS  ${r.name}`);
  for (const f of r.contrast.pixels.failing.slice(0, 6)) console.log(`      ${f.ratio}:1 (needs ${f.needs}) "${f.text}"${f.ariaHidden ? ' [aria-hidden]' : ''}\n        ${f.selector}`);
}
for (const r of shortTargets) console.log(`  TOUCH  ${r.name}: ${r.targets.shortCount} short\n      ${r.targets.short.join('\n      ')}`);
for (const r of overlaysOnMouse) console.log(`  MOUSE  ${r.name}: ${r.targets.overlaysOnMouse} touch-target overlays drawn under a mouse`);
for (const u of UNREACHABLE) console.log(`  UNREACHABLE  ${u.site} — ${u.why}`);
for (const c of coverage) console.log(`  COVERAGE  ${c}`);
const population = populationComplaints(reached);
const themes = themeDimensionComplaints(reached);
for (const c of population) console.log(`  POPULATION  ${c}`);
for (const c of themes) console.log(`  THEME  ${c}`);
if (reached.length && examined < 200) console.log(`  ⚠ VACUOUS: only ${examined} text nodes passed across ${reached.length} rows`);

if (unreached.length || overflowing.length || lowContrast.length || pixelFailing.length || pixelTotals.unmeasured || shortTargets.length || overlaysOnMouse.length
  || coverage.length || population.length || themes.length || canaries.some((c) => !c.ok) || (reached.length && examined < 200)) {
  process.exitCode = 1;
}
