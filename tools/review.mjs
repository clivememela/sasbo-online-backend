/**
 * THE FINISH REVIEW'S CAPTURES, TAKEN IN A STATE THAT IS ASSERTED. `06.3`.
 *
 * `impeccable-finish-reviewer` has no browser. It judges the build from these files, and its first
 * check refuses a review built on a capture that is blank, mid-load or of the wrong state -- so a
 * shot here is not "navigate, wait, shoot". Each one is:
 *
 *   ① taken at the viewport it names, and the viewport is MEASURED before the shutter (T14, T25);
 *   ② in the theme it names, written BEFORE the navigation and read back off the painted ground
 *      (`storeTheme` / `themeReadout`, probe.mjs);
 *   ③ ready by `pageReady` AND by a condition of its own page -- real rows, never skeletons;
 *   ④ driven by steps that each report whether they landed, and a step that did not FAILS the
 *      shot instead of photographing the page it was already on;
 *   ⑤ SETTLED before the shutter: fonts, every visible image decoded (`complete` is metadata,
 *      not pixels), and no finite animation or transition still running -- the card's landing is
 *      580ms and its sweep ends at 1.35s, and a capture inside them reviews a frame nobody sees;
 *   ⑥ checked AFTER it settles for the thing its name claims. "The record is open" is a
 *      predicate on the panel, not a filename.
 *
 * 🔑 THE REGISTER'S FAILURE, EMPTINESS, LOADING AND PARTIAL STATES ARE REACHED BY INTERCEPTING
 * THE REQUEST IN THE BROWSER. The stub server answers every path the same way every time, and
 * those four are the states A27 and EH-D9 are about.
 *
 * ⚠️ SYNTHETIC DATA ONLY (R-SEC-H). The output directory syncs through OneDrive, so this runs
 * against `serve-dist.mjs`, never against a backend holding members.
 *
 *   node tools/review.mjs <baseUrl> <outDir> [--only members-open-desk,login-laptop]
 */
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';

import { launch } from './cdp.mjs';
import {
  BOUNDARY_SHOWN, PAGE_CENSUS, SETTLED, clickByText, mouseDownOn, overflowProbe, pageReady, populationComplaints,
  scrollIntoView, storeTheme, themeDimensionComplaints, themeReadout, typeInto,
} from './probe.mjs';

const args = process.argv.slice(2);
const [baseUrl, outDir] = args;
if (!baseUrl || !outDir || baseUrl.startsWith('--')) {
  console.error('usage: node tools/review.mjs <baseUrl> <outDir> [--only name,name]');
  process.exit(2);
}
const onlyAt = args.indexOf('--only');
const only = onlyAt === -1 ? null : new Set(args[onlyAt + 1].split(','));

/**
 * The viewports. `laptop` is the owner's own screen -- a 150%-scaled panel is 1280 × 661 CSS
 * pixels once the browser's chrome is off it -- and it is the screen the rulings were made on.
 */
const VIEWPORTS = {
  desk: { width: 1440, height: 900 },
  phone: { width: 390, height: 844 },
  laptop: { width: 1280, height: 661 },
};
const BOTH = ['light', 'dark'];
const LIGHT = ['light'];

/* ── what a page is ready for, beyond `pageReady` ──────────────────────────────────────────── */

/** Real rows. A skeleton row is `data-skeleton`, never `data-row` (Table.jsx). */
const ROWS = `document.querySelectorAll('tr[data-row]').length > 1`;
const HEADING = `!!document.querySelector('main h1')`;
const TEXT = (s) => `document.body.innerText.includes(${JSON.stringify(s)})`;
const PANEL_OPEN = `!!document.querySelector('[data-testid="member-panel"][data-open="true"] .mcard')`;
const PANEL_SHUT = `!document.querySelector('[data-testid="member-panel"][data-open="true"]')`;

/** Ruling ③: the breadcrumb is never under the search. */
const CRUMBS_CLEAR_OF_SEARCH = `(() => {
  const trail = document.querySelector('header nav');
  const search = document.querySelector('header input[type="search"]')?.closest('label');
  if (!trail || !search) return false;
  return trail.getBoundingClientRect().right <= search.getBoundingClientRect().left + 0.5;
})()`;

