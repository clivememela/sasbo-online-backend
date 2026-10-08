/**
 * THE STATE-GATED SURFACES `surfaces.mjs` DRIVES, AND THE GUARD THAT HOLDS THE LIST AGAINST THE SOURCE. `07.2`.
 *
 * Its own module so the suite can hold the catalogue against `src/` on every run
 * (`src/test/stateGatedSurfaces.structural.test.js`) without launching a browser; the browser run is `surfaces.mjs`.
 * A dialog added tomorrow is a red suite tomorrow, not a surface nobody opened.
 *
 * 🔑 A SURFACE BEHIND A PERMISSION IS REACHED SIGNED IN: `session: ['USERS_ADMIN', …]` (the names of
 * `src/auth/permissions.js`). `surfaces.mjs` plants `sessionStorage['sasbo.session']` before the page's scripts run --
 * the contract's `{token, expiresAt, user}`, the user holding exactly those permissions -- and `serve-dist.mjs` answers
 * `GET /api/auth/me` with that same user and `GET /api/auth/mode` with `open` (`surfaceSession.mjs` is the one
 * definition both read). `session: []` is a signed-in user with no permission, for a refusal worth measuring; a surface
 * with no `session` is measured signed out, and any session a previous surface planted is removed first.
 * The stub answers sign-in's other routes too, but nothing else of an October stream: a screen's own reads still need
 * their stubs in `serve-dist.mjs`, or its surface measures an empty page.
 */
import { readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

import { FLOORS } from '../src/test/floors.js';
import { REPO_ROOT, filesUnder } from '../src/test/sourceScan.js';
import { BOUNDARY_SHOWN, clickByText, dialogNamed, mouseDownOn, scrollIntoView, typeInto, typeIntoLabelled } from './probe.mjs';

/* ── predicates ──────────────────────────────────────────────────────────────────────────────────── */

export const ROWS = `document.querySelectorAll('tr[data-row]').length > 1`;
export const HEADING = `!!document.querySelector('main h1')`;
/**
 * Visible text, case-insensitively. `innerText` is the text AS RENDERED, so a heading styled uppercase reads
 * "SASBO STRUCTURES" and an exact match on "SASBO structures" never held (record-structures' first run). Still
 * `innerText`, never `textContent`: text that is hidden must not count as present.
 */
const TEXT = (s) => `document.body.innerText.toLowerCase().includes(${JSON.stringify(s.toLowerCase())})`;
const STATE = (s) => `!!document.querySelector('[data-state="${s}"]')`;
/** The Debits screen's search results, by the table's own caption: the unpaid table above has rows before any search. */
const SEARCH_ROWS = `[...document.querySelectorAll('main table')].some((t) => t.querySelector('caption')?.textContent === 'Members matching the ACB search' && t.querySelectorAll('tr[data-row]').length > 1)`;
const PANEL = '[data-testid="member-panel"]';
const PANEL_OPEN = `!!document.querySelector('${PANEL}[data-open="true"] .mcard')`;
export const ANY_DIALOG = `!!document.querySelector('[role="dialog"]')`;
/**
 * A date picker OPEN, AND ON SCREEN. "Open" alone once passed a calendar placed below the bottom of the viewport: Add
 * Recruiter's Election Date sits below the fold of the dialog's scrolling body, the calendar opened under it, and the
 * sweep photographed a dialog with no calendar in it -- a surface "reached" and never seen. The whole panel must lie
 * inside the viewport.
 */
const DATE_PICKER_OPEN = `!!document.querySelector('[aria-haspopup="dialog"][aria-expanded="true"]') && [...document.querySelectorAll('[role="dialog"][aria-modal]')].some((d) => {
  const r = d.getBoundingClientRect();
  return r.width > 0 && r.height > 0 && r.top >= 0 && r.left >= 0 && r.bottom <= innerHeight && r.right <= innerWidth;
})`;
const BANNER = (offline, copy) => `!!document.querySelector('[data-testid="offline-banner"][data-offline="${offline}"]') && ${TEXT(copy)}`;
/** The first screen's figures in one of their five states (`HomeFigures.jsx`'s `data-figures`). */
const FIGURES = (state) => `!!document.querySelector('[data-figures="${state}"]')`;
/** The first screen's menu rows -- a row of the page, not the rail's entry for the same area. */
const MENU_ROWS = `!!document.querySelector('main a[href="/members"]')`;
const EXPANDED = (scope) => `!!document.querySelector(${JSON.stringify(`${scope} [aria-expanded="true"]`)})`;
/** The Month end page's six statistics, each a link to its screen (`04.1`). */
const MONTH_END_LINKS = `document.querySelectorAll('main a[href^="/admin/monthly-menu/"]').length === 6`;
/**
 * The record's History tab in one of its four states (`MemberHistory.jsx`'s `data-history`) -- not `DataState`'s own
 * `data-state`, which turns `slow` after five seconds of a held request while the tab is still loading.
 */
const HISTORY = (state) => `!!document.querySelector('${PANEL} [data-history="${state}"]')`;

/* ── drivers used here only ─────────────────────────────────────────────────────────────────────── */

/** Answer Yes in the Yes/No fieldset whose legend matches (Recruiters' `YesNo`). */
const answerYes = (legend) => `(() => {
  const re = ${legend};
  const set = [...document.querySelectorAll('fieldset')].find((f) => re.test(f.querySelector('legend')?.textContent || ''));
  const yes = set && [...set.querySelectorAll('button')].find((b) => /^Yes$/.test(b.textContent.trim()));
  if (!yes) return false;
  yes.click();
  return true;
})()`;

/**
 * Press the first element whose visible TEXT matches, with a real pointer sequence.
 *
 * `clickByText` sends `el.click()` alone, and a `SearchableSelect` option commits on POINTERDOWN -- deliberately,
 * so a choice lands before the outside-dismiss can close the list. `mouseDownOn` sends the right gesture but finds
 * its target by LABEL, which an option has none of. This is the missing pair: find by text, press like a pointer.
 */
const ROSTER_INPUT = `!!document.querySelector('[aria-label="Name of non-member 1"]')`;

const pointerDownOnText = (selector, pattern) => `(() => {
  const re = ${pattern};
  const el = [...document.querySelectorAll(${JSON.stringify(selector)})]
    .find((n) => re.test((n.textContent || '').trim()));
  if (!el || el.disabled) return false;
  for (const type of ['pointerdown', 'mousedown', 'mouseup', 'click']) {
    el.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
  }
  return true;
})()`;

/** Choose a value in the native select whose label matches, inside a scope, the way React hears it. */
const chooseOption = (scope, labelPattern, value) => `(() => {
  const re = ${labelPattern};
  const scope = ${JSON.stringify(scope)};
  const select = [...document.querySelectorAll(scope + ' select')].find((s) => re.test(s.labels?.[0]?.textContent || s.getAttribute('aria-label') || ''));
  if (!select) return false;
  Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(select, ${JSON.stringify(value)});
  select.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
})()`;

/** Click the n-th element matching a selector, and say whether it existed. */
const clickNth = (selector, n) => `(() => {
  const el = document.querySelectorAll(${JSON.stringify(selector)})[${n}];
  if (!el) return false;
  el.click();
  return true;
})()`;

const step = (what, run, until) => ({ what, run, until });
/** Bring a dialog's date field into view before it is opened, as a person scrolls to a field before pressing it. */
const DIALOG_DATE_IN_VIEW = step('bring Election Date into view', scrollIntoView('[role="dialog"] input[aria-haspopup="dialog"]'), `(() => {
  const r = document.querySelector('[role="dialog"] input[aria-haspopup="dialog"]').getBoundingClientRect();
  return r.top >= 0 && r.bottom <= innerHeight;
})()`);
/** A step that is a CDP call rather than a page expression (the network's condition). */
const network = (what, offline, until) => ({ what, network: offline, until });

const OPEN_FIRST_MEMBER = step('open the first member', clickByText('[data-row-action]', '/open the record for/i'), PANEL_OPEN);
/** Open the record's History tab (month end 03.6) -- shown "History", named "History of changes" -- until `state`. */
const OPEN_HISTORY = (state) => step('open History', clickByText(`${PANEL} button`, '/^History/'), HISTORY(state));
/**
 * An empty history as the server answers one (ME-7): the golden page with no entries, and the day the history began --
 * a member older than the history, whose record nothing has changed since. Read when a capture asks for it.
 */
const emptyHistory = () => {
  const golden = JSON.parse(readFileSync(join(REPO_ROOT, 'src', 'test', 'month-end-examples.json'), 'utf8'))
    .answers['GET /api/members/{id}/history'].body;
  return { ...golden, content: [], totalElements: 0, totalPages: 0 };
};
/** A workplace's visits (A.1) in one of their four states (`WorkplaceVisits.jsx`'s `data-visits`). */
const VISITS = (state) => `!!document.querySelector('main [data-visits="${state}"]')`;
/** Open INVENTED HARBOUR's visits by its R/A number, the row's control -- named by what it opens -- until `state`. */
const OPEN_VISITS = (state) => step("open a workplace's visits", clickByText('main td button', '/^900003 — the visits to /'), VISITS(state));
/** The batch print's numbers, pasted as a list -- two of the golden batch's banks and one number not on record. */
const TYPE_RA_NUMBERS = step('paste the R/A numbers', typeIntoLabelled('main', '/^R\\/A numbers/', '900001, 900003\n900099'));
/** Show listings, until the state the surface is after. */
const SHOW_LISTINGS = (until) => step('show the listings', clickByText('main button', '/^Show listings$/'), until);
/** A batch of which not one number is on record, as the server answers it. */
const noneOnRecord = () => ({ asked: [900099], listings: [], unknown: [900099], workplacesServed: 'ALL', reason: null });
/** A workplace with no visit on record, as the server answers one: the golden page with none on it. */
const noVisits = () => {
  const golden = JSON.parse(readFileSync(join(REPO_ROOT, 'src', 'test', 'month-end-examples.json'), 'utf8'))
    .answers['GET /api/workplaces/{id}/visits'].body;
  return { ...golden, visits: [], totalElements: 0, totalPages: 0 };
};
/**
 * A member whose record can be SAVED. The stub's first member is flagged BEC with no primary committee, so its edit stops
 * at the app's own validation ("Please select an active primary BEC Committee") before any confirmation can ask -- a
 * fact about the synthetic data, not a defect. The second is neither BEC nor COSATU.
 */
const OPEN_EDITABLE_MEMBER = step('open a member whose record validates', clickNth('[data-row-action]', 1), PANEL_OPEN);
const EDIT_RECORD = step('edit the record', clickByText(`${PANEL} button`, '/^Edit Member$/'), TEXT('Save changes'));
const CHANGE_PHONE = step('change the cellphone number', typeIntoLabelled(PANEL, '/^Cellphone Number/', '0829990001'));
const WELCOME_SENT = `!!document.querySelector('[role="dialog"] [role="status"]') && ${TEXT('Welcome Letter Sent')}`;
/**
 * Capture a member the form will save. `email`, when given, is typed as the Personal Email: the welcome letter offers
 * Send only to a member with an address (A56), so the "sent" surface needs one and the plain dialog is measured without.
 */
const captureAMember = ({ email } = {}) => [
  // The identity field is shut until a type is chosen, and then takes the type's name as its label (CaptureMember).
  step('choose I.D. Number', chooseOption('main', '/^Identity Type/', 'I.D. Number'),
    `[...document.querySelectorAll('main input')].some((i) => !i.disabled && /^I\\.D\\. Number/.test((i.labels?.[0]?.textContent || '').trim()))`),
  step('type a first name', typeIntoLabelled('main', '/^First Name/', 'Thandi')),
  step('type a surname', typeIntoLabelled('main', '/^Surname/', 'Synthetic')),
  // Synthetic, and passes the form's own check-digit test (validateSAID) -- a number that fails it holds the save back.
  step('type a valid synthetic I.D. number', typeIntoLabelled('main', '/^I\\.D\\. Number/', '9001015800088')),
  ...(email ? [step('type a personal email', typeIntoLabelled('main', '/^Personal Email/', email))] : []),
  step('open Employment & Bank', clickByText('main button', '/^Employment/'), `[...document.querySelectorAll('main select')].some((s) => /^Payment Type/.test(s.labels?.[0]?.textContent || ''))`),
  step('choose Payroll', chooseOption('main', '/^Payment Type/', 'Payroll')),
  step('save the member', clickByText('main button', '/^Save Member$/'), dialogNamed('/^Welcome Letter Preview$/')),
];
export const OPEN_CHANNEL = step('open the first channel', clickByText('button', '/show the members recruited/i'), ANY_DIALOG);
const IN_DIALOG = '[role="dialog"] button';

/* ── the catalogue ───────────────────────────────────────────────────────────────────────────────── */

/**
 * `covers` names the source sites a surface drives, as `<path under src>#<kind>@<which>`. The guard counts distinct
 * `@which` per `path#kind` against the sites in the source. `widths` narrows a surface that only exists at one.
 * `session` signs the surface in with those permissions (this file's head).
 */
export const SURFACES = [
  // ── the register (A27's class) ────────────────────────────────────────────────────────────────
  { name: 'members-more-criteria', route: '/members', ready: ROWS,
    steps: [step('open More criteria', clickByText('main button', '/More criteria/'), EXPANDED('main'))],
    present: { EXPANDED: EXPANDED('main') }, covers: ['pages/members/Members.jsx#aria-expanded@more criteria'] },
  { name: 'members-error', route: '/members', intercept: { url: '*/api/members/register/search', mode: 'fail' },
    ready: TEXT('The register could not be reached'),
    present: { ERROR: TEXT('The register could not be reached'), BANNER: BANNER('true', 'could not reach its server') },
    covers: ['components/table/Table.jsx#DataState@error', 'components/common/OfflineBanner.jsx#banner@unreachable'] },
  { name: 'members-empty', route: '/members', intercept: { url: '*/api/members/register/search', mode: 'fulfill',
      // A page with no rows (A10): `[]` is not a page, and the register would say it could not be read.
      body: async () => ({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'surname,asc' }) },
    ready: TEXT('No members match these criteria'), present: { EMPTY: STATE('empty') },
    covers: ['components/table/Table.jsx#DataState@empty'] },
  { name: 'members-loading', route: '/members', intercept: { url: '*/api/members/register/search', mode: 'hang' },
    ready: `!!document.querySelector('tbody[aria-busy="true"] tr[data-skeleton]')`,
    present: { SKELETON: `!!document.querySelector('tr[data-skeleton]')` }, covers: ['components/table/Table.jsx#DataState@loading'] },
  { name: 'members-partial', route: '/members', intercept: { url: '*/api/banks', mode: 'fail' },
    ready: `${ROWS} && ${TEXT('Some option lists are missing.')}`,
    present: { PARTIAL: TEXT('Some option lists are missing.'), SELECT_FAILED: TEXT('could not be loaded.') },
    covers: ['pages/members/Members.jsx#DataState@refs', 'components/form/LoadFailureFrame.jsx#DataState@loadError'] },

  // ── the record ────────────────────────────────────────────────────────────────────────────────
  // A10: a row's record is read when it is opened (`GET /api/members/{id}`); a read that fails says so in the panel,
  // the register as it was. One to three characters after the slash: an id, never `summary` or a record's history.
  { name: 'record-opening-failed', route: '/members', ready: ROWS,
    intercept: { url: ['*/api/members/?', '*/api/members/??', '*/api/members/???'], mode: 'fail' },
    steps: [step('open the first member', clickByText('[data-row-action]', '/open the record for/i'),
      TEXT("This member's record could not be opened."))],
    present: { ERROR: TEXT("This member's record could not be opened.") },
    covers: ['pages/members/Members.jsx#DataState@opening'] },
  { name: 'record-open', route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER], present: { PANEL_OPEN } },
  { name: 'record-structures', route: '/members', ready: ROWS,
    steps: [OPEN_FIRST_MEMBER, step('open Structures', clickByText(`${PANEL} button`, '/Structures/i'), TEXT('SASBO structures'))],
    present: { TAB: TEXT('SASBO structures') } },
  /*
   * 🔴 THE MEMBERSHIP TAB HAD NO SURFACE, AND IT IS WHERE THE RECORD'S ONLY VISIBLE DATE LIVES. R3-F1 changed how
   * Date Joined prints, and nothing photographed it: `record-open` is the Personal tab and `record-structures` is
   * Structures. A change with no capture is a change nobody can prove did what it says.
   */
  { name: 'record-membership', route: '/members', ready: ROWS,
    steps: [OPEN_FIRST_MEMBER, step('open Membership & Subscriptions', clickByText(`${PANEL} button`, '/Membership/i'), TEXT('Date Joined Sasbo'))],
    present: { TAB: TEXT('Date Joined Sasbo') } },
  // Month end 03.3: the record's one way to move a status, beside the status on the Membership tab. Opening it asks the
  // server for nothing; SASBO's codes are asked for once a leaving or suspended status is chosen.
  { name: 'record-change-status', route: '/members', ready: ROWS,
    steps: [OPEN_FIRST_MEMBER,
      step('open Membership & Subscriptions', clickByText(`${PANEL} button`, '/Membership/i'), TEXT('Date Joined Sasbo')),
      step('Change status', clickByText(`${PANEL} button`, '/^Change status$/'), dialogNamed('/^Change status$/'))],
    present: { DIALOG: dialogNamed('/^Change status$/') }, covers: ['components/members/ChangeStatus.jsx#Modal@change status'] },
  // Month end 04.5: the reason a resigning member gave -- offered once a deletion code with reasons under it is chosen.
  // `serve-dist.mjs` answers the golden deletion codes, which keep one invented reason under 2.
  { name: 'record-change-status-reason', route: '/members', ready: ROWS,
    steps: [OPEN_FIRST_MEMBER,
      step('open Membership & Subscriptions', clickByText(`${PANEL} button`, '/Membership/i'), TEXT('Date Joined Sasbo')),
      step('Change status', clickByText(`${PANEL} button`, '/^Change status$/'), dialogNamed('/^Change status$/')),
      step('choose Resigned', chooseOption('[role="dialog"]', '/^New status/', 'Resigned'),
        `[...document.querySelectorAll('[role="dialog"] select')].some((s) => !s.disabled && /^SASBO’s deletion code/.test(s.labels?.[0]?.textContent || ''))`),
      step('choose code 2', chooseOption('[role="dialog"]', '/^SASBO’s deletion code/', '2'),
        `[...document.querySelectorAll('[role="dialog"] select')].some((s) => /^The reason they gave/.test(s.labels?.[0]?.textContent || ''))`)],
    present: { REASON: `[...document.querySelectorAll('[role="dialog"] select')].some((s) => /^The reason they gave/.test(s.labels?.[0]?.textContent || ''))` } },
  // Month end 03.6: the record's History tab, a chunk of its own asked for when the tab is opened. `serve-dist.mjs`
  // answers every member's history with the golden page among invented entries in its shape -- a change of status with
  // SASBO's code and the reason under it, a workplace, a save of three fields, a number kept as its last four -- so the
  // sheet a sweep measures is the mock's. Each other state is reached the way a person meets it: held, failed, empty.
  { name: 'record-history', route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER, OPEN_HISTORY('ready')],
    present: { SHEET: HISTORY('ready'), WHO: TEXT('Not recorded'), REASON: TEXT('Code 2 · Resigned Society'),
      SAVE: TEXT('in one save'), MASKED: TEXT('Only the last four digits are kept.'), PAGER: TEXT('Showing 1–8 of 8') } },
  { name: 'record-history-loading', route: '/members', ready: ROWS,
    intercept: { url: '*/api/members/*/history*', mode: 'hang' },
    steps: [OPEN_FIRST_MEMBER, OPEN_HISTORY('loading')],
    present: { LOADING: HISTORY('loading'), PAGER: TEXT('Loading changes…') },
    covers: ['pages/members/MemberHistory.jsx#DataState@loading'] },
  { name: 'record-history-error', route: '/members', ready: ROWS,
    intercept: { url: '*/api/members/*/history*', mode: 'fail' },
    steps: [OPEN_FIRST_MEMBER, OPEN_HISTORY('error')],
    present: { ERROR: HISTORY('error'), NOT_EMPTY: TEXT('this is not an empty history'), PAGER: TEXT('Could not load changes') },
    covers: ['pages/members/MemberHistory.jsx#DataState@state'] },
  { name: 'record-history-empty', route: '/members', ready: ROWS,
    intercept: { url: '*/api/members/*/history*', mode: 'fulfill', body: emptyHistory },
    steps: [OPEN_FIRST_MEMBER, OPEN_HISTORY('empty')],
    present: { EMPTY: HISTORY('empty'), SINCE: TEXT('No changes recorded since 01 Sep 2026') },
    covers: ['pages/members/MemberHistory.jsx#DataState@state'] },
  // A History chunk that will not load is held by the record's own boundary: one line, and the record around it.
  { name: 'record-history-chunk-failed', route: '/members', ready: ROWS,
    intercept: { url: '*/assets/MemberHistory-*.js', mode: 'fail' },
    steps: [OPEN_FIRST_MEMBER, step('open History', clickByText(`${PANEL} button`, '/^History/'), TEXT('The history could not be shown.'))],
    present: { FAILED: TEXT('The history could not be shown.'), RECORD: PANEL_OPEN } },
  { name: 'record-edit', route: '/members', ready: ROWS, steps: [OPEN_FIRST_MEMBER, EDIT_RECORD], present: { EDITING: TEXT('Save changes') } },
  { name: 'record-save-confirm', route: '/members', ready: ROWS,
    steps: [OPEN_EDITABLE_MEMBER, EDIT_RECORD, CHANGE_PHONE,
      step('save', clickByText(`${PANEL} button`, '/Save changes$/'), dialogNamed('/^Save these changes\\?$/'))],
    present: { DIALOG: dialogNamed('/^Save these changes\\?$/') }, covers: ['pages/members/Members.jsx#ConfirmDialog@save'] },
  { name: 'record-discard-confirm', route: '/members', ready: ROWS,
    steps: [OPEN_EDITABLE_MEMBER, EDIT_RECORD, CHANGE_PHONE,
      step('close the record', clickByText(`${PANEL} button`, '/^Close member details$/'), dialogNamed('/^Discard your changes\\?$/'))],
    present: { DIALOG: dialogNamed('/^Discard your changes\\?$/') }, covers: ['pages/members/Members.jsx#ConfirmDialog@discard'] },
  { name: 'record-date-of-birth', route: '/members', ready: ROWS,
    // For an I.D. Number the date of birth is COMPUTED from the number and shown read-only (Members.jsx), so the picker
    // exists only for another identity type -- and every stub member holds an I.D. Number. The step makes it a passport.
    steps: [OPEN_EDITABLE_MEMBER, EDIT_RECORD,
      step('make the identity a passport', chooseOption(PANEL, '/^Identity Type/', 'Passport Number'), `!!document.querySelector('${PANEL} input[aria-haspopup="dialog"]')`),
      step('open Date of Birth', mouseDownOn(`${PANEL} input[aria-haspopup="dialog"]`, '/^Date of Birth/'), DATE_PICKER_OPEN)],
    present: { DATE_PICKER_OPEN }, covers: ['pages/members/Members.jsx#DateField@Date of Birth', 'components/form/DateField.jsx#aria-expanded@field'] },
  { name: 'record-election-date', route: '/members', ready: ROWS,
    // V55: the shop steward's Election Date is asked only once Shop Steward? is answered Yes on the record's
    // Structures tab (SASBO, 2026-09-30) -- a picker that exists in one state, so a surface drives it there.
    steps: [OPEN_EDITABLE_MEMBER, EDIT_RECORD,
      step('open Structures', clickByText(`${PANEL} button`, '/Structures/i'), TEXT('SASBO structures')),
      step('answer Shop Steward? Yes', chooseOption(PANEL, '/^Shop Steward\\?/', 'Yes'), `!!document.querySelector('${PANEL} input[aria-haspopup="dialog"]')`),
      step('open Election Date', mouseDownOn(`${PANEL} input[aria-haspopup="dialog"]`, '/^Election Date/'), DATE_PICKER_OPEN)],
    present: { DATE_PICKER_OPEN }, covers: ['pages/members/Members.jsx#DateField@Election Date'] },

  // ── Capture Member ────────────────────────────────────────────────────────────────────────────
  { name: 'capture-discard', route: '/members/capture', ready: TEXT('Save Member'),
    steps: [step('type a surname', typeIntoLabelled('main', '/^Surname/', 'Synthetic')),
      step('cancel', clickByText('main button', '/^Cancel$/'), dialogNamed('/^Discard this member\\?$/'))],
    present: { DIALOG: dialogNamed('/^Discard this member\\?$/') }, covers: ['pages/members/CaptureMember.jsx#ConfirmDialog@discard'] },
  // The welcome letter opens after a save the form accepts: I.D. Number with 13 digits, and Payroll, which asks for no
  // bank. `serve-dist.mjs` answers the save with an id and the send with an empty 200, as the server does. Without an
  // email address the dialog holds Send back and says why (A56) -- which is what capture-welcome measures.
  { name: 'capture-welcome', route: '/members/capture', ready: TEXT('Save Member'),
    steps: captureAMember(), present: { DIALOG: dialogNamed('/^Welcome Letter Preview$/'), NO_ADDRESS: TEXT('has no email address') },
    covers: ['components/members/WelcomeLetter.jsx#Modal@welcome'] },
  { name: 'capture-welcome-sent', route: '/members/capture', ready: TEXT('Save Member'),
    steps: [...captureAMember({ email: 'thandi.synthetic@example.test' }),
      step('send the welcome letter', clickByText('[role="dialog"] button', '/^Send Welcome Letter$/'), WELCOME_SENT)],
    present: { DIALOG: dialogNamed('/^Welcome Letter Preview$/'), SENT: WELCOME_SENT },
    covers: ['components/members/WelcomeLetter.jsx#Modal@welcome'] },
  // 🔴 EVERY LIST CAPTURE LOADS, OR THE PAGE IS HONESTLY 'partial' AND THIS ROW CAN NEVER BE REACHED (RF). PR #7 gave
  // Capture two more reference lists (`/api/recruiters`, `/api/subscriptions`) and this intercept never learnt them, so
  // from P10's close on the four rows failed "never ready" with the census reading `states: ["partial"]` -- recorded
  // then as the browser timing out. The census was right and the catalogue was stale.
  { name: 'capture-refs-error', route: '/members/capture', intercept: { url: ['*/api/banks*', '*/api/job-grades*', '*/api/provinces*', '*/api/recruitment-channels*', '*/api/representatives*', '*/api/members/userRoles*', '*/api/members/structures/tree*', '*/api/branches*', '*/api/districts*', '*/api/areas*', '*/api/recruiters*', '*/api/subscriptions*'], mode: 'fail' },
    ready: STATE('error'), present: { ERROR: STATE('error') },
    covers: ['pages/members/CaptureMember.jsx#DataState@refs', 'components/form/LoadFailureFrame.jsx#DataState@loadError'] },
  { name: 'capture-listbox', route: '/members/capture', ready: TEXT('Save Member'),
    // `SearchableSelect` opens on MOUSEDOWN (the whole field toggles it), and `el.click()` dispatches no mousedown (T30).
    // Its fields are on Employment & Bank; the first combobox on the default tab is the address search, a different control.
    // ENABLED, not merely present: the field is `disabled` while its bank list loads, and a mousedown on a disabled input
    // does nothing -- the third sweep's light rows raced that load and failed while its dark rows passed.
    steps: [step('open Employment & Bank', clickByText('main button', '/^Employment/'), `[...document.querySelectorAll('main [role="combobox"]')].some((c) => !c.disabled && /^Bank Where Employed/.test(c.labels?.[0]?.textContent || ''))`),
      step('open Bank Where Employed', mouseDownOn('main [role="combobox"]', '/^Bank Where Employed/'), `!!document.querySelector('[role="listbox"]')`)],
    present: { LISTBOX: `!!document.querySelector('[role="listbox"]')` }, covers: ['components/form/SearchableSelect.jsx#aria-expanded@listbox'] },

  // ── Recruiters ────────────────────────────────────────────────────────────────────────────────
  { name: 'recruiters-channel', route: '/recruiters', ready: ROWS, steps: [OPEN_CHANNEL], present: { DIALOG: ANY_DIALOG },
    covers: ['pages/members/Recruiters.jsx#Modal@channel'] },
  // RC15, step 2 (bugs): a channel's members, Add Member, a member's record and removing a member need MEMBERS_VIEW or
  // MEMBERS_EDIT -- UNREACHABLE below until the sign-in client's stub mode can drive a signed-in user. The channel's
  // removal is named for what it removes (the user manual's reading, 6 Oct): "Remove recruitment channel".
  { name: 'recruiters-remove-recruiter', route: '/recruiters', ready: ROWS,
    steps: [OPEN_CHANNEL, step('Remove recruitment channel', clickByText(IN_DIALOG, '/^Remove recruitment channel$/'), dialogNamed('/^Remove this recruitment channel\\?$/'))],
    present: { DIALOG: dialogNamed('/^Remove this recruitment channel\\?$/') }, covers: ['pages/members/Recruiters.jsx#ConfirmDialog@remove recruiter'] },
  { name: 'recruiters-recruiter-details-date', route: '/recruiters', ready: ROWS,
    steps: [OPEN_CHANNEL,
      step('view recruiter details', clickByText(IN_DIALOG, '/^View recruiter details$/'), dialogNamed('/^Recruiter details$/')),
      step('edit', clickByText(IN_DIALOG, '/^Edit$/'), `[...document.querySelectorAll('[role="dialog"] legend')].some((l) => /Elected Recruiter/.test(l.textContent))`),
      step('answer Elected Recruiter: Yes', answerYes('/Elected Recruiter/'), `!!document.querySelector('[role="dialog"] input[aria-haspopup="dialog"]')`),
      DIALOG_DATE_IN_VIEW,
      step('open Election Date', mouseDownOn('[role="dialog"] input[aria-haspopup="dialog"]', '/Election Date/'), DATE_PICKER_OPEN)],
    present: { DETAILS: dialogNamed('/^Recruiter details$/'), DATE_PICKER_OPEN },
    covers: ['pages/members/Recruiters.jsx#Modal@recruiter details', 'pages/members/Recruiters.jsx#DateField@recruiter details'] },
  // The recruiter's details -- Elected Recruiter and its date among them -- render only once a member is chosen, and the
  // picker needs MEMBERS_VIEW since RC15 step 2: the dialog is driven, its date is UNREACHABLE below.
  { name: 'recruiters-add-recruiter', route: '/recruiters', ready: ROWS,
    steps: [step('Add Recruiter', clickByText('main button', '/^Add Recruiter$/'), dialogNamed('/^Add Recruiter$/'))],
    present: { DIALOG: dialogNamed('/^Add Recruiter$/') },
    covers: ['pages/members/Recruiters.jsx#Modal@add recruiter'] },
  { name: 'recruiters-refs-partial', route: '/recruiters', intercept: { url: '*/api/banks*', mode: 'fail' },
    ready: `${ROWS} && (${STATE('partial')} || ${STATE('error')})`, present: { NOTICE: `${STATE('partial')} || ${STATE('error')}` },
    covers: ['pages/members/Recruiters.jsx#DataState@refs'] },

  // ── maintenance ───────────────────────────────────────────────────────────────────────────────
  { name: 'channels-delete', route: '/admin/maintenance/recruitment-channel', ready: `!!document.querySelector('main table tbody tr button')`,
    steps: [step('delete the first', clickByText('main button', '/^Delete /'), dialogNamed('/^Delete recruiter type$/'))],
    present: { DIALOG: dialogNamed('/^Delete recruiter type$/') }, covers: ['pages/maintainance/RecruitmentChannel.jsx#ConfirmDialog@delete'] },
  { name: 'channels-add', route: '/admin/maintenance/recruitment-channel', ready: HEADING,
    steps: [step('Add a channel', clickByText('main button', '/Add a channel$/'), `!!document.querySelector('input[aria-label="New recruitment channel name"]')`)],
    present: { FORM: `!!document.querySelector('input[aria-label="New recruitment channel name"]')` } },
  { name: 'channels-rename', route: '/admin/maintenance/recruitment-channel', ready: `!!document.querySelector('main table tbody tr button')`,
    steps: [step('rename the first', clickByText('main button', '/^Rename /'), `!!document.querySelector('main input[aria-label^="Rename "]')`)],
    present: { FORM: `!!document.querySelector('main input[aria-label^="Rename "]')` } },
  { name: 'channels-loading', route: '/admin/maintenance/recruitment-channel', intercept: { url: '*/api/recruitment-channels*', mode: 'hang' },
    ready: STATE('loading'), present: { LOADING: STATE('loading') }, covers: ['pages/maintainance/RecruitmentChannel.jsx#DataState@loading'] },
  { name: 'channels-error', route: '/admin/maintenance/recruitment-channel', intercept: { url: '*/api/recruitment-channels*', mode: 'fail' },
    ready: TEXT('We could not load the recruiter types.'), present: { ERROR: STATE('error') }, covers: ['pages/maintainance/RecruitmentChannel.jsx#DataState@error'] },
  { name: 'channels-empty', route: '/admin/maintenance/recruitment-channel', intercept: { url: '*/api/recruitment-channels*', mode: 'empty' },
    ready: TEXT('No recruiter types found.'), present: { EMPTY: STATE('empty') }, covers: ['pages/maintainance/RecruitmentChannel.jsx#DataState@empty'] },
  { name: 'subscriptions-edit', route: '/admin/maintenance/subscriptions', ready: `document.querySelectorAll('tbody tr:not([data-skeleton])').length > 1`,
    steps: [step('edit the first', clickByText('main button', '/^Edit the subscription for/'), `!!document.querySelector('main input[aria-label^="Subscription amount for"]')`)],
    present: { EDITING: `!!document.querySelector('main input[aria-label^="Subscription amount for"]')` } },
  { name: 'subscriptions-loading', route: '/admin/maintenance/subscriptions', intercept: { url: '*/api/subscriptions*', mode: 'hang' },
    ready: TEXT('Retrieving subscriptions…'), present: { LOADING: STATE('loading') }, covers: ['pages/maintainance/Subscriptions.jsx#DataState@loading'] },
  { name: 'subscriptions-error', route: '/admin/maintenance/subscriptions', intercept: { url: '*/api/subscriptions*', mode: 'fail' },
    ready: STATE('error'), present: { ERROR: STATE('error') }, covers: ['pages/maintainance/Subscriptions.jsx#DataState@list error'] },
  { name: 'subscriptions-refs-error', route: '/admin/maintenance/subscriptions', intercept: { url: '*/api/banks*', mode: 'fail' },
    ready: STATE('error'), present: { ERROR: STATE('error') }, covers: ['pages/maintainance/Subscriptions.jsx#DataState@refs error'] },

  // ── banks ─────────────────────────────────────────────────────────────────────────────────────
  { name: 'bank-details', route: '/banks', ready: ROWS,
    steps: [step('open a branch', clickByText('main button', '/open the .* record$/'), dialogNamed('/^Bank Details$/'))],
    present: { DIALOG: dialogNamed('/^Bank Details$/') },
    covers: ['pages/bank_branch/BankBranch.jsx#BankBranchModal@selected', 'pages/bank_branch/BankBranchModal.jsx#Modal@read'] },
  { name: 'bank-edit', route: '/banks', ready: ROWS,
    steps: [step('open a branch', clickByText('main button', '/open the .* record$/'), dialogNamed('/^Bank Details$/')),
      step('edit', clickByText(IN_DIALOG, '/^Edit$/'), dialogNamed('/^Edit Bank\\/Branch Details$/'))],
    present: { DIALOG: dialogNamed('/^Edit Bank\\/Branch Details$/') }, covers: ['pages/bank_branch/BankBranchModal.jsx#Modal@edit'] },
  { name: 'bank-add', route: '/banks', ready: HEADING,
    steps: [step('Add Bank / Branch', clickByText('main button', '/Add Bank \\/ Branch$/'), dialogNamed('/^Add Bank\\/Branch Details$/'))],
    present: { DIALOG: dialogNamed('/^Add Bank\\/Branch Details$/') },
    covers: ['pages/bank_branch/BankBranch.jsx#BankBranchModal@add', 'pages/bank_branch/BankBranchModal.jsx#Modal@add'] },
  { name: 'bank-row-details', route: '/banks', widths: [390], ready: ROWS,
    steps: [step('show a row\'s details', clickByText('main button', '/^Show details$/'), EXPANDED('main'))],
    present: { EXPANDED: EXPANDED('main') }, covers: ['components/table/Table.jsx#aria-expanded@row details'] },
  { name: 'bank-listing-partial', route: '/banks/listing', intercept: { url: '*/api/banks', mode: 'fail' },
    ready: STATE('partial'), present: { PARTIAL: STATE('partial') }, covers: ['pages/bank_branch/BankListing.jsx#DataState@partial'] },

  // ── ACB ───────────────────────────────────────────────────────────────────────────────────────
  { name: 'acb-edit-banking', route: '/admin/acb/debits', ready: `!!document.querySelector('main input')`,
    steps: [step('type a surname', typeInto('main input', 'Nkosi')),
      step('search', clickByText('main button', '/^Search$/'), SEARCH_ROWS),
      step('load a member', clickByText('main button', '/load banking details for/'), TEXT('Edit Details')),
      step('Edit Details', clickByText('main button', '/^Edit Details$/'), dialogNamed('/^Update Banking Details$/'))],
    present: { DIALOG: dialogNamed('/^Update Banking Details$/') }, covers: ['pages/acb/AcbDebits.jsx#Modal@update banking'] },
  // The Re-Debits screen (`04.1`): the stub's run 40 is the returned month, run 41 the next one.
  { name: 'acb-debits-runs-failed', route: '/admin/acb/debits', intercept: { url: '*/api/acb/runs?*', mode: 'fail' },
    ready: STATE('error'), present: { FAILED: STATE('error') }, covers: ['pages/acb/AcbDebits.jsx#DataState@runs'] },
  { name: 'acb-permission', route: '/admin/acb/debits', ready: TEXT('Record permission'),
    steps: [step('ask for the member\'s permission', clickByText('main button', '/^Record permission$/'),
      dialogNamed('/^The member’s permission$/'))],
    present: { DIALOG: dialogNamed('/^The member’s permission$/'), WHY: TEXT('who agreed, when and how') },
    covers: ['pages/acb/AcbHeldOver.jsx#Modal@permission'] },
  { name: 'acb-held-letter', route: '/admin/acb/debits', ready: TEXT('Mark handled'),
    steps: [step('mark a held letter handled', clickByText('main button', '/^Mark handled$/'),
      dialogNamed('/^Mark this letter handled$/'))],
    present: { DIALOG: dialogNamed('/^Mark this letter handled$/') }, covers: ['pages/acb/AcbHeldLetters.jsx#Modal@held letter'] },
  // A.C.B. -> Runs (`01.5`). The stub answers the list, a run, its sheets, a prepare and a cancel (`acbRunAnswer`).
  { name: 'acb-runs', route: '/admin/acb/runs', ready: ROWS,
    present: { TOTAL: TEXT('Control total'), OCTOBER: TEXT('October 2026') }, covers: [] },
  { name: 'acb-run-prepare', route: '/admin/acb/runs', ready: ROWS,
    steps: [step('prepare a month', clickByText('main button', '/^Prepare /'), dialogNamed('/^Prepare the .* run\\?$/'))],
    present: { DIALOG: dialogNamed('/^Prepare the .* run\\?$/') }, covers: ['pages/acb/AcbRuns.jsx#ConfirmDialog@prepare'] },
  // The polling pair: the stateful stub answers PREPARING twice, then PREPARED.
  { name: 'acb-run-preparing', route: '/admin/acb/runs', ready: ROWS,
    steps: [step('prepare a month', clickByText('main button', '/^Prepare /'), dialogNamed('/^Prepare the .* run\\?$/')),
      step('confirm', clickByText(IN_DIALOG, '/^Prepare$/'), TEXT('is prepared:'))],
    present: { PREPARED: TEXT('is prepared:') }, covers: [] },
  { name: 'acb-run-open', route: '/admin/acb/runs', ready: ROWS,
    steps: [step('open a run', clickByText('main button', '/^October 2026$/'), TEXT('Left out'))],
    present: { RUN: TEXT('October 2026 run'), APPROVE_WHY: TEXT('open when administrators sign in') }, covers: [] },
  { name: 'acb-run-failed', route: '/admin/acb/runs', ready: ROWS, intercept: { url: '*/api/acb/runs/41', mode: 'fail' },
    steps: [step('open a run that will not load', clickByText('main button', '/^October 2026$/'), STATE('error'))],
    present: { FAILED: STATE('error') }, covers: ['pages/acb/AcbRunPanel.jsx#DataState@run'] },
  { name: 'acb-run-cancel', route: '/admin/acb/runs', ready: ROWS,
    steps: [step('open a run', clickByText('main button', '/^October 2026$/'), TEXT('Cancel this run')),
      step('cancel it', clickByText('main button', '/^Cancel this run$/'), dialogNamed('/^Cancel the .* run\\?$/'))],
    present: { DIALOG: dialogNamed('/^Cancel the .* run\\?$/') }, covers: ['pages/acb/AcbRunPanel.jsx#ConfirmDialog@cancel'] },

  // ── reports and archives ──────────────────────────────────────────────────────────────────────
  { name: 'organiser-date', route: '/reports/organizer-report', ready: `!!document.querySelector('input[aria-haspopup="dialog"]')`,
    steps: [step('open a date field', mouseDownOn('input[aria-haspopup="dialog"]', '/date/i'), DATE_PICKER_OPEN)],
    present: { DATE_PICKER_OPEN }, covers: ['pages/reports/OrganizerReport.jsx#DateField@date'] },
  { name: 'organiser-date-months', route: '/reports/organizer-report', ready: `!!document.querySelector('input[aria-haspopup="dialog"]')`,
    steps: [step('open a date field', mouseDownOn('input[aria-haspopup="dialog"]', '/date/i'), DATE_PICKER_OPEN),
      step('show the months', clickNth('[role="dialog"][aria-modal] button[aria-expanded]', 0), `!!document.querySelector('[role="dialog"][aria-modal] button[aria-expanded="true"]')`)],
    present: { VIEW: `!!document.querySelector('[role="dialog"][aria-modal] button[aria-expanded="true"]')` }, covers: ['components/form/DateField.jsx#aria-expanded@months'] },
  { name: 'organiser-date-years', route: '/reports/organizer-report', ready: `!!document.querySelector('input[aria-haspopup="dialog"]')`,
    steps: [step('open a date field', mouseDownOn('input[aria-haspopup="dialog"]', '/date/i'), DATE_PICKER_OPEN),
      step('show the years', clickNth('[role="dialog"][aria-modal] button[aria-expanded]', 1), `!!document.querySelector('[role="dialog"][aria-modal] button[aria-expanded="true"]')`)],
    present: { VIEW: `!!document.querySelector('[role="dialog"][aria-modal] button[aria-expanded="true"]')` }, covers: ['components/form/DateField.jsx#aria-expanded@years'] },
  { name: 'locations-results', route: '/reports/locations', ready: HEADING,
    steps: [step('search', clickByText('main button', '/^Search$/'), `!!document.querySelector('main table tbody tr')`)], present: { ROWS: `!!document.querySelector('main table tbody tr')` } },
  { name: 'locations-loading', route: '/reports/locations', intercept: { url: '*/api/branches*', mode: 'hang' }, ready: HEADING,
    steps: [step('search', clickByText('main button', '/^Search$/'), TEXT('Searching locations…'))], present: { LOADING: STATE('loading') },
    covers: ['pages/reports/Locations.jsx#DataState@loading'] },
  { name: 'locations-error', route: '/reports/locations', intercept: { url: '*/api/branches*', mode: 'fail' }, ready: HEADING,
    steps: [step('search', clickByText('main button', '/^Search$/'), TEXT('We could not run that search.'))], present: { ERROR: STATE('error') },
    covers: ['pages/reports/Locations.jsx#DataState@error'] },
  { name: 'locations-empty', route: '/reports/locations', intercept: { url: '*/api/branches*', mode: 'empty' }, ready: HEADING,
    steps: [step('search', clickByText('main button', '/^Search$/'), STATE('empty'))], present: { EMPTY: STATE('empty') },
    covers: ['pages/reports/Locations.jsx#DataState@empty'] },
  ...[
    ['finance-houses', '/archives/finance-houses', 'pages/archives/FinanceHouses.jsx'],
    ['research', '/archives/research-information', 'pages/archives/ResearchInformation.jsx'],
    ['affiliations', '/archives/affiliations-other-trade-unions', 'pages/archives/AffiliationsOtherTradeUnions.jsx'],
  ].flatMap(([slug, route, file]) => [
    { name: `${slug}-folder`, route, ready: `!!document.querySelector('main button[aria-expanded="false"]')`,
      steps: [step('open the first folder', clickNth('main button[aria-expanded="false"]', 0), EXPANDED('main'))],
      present: { EXPANDED: EXPANDED('main') }, covers: [`${file}#aria-expanded@folder`] },
    { name: `${slug}-empty`, route, ready: HEADING,
      steps: [step('search for nothing', typeIntoLabelled('main', '/^Search/', 'zz-no-such-document'), STATE('empty'))],
      present: { EMPTY: STATE('empty') }, covers: ['components/archives/ArchiveEmpty.jsx#DataState@empty'] },
  ]),
  { name: 'finance-houses-category', route: '/archives/finance-houses', ready: `!!document.querySelector('main button[aria-expanded="false"]')`,
    steps: [step('open the first bank', clickNth('main button[aria-expanded="false"]', 0), EXPANDED('main')),
      step('open its first category', clickNth('main [aria-expanded="true"] ~ * button[aria-expanded="false"], main li button[aria-expanded="false"]', 0), `document.querySelectorAll('main [aria-expanded="true"]').length > 1`)],
    present: { NESTED: `document.querySelectorAll('main [aria-expanded="true"]').length > 1` }, covers: ['pages/archives/FinanceHouses.jsx#aria-expanded@category'] },
  /*
   * 🔴 R4-F2 PUT THIS ENTRY HERE. General Form renders only after choosing it on `/reports/forms`, so no route
   * sweep, matrix or contrast pass had ever reached its 120 roster inputs -- and their placeholders were drawn in
   * `ink-faint` (3.65:1 in dark) for the whole arc, on the one surface nothing measured. L2, exactly.
   */
  { name: 'general-form', route: '/reports/forms', ready: HEADING,
    // Form Type is a `SearchableSelect`, not a native select: it opens on MOUSEDOWN (T30) and its options commit on
    // pointerdown. `chooseOption` drives a <select> and found nothing here -- the first spelling of this entry.
    steps: [step('open Form Type', mouseDownOn('main [role="combobox"]', '/^Form Type/'), `!!document.querySelector('[role="listbox"]')`),
      step('choose General Form', pointerDownOnText('[role="option"]', '/^General Form$/'), ROSTER_INPUT)],
    present: { ROSTER: ROSTER_INPUT } },
  { name: 'forms-empty', route: '/reports/forms', ready: HEADING,
    steps: [step('filter to nothing', typeIntoLabelled('main', '/^Random access number/', '000000000'), STATE('empty'))],
    present: { EMPTY: STATE('empty') }, covers: ['pages/forms/Forms.jsx#DataState@empty'] },

  // ── the first screen's figures (month end `02.3`, MR-ROB-37) ──────────────────────────────────
  // The strip and the sheet above the menu rows, from `serve-dist.mjs`'s invented summary. Each state is reached the
  // way a person meets it, and every one of them keeps the menu rows: they never wait on the figures (MD69).
  { name: 'home-figures', route: '/home', ready: FIGURES('ready'),
    present: { STRIP: TEXT('Paying members'), SHEET: TEXT('No workplace on record'), RULE_C: TEXT('fewer than five'),
      OVER_A_HUNDRED: TEXT('complement out of date'), MENU: MENU_ROWS } },
  { name: 'home-figures-loading', route: '/home', intercept: { url: '*/api/members/summary*', mode: 'hang' },
    ready: FIGURES('loading'), present: { LOADING: `!!document.querySelector('[data-figures="loading"] dl[aria-busy="true"]')`, MENU: MENU_ROWS } },
  { name: 'home-figures-error', route: '/home', intercept: { url: '*/api/members/summary*', mode: 'fail' },
    ready: FIGURES('error'), present: { ERROR: STATE('error'), MENU: MENU_ROWS } },
  // The shared fixture's shape, `[]`: what an answer of the wrong shape looks like to a person (MV-36).
  { name: 'home-figures-not-available', route: '/home', intercept: { url: '*/api/members/summary*', mode: 'empty' },
    ready: TEXT('The figures are not available from this server yet.'), present: { ERROR: FIGURES('error'), MENU: MENU_ROWS } },
  // A figures chunk that will not load is held by the page's own boundary: one line, and the menu rows beneath it.
  { name: 'home-figures-chunk-failed', route: '/home', intercept: { url: '*/assets/HomeFigures-*.js', mode: 'fail' },
    ready: TEXT('The figures could not be shown.'), present: { MENU: MENU_ROWS } },

  // ── a workplace's visits (month end A.1, on 01.5) ─────────────────────────────────────────────
  // `serve-dist.mjs` answers every workplace's visits with the server's golden page: a visit that changed the complement,
  // and a month of SASBO's own history. The row is opened as a person opens it, by its R/A number; the other states are
  // the read hung, failed, or answered with nothing on it.
  { name: 'workplace-visits', route: '/admin/maintenance/workplaces', ready: ROWS, steps: [OPEN_VISITS('ready')],
    present: { READY: VISITS('ready'), MONTH: TEXT('From SASBO’s list'), WITHHELD: TEXT('shown once sign-in exists') },
    covers: ['pages/maintainance/Workplaces.jsx#aria-expanded@visits'] },
  { name: 'workplace-visits-loading', route: '/admin/maintenance/workplaces', ready: ROWS,
    intercept: { url: '*/api/workplaces/*/visits*', mode: 'hang' }, steps: [OPEN_VISITS('loading')],
    present: { LOADING: VISITS('loading') }, covers: ['pages/maintainance/WorkplaceVisits.jsx#DataState@state'] },
  { name: 'workplace-visits-error', route: '/admin/maintenance/workplaces', ready: ROWS,
    intercept: { url: '*/api/workplaces/*/visits*', mode: 'fail' }, steps: [OPEN_VISITS('error')],
    present: { ERROR: VISITS('error'), RETRY: TEXT('Try again') } },
  { name: 'workplace-visits-empty', route: '/admin/maintenance/workplaces', ready: ROWS,
    intercept: { url: '*/api/workplaces/*/visits*', mode: 'fulfill', body: noVisits }, steps: [OPEN_VISITS('empty')],
    present: { EMPTY: TEXT('No visits on record') } },

  // ── the organisers' batch print (October build, 5 Oct Show & Tell) ──────────────────────────────
  // The R/A numbers pasted into the box as an organiser pastes a list, and Show listings; `serve-dist.mjs` answers the
  // server's golden batch -- two invented banks and a number not on record. Signed out, so each branch says why its
  // members are not listed. The other states: the batch hung, failed, or not one number on record.
  { name: 'batch-print-results', route: '/reports/locations/print', ready: HEADING,
    steps: [TYPE_RA_NUMBERS, SHOW_LISTINGS(`!!document.querySelector('main .print-bank article')`)],
    present: { BANKS: `!!document.querySelector('main .print-bank h2')`, UNKNOWN: TEXT('Not on record here: 900099'),
      SIGN_IN: TEXT('Sign in to see the members at this branch') } },
  { name: 'batch-print-loading', route: '/reports/locations/print', ready: HEADING,
    intercept: { url: '*/api/workplaces/listings*', mode: 'hang' },
    steps: [TYPE_RA_NUMBERS, SHOW_LISTINGS(TEXT('Finding the branches…'))], present: { LOADING: STATE('loading') },
    covers: ['pages/reports/BatchPrint.jsx#DataState@loading'] },
  { name: 'batch-print-error', route: '/reports/locations/print', ready: HEADING,
    intercept: { url: '*/api/workplaces/listings*', mode: 'fail' },
    steps: [TYPE_RA_NUMBERS, SHOW_LISTINGS(TEXT('We could not find those branches.'))], present: { ERROR: STATE('error') },
    covers: ['pages/reports/BatchPrint.jsx#DataState@error'] },
  { name: 'batch-print-none-on-record', route: '/reports/locations/print', ready: HEADING,
    intercept: { url: '*/api/workplaces/listings*', mode: 'fulfill', body: noneOnRecord },
    steps: [TYPE_RA_NUMBERS, SHOW_LISTINGS(TEXT('None of these R/A numbers is on record here'))],
    present: { EMPTY: STATE('empty') }, covers: ['pages/reports/BatchPrint.jsx#DataState@empty'] },

  // ── month end (`04.1`-`04.10`) ────────────────────────────────────────────────────────────────
  // The Month end page and a statistic's screen, from `serve-dist.mjs`'s golden months and sheets: August 2026 taken,
  // September 2026 open. Each state is reached the way a person meets it -- a month chosen by its address, a sheet
  // failed, hung or of the wrong shape by interception, a pack prepared and polled by the stub's own job.
  { name: 'month-end', route: '/admin/monthly-menu', ready: TEXT('Its figures are the month as it stood then'),
    present: { TAKEN: TEXT('Taken on'), STATISTICS: MONTH_END_LINKS,
      LISTINGS: TEXT('This waits until administrators sign in to the system. Nothing was produced.') } },
  { name: 'month-end-open', route: '/admin/monthly-menu?month=2026-09', ready: TEXT('is the open month'),
    present: { LIVE: TEXT('read live'), TAKE_WHY: TEXT('Taking a month freezes its figures for good') } },
  { name: 'month-end-months-failed', route: '/admin/monthly-menu', intercept: { url: '*/api/month-end/months*', mode: 'fail' },
    ready: TEXT('Month could not be loaded.'), present: { FAILED: STATE('error'), STATISTICS: MONTH_END_LINKS } },
  // The pack's job, measurable: the stub answers PREPARING twice and then READY, on A.C.B.'s first two delays.
  { name: 'month-end-pack', route: '/admin/monthly-menu', ready: TEXT('Its figures are the month as it stood then'),
    steps: [step('prepare the pack', clickByText('main button', '/^Prepare the pack for /'), TEXT('is ready'))],
    present: { READY: TEXT('is ready'),
      LINKS: `document.querySelectorAll('main a[download][href*="/api/month-end/packs/"]').length === 2` } },
  // Membership holds the widest sheet, eleven columns by table code: the one a phone scrolls sideways.
  { name: 'month-end-statistic', route: '/admin/monthly-menu/membership?month=2026-08', ready: ROWS,
    present: { TAKEN: TEXT('as taken on'), TOTAL: TEXT('All groups'), WIDE: TEXT('By payroll deduction (R)'),
      NOTES: TEXT('Members are the members on record') } },
  { name: 'month-end-statistic-rule-c', route: '/admin/monthly-menu/branches?month=2026-08', ready: ROWS,
    present: { FEWER: TEXT('fewer than five'), WITHHELD: TEXT('withheld') } },
  // The open month read live -- and recruitment's sheets, which hold no row, each in its empty state.
  { name: 'month-end-statistic-open', route: '/admin/monthly-menu/recruitment?month=2026-09', ready: TEXT('is the open month'),
    present: { LIVE: TEXT('read live'), EMPTY: TEXT('Nothing was counted on this sheet for September 2026.') },
    covers: ['pages/admin/MonthEndStatistic.jsx#DataState@sheet empty'] },
  { name: 'month-end-statistic-loading', route: '/admin/monthly-menu/membership?month=2026-08',
    intercept: { url: '*/api/month-end/2026-08/membership*', mode: 'hang' },
    ready: STATE('loading'), present: { LOADING: STATE('loading') }, covers: ['pages/admin/MonthEndStatistic.jsx#DataState@state'] },
  { name: 'month-end-statistic-error', route: '/admin/monthly-menu/membership?month=2026-08',
    intercept: { url: '*/api/month-end/2026-08/membership*', mode: 'fail' },
    ready: STATE('error'), present: { ERROR: STATE('error') }, covers: ['pages/admin/MonthEndStatistic.jsx#DataState@state'] },
  { name: 'month-end-statistic-not-available', route: '/admin/monthly-menu/membership?month=2026-08',
    intercept: { url: '*/api/month-end/2026-08/membership*', mode: 'empty' },
    ready: TEXT('This statistic is not available from this server yet.'), present: { ERROR: STATE('error') } },
  { name: 'month-end-statistic-refused', route: '/admin/monthly-menu/membership?month=2026-07',
    ready: TEXT('That month was not taken'), present: { REFUSED: STATE('error') } },
  { name: 'month-end-statistic-unknown', route: '/admin/monthly-menu/no-such-statistic', ready: HEADING,
    present: { NOT_FOUND: TEXT('No such statistic') } },

  // ── sign-in's Maintenance screens (contracts/signin.md), signed in through `session` ──────────
  // `serve-dist.mjs` answers invented users over the server's seeded positions, their roles, the permission catalogue
  // and several pages of the record (`adminAnswer`). A new user's temporary password is one invented string.
  { name: 'users', route: '/admin/maintenance/users', session: ['USERS_ADMIN'], ready: ROWS,
    present: { ROWS, CREATE: TEXT('Create a user'), SCOPE: TEXT('Areas: A, B') } },
  { name: 'users-refused', route: '/admin/maintenance/users', session: [], ready: HEADING,
    present: { REFUSED: TEXT('You need permission to manage users') } },
  { name: 'users-create', route: '/admin/maintenance/users', session: ['USERS_ADMIN'], ready: ROWS,
    steps: [step('open Create a user', clickByText('main button', '/Create a user/'), dialogNamed('/^Create a user$/')),
      step('choose an area scope', `(() => { const r = [...document.querySelectorAll('[role="dialog"] input[type="radio"]')].find((n) => n.value === 'AREA'); if (!r) return false; r.click(); return true; })()`,
        TEXT('Area A'))],
    present: { DIALOG: dialogNamed('/^Create a user$/'), AREAS: TEXT('Area A') }, covers: ['pages/users/UserDialog.jsx#Modal@user'] },
  { name: 'users-temporary-password', route: '/admin/maintenance/users', session: ['USERS_ADMIN'], ready: ROWS,
    steps: [step('open Create a user', clickByText('main button', '/Create a user/'), dialogNamed('/^Create a user$/')),
      step('type a name', typeIntoLabelled('[role="dialog"]', '/^Name/', 'Mo Synthetic')),
      step('type a username', typeIntoLabelled('[role="dialog"]', '/^Username/', 'mo.synthetic')),
      step('choose a position', chooseOption('[role="dialog"]', '/^Position/', 'HEAD_OFFICE_ADMINISTRATOR'), `!!document.querySelector('[role="dialog"] select')`),
      step('create the user', clickByText('[role="dialog"] button', '/^Create user$/'), TEXT('It is shown only this once'))],
    present: { ONCE: TEXT('It is shown only this once'), PASSWORD: TEXT('surface-temporary-password') } },
  { name: 'positions-change', route: '/admin/maintenance/positions', session: ['USERS_ADMIN'], ready: ROWS,
    steps: [step('open a position', clickNth('main [data-row-action]', 0), ANY_DIALOG)],
    present: { DIALOG: dialogNamed('/^Change System Administrator$/') }, covers: ['pages/users/Positions.jsx#Modal@position'] },
  // The SECOND role: the first is the System Administrator's, `fixed`, whose dialog says so instead of a checklist.
  { name: 'roles-change', route: '/admin/maintenance/roles', session: ['USERS_ADMIN'], ready: ROWS,
    steps: [step('open a role', clickNth('main [data-row-action]', 1), ANY_DIALOG)],
    present: { DIALOG: dialogNamed('/^Change Head Office Administrator$/'), CATALOGUE: TEXT('Read the record of who did what') },
    covers: ['pages/users/Roles.jsx#Modal@role'] },
  { name: 'audit-from', route: '/admin/maintenance/audit', session: ['AUDIT_VIEW'], ready: ROWS,
    steps: [step('open From', mouseDownOn('main input[aria-haspopup="dialog"]', '/^From/'), DATE_PICKER_OPEN)],
    present: { DATE_PICKER_OPEN }, covers: ['pages/users/Audit.jsx#DateField@From'] },
  { name: 'audit-to', route: '/admin/maintenance/audit', session: ['AUDIT_VIEW'], ready: ROWS,
    steps: [step('open To', mouseDownOn('main input[aria-haspopup="dialog"]', '/^To/'), DATE_PICKER_OPEN)],
    present: { DATE_PICKER_OPEN }, covers: ['pages/users/Audit.jsx#DateField@To'] },

  // ── the shell ─────────────────────────────────────────────────────────────────────────────────
  { name: 'shell-drawer', route: '/home', widths: [390], ready: HEADING,
    steps: [step('open the navigation', clickByText('header button', '/^Open navigation menu$/'), EXPANDED('header'))],
    present: { EXPANDED: EXPANDED('header') }, covers: ['components/Header.jsx#aria-expanded@drawer'] },
  { name: 'shell-section', route: '/home', widths: [1440], ready: HEADING,
    steps: [step('open a rail section', clickNth('nav[aria-label="Main"] button[aria-expanded="false"]', 0), EXPANDED('nav[aria-label="Main"]'))],
    present: { EXPANDED: EXPANDED('nav[aria-label="Main"]') }, covers: ['components/common/SidebarItem.jsx#aria-expanded@section'] },
  { name: 'shell-offline', route: '/members', ready: ROWS,
    steps: [network('go offline', true, BANNER('true', 'You are offline.'))],
    present: { BANNER: BANNER('true', 'You are offline.') }, covers: ['components/common/OfflineBanner.jsx#banner@offline'] },
  { name: 'shell-back-online', route: '/members', ready: ROWS,
    steps: [network('go offline', true, BANNER('true', 'You are offline.')), network('come back', false, TEXT('The connection is back.'))],
    present: { BANNER: TEXT('The connection is back.') }, covers: ['components/common/OfflineBanner.jsx#banner@back'] },
  { name: 'route-boundary', route: '/members', intercept: { url: '*/assets/Members-*.js', mode: 'fail' },
    ready: BOUNDARY_SHOWN, present: { BOUNDARY: BOUNDARY_SHOWN } },
];

