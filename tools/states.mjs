/**
 * The surfaces the matrix cannot reach by navigating. `05.1`.
 *
 * 🔴 A ROUTE MEASURED ON ARRIVAL IS NOT THE ROUTE. The matrix loads 25 routes at 7 widths
 * and finds no overflow — and the widest tables in this application are not on screen when
 * it looks. `LocationListing`'s ELEVEN-column table renders only after a search;
 * `AcbDebits`' results table only after a member is found; the date sheet and the member
 * record only after something is opened. Those are the surfaces most likely to overflow a
 * 390px phone, and the matrix's clean run says nothing whatsoever about them.
 *
 * ⚠️ THIS IS `07.2`'s PROBLEM ARRIVING EARLY. That unit is *"state-gated surfaces"* and it
 * exists because of exactly this gap. What is driven here is the subset `05.1` needs to
 * make its overflow claim honest — not the full journey set.
 *
 * 🔑 AND IT CLOSES A P3 BOX. *"The sheet at a real 390px"* was left unticked at P3 with the
 * reason *"jsdom has no layout; the sheet branch IS driven and asserted, but 'one-handed
 * reach' is not a jsdom question"* — and it was deferred here. This is the first time that
 * sheet has been laid out at 390 real pixels.
 *
 * 🔄 RE-AIMED AT THE CARD SHELL, 2026-09-14 (`06.3`). The member record stopped being a dialog
 * when the Card arrangement landed (`3bbeb04`) — it is the shell's third column, a drawer below
 * `xl` — and main's merge (`91668e8`) removed the Structures tab this file drove on Capture. Three
 * of the six cases were asserting a dialog, or a tab, that no longer exists. And the ACB case typed
 * into `document.querySelector('input')`, which is now the TOPBAR's search: typing there takes you
 * to the register, so the case had stopped measuring ACB Debits at all.
 */
import { mkdirSync, writeFileSync } from 'node:fs';

import { launch } from './cdp.mjs';

import {
  SETTLED, clickByText, mouseDownOn, overflowProbe, pageReady, populationComplaints, storeTheme,
  themeDimensionComplaints, themeReadout,
} from './probe.mjs';

const baseUrl = process.argv[2] ?? 'http://127.0.0.1:4319';
const shotsDir = process.argv[3];

/** Real rows. A skeleton row is `data-skeleton`, never `data-row` (Table.jsx). */
const ROWS = `document.querySelectorAll('tr[data-row]').length > 1`;
/** The Debits screen's search results, by the table's own caption. */
const SEARCH_ROWS = `[...document.querySelectorAll('main table')].some((t) => t.querySelector('caption')?.textContent === 'Members matching the ACB search' && t.querySelectorAll('tr[data-row]').length > 1)`;
const RECORD_OPEN = `!!document.querySelector('[data-testid="member-panel"][data-open="true"] .mcard')`;
const OPEN_FIRST_MEMBER = {
  what: 'open the first member',
  run: clickByText('[data-row-action]', '/open the record for/i'),
  until: RECORD_OPEN,
};

/**
 * Each state, and the drive that reaches it.
 *
 * 🔑 EVERY STEP RETURNS WHETHER IT LANDED, AND SAYS WHAT LANDING LOOKS LIKE. A step whose action
 * did not land, or whose `until` never came true, FAILS the case rather than being skipped. A
 * driver that silently no-ops measures the page it was already on and reports it clean — which
 * is this arc's recurring species with a browser attached. (`until` replaces the fixed 600ms that
 * sat between steps, which was a guess about how busy the machine is.)
 */