/*
 * 🔑 THE FINISH REVIEW'S FINDINGS, AS MEASUREMENTS (`06.3`). A reviewer reads a picture; these read
 * the page the picture was taken of, so a fix is proved by the capture rather than argued from it.
 */

/** Ruling ③: the current page's crumb is whole, never an ellipsis. */
const CRUMB_WHOLE = `(() => {
  const current = document.querySelector('header nav [aria-current="page"]');
  return !!current && current.scrollWidth <= current.clientWidth;
})()`;

/** Where the topbar has the room (1440 with a record open), "Home" is shown and whole too. */
const HOME_CRUMB_WHOLE = `(() => {
  const home = document.querySelector('header nav a.crumb-lead');
  return !!home && home.getClientRects().length > 0 && home.scrollWidth <= home.clientWidth;
})()`;

/** Ruling ③: no figure's label runs past its own column. */
const FIGURES_FIT = `[...document.querySelectorAll('[data-testid="figure-strip"] dt')].every((dt) => dt.scrollWidth <= dt.clientWidth)`;

/** No figure pretends to be a reading when the register was not read (K1). */
const NO_ZERO_FIGURES = `![...document.querySelectorAll('[data-testid="figure-strip"] dd')].some((dd) => dd.textContent.trim() === '0')`;

/** The comp's criteria row: every criterion, More criteria and Clear on one baseline. */
const CRITERIA_ONE_LINE = `(() => {
  const row = document.querySelector('main section[aria-label="Filter criteria"] [data-criteria-items]');
  if (!row) return false;
  const bottoms = [...row.children].filter((el) => el.getClientRects().length).map((el) => el.getBoundingClientRect().bottom);
  return bottoms.length > 1 && Math.max(...bottoms) - Math.min(...bottoms) < 4;
})()`;

/** The card's foot: Membership no. and Employer labels on the comp's one baseline. */
const CARD_FOOT_ONE_LINE = `(() => {
  const labels = [...document.querySelectorAll('[data-testid="membership-card"] small')];
  const number = labels.find((el) => /membership no/i.test(el.textContent));
  const employer = labels.find((el) => /^employer$/i.test(el.textContent.trim()));
  return !!number && !!employer && Math.abs(number.getBoundingClientRect().top - employer.getBoundingClientRect().top) < 2;
})()`;

/** The rail's foot sits at the bottom of the rail, on every screen. */
const RAIL_FOOT_PINNED = `(() => {
  const rail = document.querySelector('nav[aria-label="Main"]');
  const appearance = rail && [...rail.querySelectorAll('button')].find((el) => /appearance/i.test(el.textContent));
  return !!appearance && rail.getBoundingClientRect().bottom - appearance.getBoundingClientRect().bottom < 64;
})()`;

/** On a phone no member's second line is cut short by a neighbouring column. */
const MEMBER_LINES_WHOLE = `[...document.querySelectorAll('tr[data-row] td[data-col="member"] .truncate')].every((el) => el.scrollWidth <= el.clientWidth)`;

/** The date sheet's days span the sheet, in the approved picker's 44px targets. */
const DAYS_SPAN_SHEET = `(() => {
  const sheet = document.querySelector('[role="dialog"][aria-modal="true"]');
  const grid = sheet && sheet.querySelector('[role="grid"]');
  if (!grid) return false;
  const cells = [...grid.querySelectorAll('[role="gridcell"]')];
  return grid.getBoundingClientRect().width >= sheet.getBoundingClientRect().width - 40
    && cells.length >= 28 && cells.every((cell) => cell.getBoundingClientRect().height >= 44);
})()`;

// ── The 06.3 critique's fixes, each claimed before it was made ───────────────────────────────

/** A colour string, as the browser resolves it, read back through a 1px canvas: [r, g, b, a]. */
const PIXEL = `((colour) => {
  const ctx = document.createElement('canvas').getContext('2d');
  ctx.clearRect(0, 0, 1, 1);
  ctx.fillStyle = 'rgba(0, 0, 0, 0)';
  ctx.fillStyle = colour;
  ctx.fillRect(0, 0, 1, 1);
  return [...ctx.getImageData(0, 0, 1, 1).data];
})`;