/**
 * Sites no person can reach, named with the reason -- counted by the guard, never driven, and printed in every run so
 * the reason is re-read rather than remembered.
 */
export const UNREACHABLE = [
  { site: 'pages/members/CaptureMember.jsx#DateField@Election Date',
    why: 'behind `form.electedRep === "Y"`, and nothing on the form sets `electedRep` since main removed its question (F-P48)' },
  { site: 'pages/members/CaptureMember.jsx#DateField@Date Joined',
    why: 'rendered `disabled` -- a date nobody can open' },
  { site: 'components/form/AddressAutocomplete.jsx#aria-expanded@suggestions',
    why: 'its suggestions come from Google Places; the stub cannot answer Google and a measurement sends nothing to it (R-SEC-H)' },
  // October build, bugs (RC15 step 2): the members of a recruitment channel are a server page that needs MEMBERS_VIEW, and
  // changing who is in a channel needs MEMBERS_EDIT; oct/base's AuthProvider is the anonymous caller until the sign-in
  // stream's client lands -- then its stub mode (contracts/signin.md) drives these, and they go.
  { site: 'pages/members/Recruiters.jsx#Modal@add member',
    why: 'Add Member is offered only to a signed-in user holding MEMBERS_EDIT, and no session can be stubbed until sign-in lands' },
  { site: 'pages/members/Recruiters.jsx#Modal@member details',
    why: "a member's record opens from a channel's member list, which needs MEMBERS_VIEW, and no session can be stubbed until sign-in lands" },
  { site: 'pages/members/Recruiters.jsx#DataState@member record',
    why: "the member's record loads inside Member details, reached only from a channel's member list (MEMBERS_VIEW) -- no session can be stubbed until sign-in lands" },
  { site: 'pages/members/Recruiters.jsx#ConfirmDialog@remove member',
    why: 'taking a member off a channel needs MEMBERS_EDIT, from the member list MEMBERS_VIEW shows -- no session can be stubbed until sign-in lands' },
  { site: 'pages/members/Recruiters.jsx#DateField@add recruiter',
    why: "the recruiter's Election Date renders once a member is chosen, and the picker needs MEMBERS_VIEW -- no session can be stubbed until sign-in lands" },
  // October build: the two screens below need a signed-in user, and oct/base's AuthProvider is the anonymous caller
  // until the sign-in stream's client lands -- then the stub mode of contracts/signin.md drives them, and these go.
  { site: 'pages/reports/WorkplaceListing.jsx#DataState@loading',
    why: 'the members at a branch are read only for a signed-in user holding MEMBERS_VIEW, and no session can be stubbed until sign-in lands' },
  { site: 'pages/reports/WorkplaceListing.jsx#DataState@error',
    why: 'the members at a branch are read only for a signed-in user holding MEMBERS_VIEW, and no session can be stubbed until sign-in lands' },
  { site: 'pages/reports/MyVisitDrafts.jsx#DataState@state',
    why: 'My visits is shown only to a signed-in user holding VISITS_CAPTURE, and no session can be stubbed until sign-in lands' },
];