const STATES = [
  {
    name: 'members — record drawer open',
    route: '/members',
    ready: ROWS,
    steps: [OPEN_FIRST_MEMBER],
  },
  {
    name: 'members — record drawer, Sasbo Structures tab',
    route: '/members',
    ready: ROWS,
    steps: [
      OPEN_FIRST_MEMBER,
      {
        what: 'switch to Structures',
        run: clickByText('[data-testid="member-panel"] button', '/Sasbo Structures/i'),
        until: `/Sasbo Structures/.test(document.querySelector('[data-testid="member-panel"] button[aria-current="true"]')?.textContent || '')`,
      },
    ],
  },
  {
    // 🔄 RE-HOMED FROM CAPTURE. This case opened the Election Date on Capture's Structures tab,
    // which main removed (`4a7a67b`); Capture's one remaining date sits behind an Elected Recruiter
    // answer whose question main removed with it (F-P48), so no person can open it. The organiser
    // report has four dates and is the form filled standing up, on a phone — the case the P3
    // deferral was about.
    name: 'organiser report — the date sheet (P3 deferral)',
    route: '/reports/organizer-report',
    ready: `!!document.querySelector('input[aria-expanded]')`,
    steps: [
      {
        // The trigger is the FIELD, not a button beside it: the input carries
        // `role="combobox"`, and the calendar icon is `aria-hidden` with `tabIndex={-1}`
        // because it is decoration.
        what: 'open a date field',
        run: mouseDownOn('input[aria-expanded]', '/date/i'),
        // 🔑 THE SHEET, NOT MERELY A PANEL. `aria-modal` is `true` only on the bottom-sheet
        // branch — the popover sets it `false` — so this is what distinguishes "the picker
        // opened" from "the picker opened in its PHONE form", which is the P3 box.
        until: `!!document.querySelector('[role="dialog"][aria-modal="true"]')`,
      },
    ],
  },
  {
    name: 'locations — the 11-column listing',
    route: '/reports/locations',
    ready: `[...document.querySelectorAll('main button')].some(b => /^Search$/i.test((b.textContent || '').trim()))`,
    steps: [
      { what: 'run the search', run: clickByText('main button', '/^Search$/i'), until: `!!document.querySelector('main table tbody tr')` },
    ],
  },
  {
    name: 'acb debits — the results table',
    route: '/admin/acb/debits',
    // `main input`, never the first input in the document: that is the topbar's search.
    ready: `!!document.querySelector('main input')`,
    steps: [
      { what: 'type a surname', run: `(() => { const i = document.querySelector('main input'); if (!i) return false; const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set; s.call(i,'Nkosi'); i.dispatchEvent(new Event('input',{bubbles:true})); return true; })()` },
      // The search's own table: the unpaid list above it has rows before any search is made (`04.1`).
      { what: 'run the search', run: clickByText('main button', '/^Search$/i'), until: SEARCH_ROWS },
    ],
  },
  {
    name: 'recruiters — the member list dialog',
    route: '/recruiters',
    ready: ROWS,
    steps: [
      { what: 'open the first channel', run: clickByText('button', '/show the members recruited/i'), until: `!!document.querySelector('[role="dialog"]')` },
    ],
  },
];

const WIDTH = 390;
const results = [];
let cdp;