/**
 * Today's ring is Card's gold -- the generated `@theme inline` alias made `ring-accent` the surface wash.
 * The colour is read from `--tw-ring-color`, the variable Tailwind draws the ring with. The first version of this
 * claim read the first colour in the computed shadow list, which is one of Tailwind's empty transparent layers:
 * red before the fix and red after it, for a reason that had nothing to do with gold.
 */
const TODAY_RING_GOLD = `(() => {
  const cell = [...document.querySelectorAll('[role="gridcell"]')].find((c) => /, today/i.test(c.getAttribute('aria-label') || ''));
  if (!cell) return false;
  const style = getComputedStyle(cell);
  const colour = style.getPropertyValue('--tw-ring-color').trim();
  if (!/inset/.test(style.boxShadow) || !colour) return false;
  const [r, g, b, a] = ${PIXEL}(colour);
  const max = Math.max(r, g, b);
  return a > 0 && max > 0 && (max - Math.min(r, g, b)) / max >= 0.3 && r - b >= 40;
})()`;

/** Every Capture Member section tab on screen at a phone's width, none clipped by a scroller. */
const CAPTURE_TABS_ON_SCREEN = `(() => {
  const tabs = [...document.querySelectorAll('main button')].filter((b) => /^(Personal Information|Employment|Membership|Recruitment)/.test(b.textContent.trim()));
  if (tabs.length < 4) return false;
  return tabs.every((b) => {
    const r = b.getBoundingClientRect();
    let left = 0;
    let right = window.innerWidth;
    for (let p = b.parentElement; p; p = p.parentElement) {
      if (['auto', 'scroll', 'hidden'].includes(getComputedStyle(p).overflowX)) {
        const box = p.getBoundingClientRect();
        left = Math.max(left, box.left);
        right = Math.min(right, box.right);
      }
    }
    return r.left >= left - 1 && r.right <= right + 1;
  });
})()`;

/** The number fields ask a phone for the right keyboard. */
const CAPTURE_KEYBOARDS = `(() => {
  const input = (label) => {
    const l = [...document.querySelectorAll('main label')].find((n) => n.textContent.replace(/\\*|\\(required\\)/g, '').trim() === label);
    return l ? document.getElementById(l.htmlFor) : null;
  };
  const phones = ['Cellphone Number', 'Work Tel', 'Home Tel'].map(input);
  const id = input('Identity Number');
  return phones.every((el) => el && el.getAttribute('inputmode') === 'tel') && !!id && id.getAttribute('inputmode') === 'numeric';
})()`;

/** The sticky action bar is a surface, not glass: no backdrop blur, and a fully opaque ground. */
const ACTION_BAR_OPAQUE = `(() => {
  const save = [...document.querySelectorAll('main button')].find((b) => b.textContent.trim() === 'Save Member');
  const bar = save && save.parentElement;
  if (!bar) return false;
  const s = getComputedStyle(bar);
  return (!s.backdropFilter || s.backdropFilter === 'none') && ${PIXEL}(s.backgroundColor)[3] === 255;
})()`;

/** The recruiter's member list fits its dialog, so Status and Remove are on screen. */
const DIALOG_TABLE_FITS = `(() => {
  const dialog = document.querySelector('[role="dialog"]');
  if (!dialog || !dialog.querySelector('table')) return false;
  const scrollers = [...dialog.querySelectorAll('*')].filter((el) => ['auto', 'scroll'].includes(getComputedStyle(el).overflowX) && el.querySelector('table'));
  return scrollers.length > 0 && scrollers.every((el) => el.scrollWidth <= el.clientWidth + 1);
})()`;

/** No eyebrow over the record's title: the contract refuses kickers above headings. */
const NO_EYEBROW = `(() => {
  const dialog = document.querySelector('[role="dialog"]');
  return !!dialog && ![...dialog.querySelectorAll('p')].some((p) => p.textContent.trim().toLowerCase() === 'recruitment channel' && getComputedStyle(p).textTransform === 'uppercase');
})()`;

/** The skeleton's status is a 72x22 pill, not the 12px bar the unlayered `.skeleton` rule made of it. */
const SKELETON_PILL = `(() => {
  const pills = [...document.querySelectorAll('tr[data-skeleton] td:last-child .skeleton')];
  return pills.length > 0 && pills.every((el) => {
    const r = el.getBoundingClientRect();
    return Math.abs(r.width - 72) <= 2 && Math.abs(r.height - 22) <= 2;
  });
})()`;