/* ── the guard: the catalogue against the source ─────────────────────────────────────────────────── */

const SITE_KINDS = [
  { kind: 'Modal', re: /<Modal\b/g, skip: ['components/overlay/Modal.jsx'] },
  { kind: 'ConfirmDialog', re: /<ConfirmDialog\b/g, skip: ['components/overlay/Modal.jsx'] },
  { kind: 'BankBranchModal', re: /<BankBranchModal\b/g, skip: [] },
  { kind: 'DataState', re: /<DataState\b/g, skip: ['components/common/DataState.jsx'] },
  { kind: 'DateField', re: /<DateField\b/g, skip: [] },
  // The ATTRIBUTE, not the attribute selector: `[aria-expanded="true"]` in a query names no control (Modal.jsx, R2-F1).
  { kind: 'aria-expanded', re: /(?<!\[)aria-expanded=/g, skip: [] },
];

/** How many sites of one kind a source text holds. Exported so the guard's own patterns carry canaries. */
export const siteCount = (text, kind) => (text.match(SITE_KINDS.find((k) => k.kind === kind).re) || []).length;

/**
 * Returns complaints; empty means every state-gated site in `src/` is driven or excused.
 *
 * Every `<Modal`, `<ConfirmDialog`, `<BankBranchModal`, `<DataState`, `<DateField` and `aria-expanded` site in a
 * non-test `.jsx` file under `src/` is counted per file, and the catalogue must name at least that many distinct sites
 * per file, each driven or listed in `UNREACHABLE` with the reason. The offline banner is claimed by its states. The
 * source is walked by the suite's own walker (`sourceScan.js`: from the repository root, not the working directory,
 * and an unreadable directory is recorded rather than dropped) and read RAW -- a site written in a comment is still
 * counted, which errs towards a complaint.
 */
export function coverageComplaints(surfaces = SURFACES, unreachable = UNREACHABLE) {
  const sources = new Map();
  const src = join(REPO_ROOT, 'src');
  for (const path of filesUnder(src, /\.jsx$/).filter((p) => !/\.test\.jsx$/.test(p))) {
    const file = relative(src, path).replaceAll('\\', '/');
    const text = readFileSync(path, 'utf8');
    for (const { kind, skip } of SITE_KINDS) {
      if (skip.includes(file)) continue;
      const count = siteCount(text, kind);
      if (count) sources.set(`${file}#${kind}`, count);
    }
  }
  const total = [...sources.values()].reduce((a, b) => a + b, 0);
  if (total < FLOORS.STATE_GATED_SITES) return [`read only ${total} state-gated sites under src/ -- the scan is not seeing the source`];

  const claimed = new Map();
  for (const site of [...surfaces.flatMap((s) => s.covers ?? []), ...unreachable.map((u) => u.site)]) {
    const [key, which] = site.split('@');
    if (!claimed.has(key)) claimed.set(key, new Set());
    claimed.get(key).add(which);
  }
  const complaints = [];
  for (const [key, count] of [...sources].sort()) {
    const named = claimed.get(key)?.size ?? 0;
    if (named < count) complaints.push(`${key}: ${count} site(s) in the source, ${named} driven or excused -- a surface nobody drives`);
  }
  for (const key of claimed.keys()) {
    // The banner is counted by its states, not by JSX sites.
    if (!sources.has(key) && !key.endsWith('#banner')) complaints.push(`${key} is claimed and no longer exists in the source -- drop the claim`);
  }
  return complaints;
}