try {
  // The browser, the socket, the strict `evaluate`, the viewport rule and T23's identity
  // check all live in `cdp.mjs` -- see its header for the four defects six hand-copies of
  // this block had already drifted into.
  cdp = await launch('states');
  const { send, evaluate, metrics, waitFor, assertBuiltClient } = cdp;

  console.log('identity OK — entry script', await assertBuiltClient(baseUrl));
  if (shotsDir) mkdirSync(shotsDir, { recursive: true });

  /**
   * A row for a state that never became ready.
   *
   * 🔑 ONE BUILDER, SO THE TWO PATHS CANNOT DISAGREE. `elements: 0` is the load-bearing field:
   * `populationComplaints` keys off it, and a row without it is invisible to the canary rather
   * than caught by it. `0` is honest here — the state genuinely never rendered — and it is a
   * real number, so the check convicts the row instead of skipping it.
   */
  const unreachedRow = (state, theme, why) => ({
    ...state,
    theme,
    reached: false,
    missed: [why],
    overflows: false,
    offenders: [],
    offenderCount: 0,
    scrollWidth: 0,
    innerWidth: 0,
    requested: WIDTH,
    elements: 0,
  });

  // localStorage belongs to an ORIGIN, and the browser starts on about:blank -- which has
  // none. One throwaway navigation gives the first `storeTheme` somewhere to write.
  await send('Page.navigate', { url: baseUrl + '/' });
  await waitFor('document.readyState === "complete"', { tries: 60 });

  for (const state of STATES) {
    for (const theme of ['light', 'dark']) {
      // Through `cdp.mjs`'s one viewport rule, so this harness and the matrix agree about
      // what a 390px phone is.
      await metrics(WIDTH);
      // 🔴 THE THEME IS SET BEFORE THE NAVIGATION, NOT AFTER IT. Setting it after meant
      // setting it before React mounted, and `ThemeContext`'s mount effect then re-applied
      // the class from its own `localStorage`-seeded state -- so the dark half of every run
      // rendered light. `index.html`'s pre-paint script reads this key, so writing it first
      // makes the theme correct from the first frame. See `probe.mjs`.
      await evaluate(storeTheme(theme === 'dark'));
      await send('Page.navigate', { url: baseUrl + state.route });

      // 🔴 WAIT FOR WHAT THIS STATE NEEDS, not for a fixed number of milliseconds.
      // The first run reached 4 of 12, and the pattern gave the cause away: two cases
      // beginning with the SAME click disagreed. That is a race — one ran while the
      // members request was still in flight, so there was no row to click. A fixed sleep
      // makes the result depend on how busy the machine is, which is the definition of a
      // flaky measurement.
      if (!await waitFor(pageReady) || (state.ready && !await waitFor(state.ready))) {
        // No theme readout: a state that never became ready was never painted, and a row
        // with a placeholder colour would poison the theme canary below.
        //
        // 🔴 IT STILL CARRIES AN `elements` COUNT, AND IT USED NOT TO. This path hand-listed
        // eight fields and omitted that one — the single key `populationComplaints` reads —
        // so an unreached state fell straight out of the population canary and the run came
        // back clean about a row that had measured nothing. One row builder now, so a field
        // added to the measured row cannot go missing from this one.
        results.push(unreachedRow(state, theme, 'never became ready: ' + (state.ready ?? 'pageReady')));
        continue;
      }

      const missed = [];
      for (const step of state.steps) {
        if (await evaluate(step.run) !== true) { missed.push(step.what); break; }
        if (step.until && !await waitFor(step.until)) { missed.push(`${step.what} (it landed and never took effect)`); break; }
      }
      // Measured once the page has stopped moving: the drawer slides in over 380ms, and a probe
      // taken mid-slide sees a panel half off the right edge and calls it overflow (T5).
      if (!missed.length && !await waitFor(SETTLED, { tries: 120, awaitPromise: true })) missed.push('the page never settled');

      // 🔑 READ THE THEME AT THE MOMENT THE PAGE IS MEASURED. Taken on arrival it read
      // `rgba(0, 0, 0, 0)` for both themes -- the stylesheet had not applied yet -- and the
      // canary correctly called that a dead dimension. Same tick as the probe, same tick as
      // the screenshot.
      const paint = await evaluate(themeReadout);
      const r = await evaluate(overflowProbe(WIDTH));
      const reached = missed.length === 0;
      const overflows = r.scrollWidth > WIDTH + 1 || r.offenderCount > 0;
      results.push({ ...state, theme, ...r, reached, missed, overflows, theme_dark: paint?.dark, theme_bg: paint?.bg });

      if (shotsDir && reached) {
        const { data } = await send('Page.captureScreenshot', { format: 'png' });
        writeFileSync(`${shotsDir}/${state.name.replace(/\W+/g, '_')}-${theme}.png`, Buffer.from(data, 'base64'));
      }
    }
  }
} catch (e) {
  console.error('states failed:', e.message);
  process.exitCode = 1;
} finally {
  cdp?.close();
}

writeFileSync('reports/states.json', JSON.stringify(results, null, 2));

console.log(`\n${results.length} state×theme combinations at a real ${WIDTH}px\n`);
for (const r of results) {
  const flag = !r.reached ? 'NOT REACHED' : r.overflows ? 'OVERFLOWS' : 'ok';
  console.log(`  ${flag.padEnd(12)} ${r.theme.padEnd(5)} ${r.name}`);
  if (!r.reached) console.log(`      steps that did not land: ${r.missed.join(', ')}`);
  if (r.overflows) {
    console.log(`      scrollWidth=${r.scrollWidth} inner=${r.innerWidth} offenders=${r.offenderCount}`);
    for (const o of r.offenders) console.log(`      ${o}`);
  }
}

// 🔑 THE THEME DIMENSION IS PROVED, NOT ASSUMED. Every light/dark pair this harness
// produced was once byte-identical, because the theme was applied after React had already
// re-applied its own. A run that measured one theme twice must not report twelve
// combinations.
const populationIssues = populationComplaints(results);
for (const complaint of populationIssues) console.log(`  POPULATION  ${complaint}`);
const themeComplaints = themeDimensionComplaints(results);
for (const complaint of themeComplaints) console.log(`  THEME  ${complaint}`);
const themes = [...new Set(results.map((r) => `${r.theme}=${r.theme_bg}`))].sort();
console.log(`
body background by theme: ${themes.join('  ')}`);

const unreached = results.filter((r) => !r.reached);
const overflowing = results.filter((r) => r.reached && r.overflows);
console.log(`\nreached: ${results.length - unreached.length}/${results.length}   overflowing: ${overflowing.length}`);
// 🔴 A STATE THAT WAS NEVER REACHED IS NOT A PASS. It is a measurement that did not happen,
// and reporting it as clean is the failure this whole file is written against.
if (unreached.length || overflowing.length || themeComplaints.length || populationIssues.length) process.exitCode = 1;