/** Channel names sort as a person reads them: 2 before 10. */
const NAMES_SORT_NATURALLY = `(() => {
  const names = [...document.querySelectorAll('main tr[data-row] td[data-col="name"] button')].map((b) => b.textContent.trim());
  if (names.length < 3) return false;
  const natural = [...names].sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));
  return names.join('|') === natural.join('|');
})()`;

/** Ruling ⑤: the footer sits below the login card, never on it. */
const FOOTER_CLEAR_OF_CARD = `(() => {
  const card = document.querySelector('form')?.closest('.rounded-card');
  const footer = document.querySelector('footer');
  if (!card || !footer) return false;
  return footer.getBoundingClientRect().top >= card.getBoundingClientRect().bottom;
})()`;

/*
 * `PAGE_CENSUS`, `typeInto`, `scrollIntoView` and `BOUNDARY_SHOWN` lived here until `surfaces.mjs` (`07.2`) needed
 * them too. They are probe.mjs's now, beside the other shared drivers.
 */

/** A11's box, with probe.mjs `BOUNDARY_SHOWN`: the shell is still standing, and no build artefact shows. */
const SHELL_STANDING = `!!document.querySelector('nav[aria-label="Main"]') && !!document.querySelector('header input[type="search"]')`;
const NO_BUILD_ARTEFACT = `!/assets\\/|\\.js\\b|dynamically imported/i.test(document.querySelector('[data-testid="route-error"]')?.innerText || '')`;

/**
 * The register's first member's RECORD in the server's REAL wire shape for COSATU positions:
 * one comma-separated string, never unfolded on the way out (A37, open and pinned). The stub
 * server sends an array, which is why no capture had ever reached the throw. Since A10 a record is
 * read when its row is opened (`GET /api/members/{id}`), so it is that read which is answered --
 * for the member the register's first page puts first, or the panel refuses a record that is not
 * the row's.
 */
const a37Record = async () => {
  const page = await (await fetch(`${baseUrl}/api/members/register/search`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
  })).json();
  const members = await (await fetch(`${baseUrl}/api/members`)).json();
  const first = members.find((member) => member.id === page.content[0].id);
  return { ...first, isCosatu: 'Y', cosatuPositions: 'PEC,CEC' };
};
/** A record's own read, `GET /api/members/{id}`: one to three characters after the slash, never `summary`. */
const RECORD_READS = ['*/api/members/?', '*/api/members/??', '*/api/members/???'];
/** The register answering a page with nobody on it (A10). */
const EMPTY_REGISTER_PAGE = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'surname,asc' };

const OPEN_FIRST_MEMBER = {
  what: 'open the first member',
  run: clickByText('[data-row-action]', '/open the record for/i'),
  until: PANEL_OPEN,
};

/**
 * The set, named for what each shows; `-light` / `-dark` is appended per theme.
 *
 * The first block is the register -- the thesis screen -- at every width and in every state, with
 * the owner's rulings asserted where a predicate can hold them. The rest take the world by rule.
 */
const SHOTS = [
  // ── the thesis screen, and rulings ② ③ ④ ──────────────────────────────────────────────────
  { name: 'members-desk', vp: 'desk', themes: BOTH, route: '/members', ready: ROWS, expect: { PANEL_SHUT, CRUMB_WHOLE, FIGURES_FIT, CRITERIA_ONE_LINE, RAIL_FOOT_PINNED } },
  { name: 'members-open-desk', vp: 'desk', themes: BOTH, route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER], expect: { PANEL_OPEN, CRUMB_WHOLE, HOME_CRUMB_WHOLE, FIGURES_FIT, CRITERIA_ONE_LINE, CARD_FOOT_ONE_LINE } },
  { name: 'members-laptop', vp: 'laptop', themes: LIGHT, route: '/members', ready: ROWS, expect: { PANEL_SHUT, CRUMBS_CLEAR_OF_SEARCH, CRUMB_WHOLE, FIGURES_FIT, CRITERIA_ONE_LINE } },
  { name: 'members-open-laptop', vp: 'laptop', themes: BOTH, route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER], expect: { PANEL_OPEN, CRUMBS_CLEAR_OF_SEARCH, CRUMB_WHOLE, FIGURES_FIT, CRITERIA_ONE_LINE, CARD_FOOT_ONE_LINE } },
  { name: 'members-phone', vp: 'phone', themes: BOTH, route: '/members', ready: ROWS, expect: { PANEL_SHUT, MEMBER_LINES_WHOLE } },
  {
    name: 'members-rows-phone', vp: 'phone', themes: LIGHT, route: '/members', ready: ROWS,
    steps: [{ what: 'scroll to the register', run: scrollIntoView('section[aria-label="Member register"]') }],
  },
  { name: 'members-open-phone', vp: 'phone', themes: BOTH, route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER], expect: PANEL_OPEN },
  {
    name: 'members-edit-desk', vp: 'desk', themes: LIGHT, route: '/members', ready: ROWS,
    steps: [OPEN_FIRST_MEMBER, { what: 'edit the record in place', run: clickByText('button', '/^Edit Member$/'), until: TEXT('Save changes') }],
  },

  // ── A27's class: the register's other states, reached by interception ─────────────────────
  {
    name: 'members-error-desk', vp: 'desk', themes: LIGHT, route: '/members',
    intercept: { url: '*/api/members/register/search', mode: 'fail' },
    ready: TEXT('The register could not be reached'),
    expect: {
      ERROR_SHOWN: TEXT('The register could not be reached'),
      NO_EMPTY_COPY: `!${TEXT('No members match')}`,
      NO_ZERO_FIGURES,
    },
  },
  {
    name: 'members-empty-desk', vp: 'desk', themes: LIGHT, route: '/members',
    // A page with no rows (A10): `[]` is not a page, and the register would say it could not be read.
    intercept: { url: '*/api/members/register/search', mode: 'fulfill', body: async () => EMPTY_REGISTER_PAGE },
    ready: TEXT('No members match these criteria'),
  },
  {
    name: 'members-loading-desk', vp: 'desk', themes: LIGHT, route: '/members',
    intercept: { url: '*/api/members/register/search', mode: 'hang' },
    ready: `!!document.querySelector('tbody[aria-busy="true"] tr[data-skeleton]')`,
    expect: SKELETON_PILL,
  },
  {
    name: 'members-partial-desk', vp: 'desk', themes: LIGHT, route: '/members',
    intercept: { url: '*/api/banks', mode: 'fail' },
    ready: `${ROWS} && ${TEXT('Some option lists are missing.')}`,
  },

  // ── ruling ⑤ ─────────────────────────────────────────────────────────────────────────────
  { name: 'login-desk', vp: 'desk', themes: BOTH, route: '/login', ready: TEXT('Welcome back'), expect: FOOTER_CLEAR_OF_CARD },
  { name: 'login-laptop', vp: 'laptop', themes: LIGHT, route: '/login', ready: TEXT('Welcome back'), expect: FOOTER_CLEAR_OF_CARD },
  { name: 'login-phone', vp: 'phone', themes: BOTH, route: '/login', ready: TEXT('Welcome back'), expect: FOOTER_CLEAR_OF_CARD },

  // ── the screens that take the world by rule ─────────────────────────────────────────────
  { name: 'home-desk', vp: 'desk', themes: BOTH, route: '/home', ready: HEADING, expect: RAIL_FOOT_PINNED },
  { name: 'home-phone', vp: 'phone', themes: LIGHT, route: '/home', ready: HEADING },
  { name: 'capture-desk', vp: 'desk', themes: BOTH, route: '/members/capture', ready: TEXT('Save Member'), expect: ACTION_BAR_OPAQUE },
  {
    name: 'capture-phone', vp: 'phone', themes: LIGHT, route: '/members/capture', ready: TEXT('Save Member'),
    expect: { CAPTURE_TABS_ON_SCREEN, CAPTURE_KEYBOARDS, ACTION_BAR_OPAQUE },
  },
  { name: 'recruiters-desk', vp: 'desk', themes: BOTH, route: '/recruiters', ready: ROWS, expect: NAMES_SORT_NATURALLY },
  { name: 'recruiters-phone', vp: 'phone', themes: LIGHT, route: '/recruiters', ready: ROWS },
  {
    name: 'recruiters-dialog-desk', vp: 'desk', themes: LIGHT, route: '/recruiters', ready: ROWS,
    steps: [{ what: 'open a channel', run: clickByText('button', '/show the members recruited/i'), until: `!!document.querySelector('[role="dialog"]')` }],
    expect: { DIALOG_TABLE_FITS, NO_EYEBROW },
  },
  { name: 'banks-desk', vp: 'desk', themes: BOTH, route: '/banks', ready: ROWS },
  { name: 'subscriptions-desk', vp: 'desk', themes: LIGHT, route: '/admin/maintenance/subscriptions', ready: `document.querySelectorAll('tbody tr:not([data-skeleton])').length > 1` },
  { name: 'organizer-desk', vp: 'desk', themes: LIGHT, route: '/reports/organizer-report', ready: HEADING },
  {
    name: 'date-sheet-phone', vp: 'phone', themes: LIGHT, route: '/reports/organizer-report', ready: HEADING,
    steps: [{ what: 'open a date field', run: mouseDownOn('input[aria-expanded]', '/date/i'), until: `!!document.querySelector('[role="dialog"][aria-modal="true"]')` }],
    expect: { DAYS_SPAN_SHEET, TODAY_RING_GOLD },
  },
  { name: 'forms-desk', vp: 'desk', themes: LIGHT, route: '/reports/forms', ready: HEADING },
  { name: 'apply-desk', vp: 'desk', themes: LIGHT, route: '/members/apply', ready: HEADING },
  {
    name: 'locations-desk', vp: 'desk', themes: LIGHT, route: '/reports/locations', ready: HEADING,
    steps: [{ what: 'run the search', run: clickByText('button', '/^Search$/'), until: `!!document.querySelector('main table tbody tr')` }],
  },
  {
    name: 'acb-debits-desk', vp: 'desk', themes: LIGHT, route: '/admin/acb/debits', ready: `!!document.querySelector('main input')`,
    steps: [
      { what: 'type a surname', run: typeInto('main input', 'Nkosi') },
      { what: 'run the search', run: clickByText('button', '/^Search$/'), until: ROWS },
    ],
  },
  { name: 'finance-houses-desk', vp: 'desk', themes: LIGHT, route: '/archives/finance-houses', ready: HEADING },
  { name: 'admin-desk', vp: 'desk', themes: LIGHT, route: '/admin', ready: HEADING },

  // ── A11's box: the designed fallback, proven by a DELIBERATE throw on a real route ─────────
  {
    // The failure `03.1` introduced: a route chunk that never arrives -- a stale index.html after
    // a deploy. It throws during render on an application that is otherwise healthy.
    name: 'boundary-chunk-desk', vp: 'desk', themes: LIGHT, route: '/members',
    intercept: { url: '*/assets/Members-*.js', mode: 'fail' },
    ready: BOUNDARY_SHOWN,
    expect: `${BOUNDARY_SHOWN} && ${SHELL_STANDING} && ${NO_BUILD_ARTEFACT}`,
  },
  {
    // A render throw from data: the record's Structures tab calls `.join` on the string A37 says
    // the server really sends. Not a fixture component written to throw -- the register's own code.
    name: 'boundary-render-desk', vp: 'desk', themes: LIGHT, route: '/members',
    intercept: { url: RECORD_READS, mode: 'fulfill', body: a37Record },
    ready: ROWS,
    steps: [
      OPEN_FIRST_MEMBER,
      { what: 'open the Structures tab', run: clickByText('[data-testid="member-panel"] button', '/Sasbo Structures/i'), until: BOUNDARY_SHOWN },
    ],
    expect: `${BOUNDARY_SHOWN} && ${SHELL_STANDING}`,
  },
];

/** The reviewer's own required names (its check 0), and the owner's screen. */
const ALIASES = [
  ['members-open-desk-light.png', 'desktop.png'],
  ['members-phone-light.png', 'mobile.png'],
  ['members-open-laptop-light.png', 'user-1280.png'],
];

const selected = SHOTS.filter((s) => !only || only.has(s.name));
if (only) {
  const unknown = [...only].filter((n) => !SHOTS.some((s) => s.name === n));
  if (unknown.length) {
    console.error(`--only names ${unknown.join(', ')}, and no shot is called that`);
    process.exit(2);
  }
}

mkdirSync(outDir, { recursive: true });

const rows = [];
let entry = null;
let cdp;

try {
  cdp = await launch('review');
  const { send, evaluate, metrics, waitFor, assertBuiltClient, intercept } = cdp;

  entry = await assertBuiltClient(baseUrl);
  console.log('identity OK — entry script', entry);

  // The interception driver -- fail, hang, empty or fulfil one request pattern for one shot -- is cdp.mjs's
  // `intercept`, shared with surfaces.mjs (`07.2`).

  for (const shot of selected) {
    const vp = VIEWPORTS[shot.vp];
    const phone = vp.width < 700;

    for (const theme of shot.themes) {
      const name = `${shot.name}-${theme}`;
      const row = { name, file: `${name}.png`, route: shot.route, width: vp.width, height: vp.height, theme, missed: [], elements: 0 };

      await metrics(vp.width, vp.height);
      // Touch as well as width on a phone: `pointer: coarse` is what turns the 16px field rule and
      // the 44px targets on, and an emulated width alone keeps a desktop pointer (T6).
      await send('Emulation.setTouchEmulationEnabled', phone ? { enabled: true, maxTouchPoints: 5 } : { enabled: false });
      await evaluate(storeTheme(theme === 'dark'));
      const release = shot.intercept ? await intercept(shot.intercept) : null;

      await send('Page.navigate', { url: baseUrl + shot.route });
      if (!await waitFor(pageReady, { tries: 120 })) {
        row.missed.push('pageReady never held');
        row.diagnosis = await evaluate(PAGE_CENSUS).catch((e) => `the census threw: ${e.message}`);
      } else if (shot.ready && !await waitFor(shot.ready, { tries: 120 })) {
        row.missed.push(`never ready: ${shot.ready}`);
        row.diagnosis = await evaluate(PAGE_CENSUS).catch((e) => `the census threw: ${e.message}`);
      } else {
        for (const step of shot.steps ?? []) {
          if (await evaluate(step.run) !== true) { row.missed.push(`step did not land: ${step.what}`); break; }
          if (step.until && !await waitFor(step.until)) { row.missed.push(`step landed but never took effect: ${step.what}`); break; }
        }
      }

      if (!row.missed.length && !await waitFor(SETTLED, { tries: 120, awaitPromise: true })) {
        row.missed.push('never settled: a finite animation was still running after 18s');
      }

      // 🔑 A PAGE IN THE RIGHT STATE THAT BREAKS THE RULE ITS NAME CLAIMS IS STILL PHOTOGRAPHED --
      // that capture is the evidence of the break, and the row still fails. A page that never
      // reached its state is not photographed at all: there is nothing true in that frame.
      //
      // 🔑 A CLAIM IS A SET OF NAMED PREDICATES, so a failure says WHICH. Six conditions joined
      // with `&&` reported "does not show what it claims" and nothing more, and the one that failed
      // had to be found by hand (`06.3`).
      const reached = !row.missed.length;
      if (reached && shot.expect) {
        const claims = typeof shot.expect === 'string' ? { CLAIM: shot.expect } : shot.expect;
        const broken = [];
        for (const [claim, expression] of Object.entries(claims)) {
          if (await evaluate(expression) !== true) broken.push(claim);
        }
        if (broken.length) row.missed.push(`does not show what it claims (captured as the evidence): ${broken.join(', ')}`);
      }

      if (reached) {
        // The readout, the probe and the shutter on one settled page (states.mjs's rule).
        const { paint, probe } = await evaluate(`({ paint: ${themeReadout}, probe: ${overflowProbe(vp.width)} })`);
        Object.assign(row, {
          innerWidth: probe.innerWidth,
          viewportHeld: probe.innerWidth === vp.width,
          elements: probe.elements,
          offenderCount: probe.offenderCount,
          offenders: probe.offenders,
          theme_dark: paint.dark,
          theme_bg: paint.bg,
        });
        if (!row.viewportHeld) row.missed.push(`viewport not held: asked for ${vp.width}, the page is ${probe.innerWidth} wide`);
        const { data } = await send('Page.captureScreenshot', { format: 'png' });
        writeFileSync(join(outDir, row.file), Buffer.from(data, 'base64'));
        row.captured = true;
      }

      if (release) for (const problem of await release()) row.missed.push(`interception failed: ${problem}`);

      row.ok = row.missed.length === 0;
      rows.push(row);
      console.log(`  ${row.ok ? 'ok  ' : 'FAIL'}  ${name}${row.ok ? '' : `  — ${row.missed.join('; ')}`}`);
      if (row.diagnosis) console.log(`        the page was showing: ${JSON.stringify(row.diagnosis)}`);
    }
  }
} catch (e) {
  console.error('review failed:', e.message);
  process.exitCode = 1;
} finally {
  cdp?.close();
}

// 🔴 AN ALIAS FOLLOWS THE CAPTURE, NOT THE VERDICT. Copied only for a passing row, `user-1280.png`
// kept the PRE-fix frame the moment its shot failed a claim -- and the reviewer's verdict pass
// re-reads exactly that file (`06.3`). A capture that breaks a claim is still the page as it is.
for (const [from, to] of ALIASES) {
  if (rows.some((r) => r.file === from && r.captured)) copyFileSync(join(outDir, from), join(outDir, to));
}

const commit = (() => {
  try {
    const sha = execFileSync('git', ['rev-parse', '--short', 'HEAD'], { encoding: 'utf8' }).trim();
    const dirty = execFileSync('git', ['status', '--porcelain', '--', 'src', 'index.html'], { encoding: 'utf8' }).trim();
    return dirty ? `${sha} + uncommitted changes under src/ or index.html` : sha;
  } catch {
    return 'unknown';
  }
})();

/*
 * 🔑 A RECAPTURE MERGES; IT DOES NOT REPLACE. The finish reviewer's verdict pass re-reads the
 * same files, so `--only` is how a fixed shot is retaken -- and the first `--only` run here wrote
 * its rows over a 43-row manifest. Each row now carries its own commit and time, and a run that
 * captured nothing leaves the manifest exactly as it was.
 */
const manifestPath = join(outDir, 'manifest.json');
if (rows.length) {
  const stamp = { capturedAt: new Date().toISOString(), commit, entry, baseUrl };
  let kept = [];
  try {
    kept = JSON.parse(readFileSync(manifestPath, 'utf8')).shots ?? [];
  } catch {
    kept = [];
  }
  const shots = [...kept.filter((old) => !rows.some((row) => row.name === old.name)), ...rows.map((row) => ({ ...row, ...stamp }))];
  writeFileSync(manifestPath, JSON.stringify({ updatedAt: stamp.capturedAt, shots }, null, 2));
} else {
  console.log(`nothing was captured, so ${manifestPath} is left as it was`);
}

const population = populationComplaints(rows);
/*
 * 🔑 THE THEME CANARY ONLY WHERE BOTH THEMES WERE ASKED FOR. It proves the dark rows rendered differently from
 * the light ones, and a subset of light-only shots (`--only date-sheet-phone`) has no dark rows to prove it
 * with: it failed every such run as "unproven", however well its claims held. matrix.mjs drops its route check
 * for a named subset for the same reason. The one check that still means something on a single theme -- a row
 * measured before the stylesheet applied -- runs on every run.
 */
const bothThemes = ['light', 'dark'].every((theme) => selected.some((s) => s.themes.includes(theme)));
const themes = bothThemes
  ? themeDimensionComplaints(rows)
  : rows.filter((r) => r.theme_bg === 'rgba(0, 0, 0, 0)').map((r) => `${r.name} was measured before the stylesheet applied`);
for (const complaint of population) console.log(`  POPULATION  ${complaint}`);
for (const complaint of themes) console.log(`  THEME  ${complaint}`);

const failed = rows.filter((r) => !r.ok);
console.log(`\n${rows.length - failed.length} of ${rows.length} captured in the state they claim · commit ${commit}`);
if (failed.length || population.length || themes.length || rows.length !== selected.reduce((n, s) => n + s.themes.length, 0)) {
  process.exitCode = 1;
}
