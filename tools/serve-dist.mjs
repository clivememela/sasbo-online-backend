/**
 * Serves the built `dist/` AND answers `/api/**` with synthetic data. `05.1`.
 *
 * 🔴 WHY NOT `vite preview`. On this machine it binds `[::1]` ONLY, so a probe pointed at
 * `127.0.0.1:<port>` can reach a DIFFERENT server that happens to hold the IPv4 side of
 * the same port — which is exactly [T23], and it cost this arc a measurement of the
 * mockups directory index instead of the application. This binds `127.0.0.1` explicitly
 * and prints what it bound, so the address measured is the address served.
 *
 * 🔑 WHY IT SERVES THE API TOO. The matrix measures LAYOUT, and a table with no rows has
 * no layout worth measuring — a nine-column table that overflows at 390px does not
 * overflow when it is empty. The client is built with `VITE_API_BASE_URL=` so it calls
 * `/api/...` same-origin and lands here.
 *
 * 🔑 THAT BUILD NEEDS ONE FILE, AND IT IS NOT COMMITTED. Create `.env.production.local`
 * holding `VITE_API_BASE_URL=` (empty), run `npx vite build`, then delete it again -- it is
 * a measurement artefact, not part of the app, and a build that keeps it would ship a client
 * that calls its own origin. Without it `.env.local` wins and the built client talks to a
 * dead backend on 8081, which looks exactly like a working measurement of empty pages.
 *
 * ⚠️ SYNTHETIC DATA ONLY (R-SEC-H). Every name, number and address below is invented.
 * Nothing here touches a real database, and the long values are deliberate: a matrix run
 * against three-character names proves nothing about a column that has to hold
 * "Pietermaritzburg & Midlands Branch".
 */
import { createReadStream, readdirSync, readFileSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

import { surfaceUser, userForToken } from './surfaceSession.mjs';

/** Is this a readable file? One stat, and a missing path is simply `false`. */
const isFile = (path) => {
  try {
    return statSync(path).isFile();
  } catch {
    return false;
  }
};

const ROOT = process.argv[2] ?? 'dist';
const PORT = Number(process.argv[3] ?? 4319);

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.json': 'application/json; charset=utf-8',
  '.woff2': 'font/woff2',
};

/** Long, plausible, and entirely invented. */
const surnames = ['Nkosi', 'Van der Merwe', 'Pillay', 'Mokoena', 'Abrahams', 'Ndlovu', 'Botha', 'Naidoo'];
const banks = ['Absa Bank', 'Standard Bank of South Africa', 'First National Bank', 'Nedbank', 'Capitec Bank'];
const branches = ['Pietermaritzburg & Midlands Branch', 'Sandton Central', 'Cape Town CBD', 'Gqeberha Central'];
const statuses = ['Active', 'Pending', 'Suspended', 'Resigned', 'Retired'];

const members = Array.from({ length: 60 }, (_, i) => ({
  id: i + 1,
  membershipNumber: String(100000 + i),
  employeeNumber: String(778800 + i),
  title: ['Mr', 'Ms', 'Dr', 'Adv'][i % 4],
  initials: 'ABC'.slice(0, (i % 3) + 1),
  firstNames: 'Synthetic Testperson',
  surname: surnames[i % surnames.length],
  identityType: 'I.D. Number',
  identityNumber: '9001010000080',
  bankName: banks[i % banks.length],
  branch: branches[i % branches.length],
  status: statuses[i % statuses.length],
  phone: '0110000000',
  personalEmail: 'synthetic.member@example.invalid',
  workEmail: 'synthetic.work@example.invalid',
  address: '1 Percy Street, Fourways, 2191',
  bankAddress: '1 Percy Street, Fourways, 2191',
  paymentType: i % 2 ? 'Debit Order' : 'Payroll',
  bankAccountNumber: '000123456789',
  monthlySubscription: '182.77',
  joinDate: '2019-04-01',
  recruitmentRepresentativeId: (i % 5) + 1,
  isBec: i % 3 === 0,
  isCosatu: i % 4 === 0 ? 'Y' : 'N',
  cosatuLocal: 'Braamfontein',
  cosatuRegion: 'Gauteng South',
  cosatuPositions: ['PEC'],
}));

/*
 * 🔴 ONE ROW AT EVERY CAP. The register's column ladder promises that its columns FIT, and the widths it steps
 * at were measured against this data -- whose longest member line, "Adv ABC Van der Merwe", is far short of the
 * width the member cell allows. A real roll has longer names, so a ladder measured on this data clipped Status
 * at widths where this data fitted. Row 8 carries the longest value each capped column can show, so every
 * sweep measures the worst case the caps permit, and `overflowProbe`'s register check holds it there.
 */
Object.assign(members[7], {
  title: 'Adv',
  initials: 'NTN',
  surname: 'Van der Merwe-Mokoena',
  firstNames: 'Nomvula Thandeka Ntombifuthi',
  employeeNumber: 'E001234567',
  bankName: 'The Standard Bank of South Africa Limited',
  branch: 'Johannesburg Central Business District Branch',
  status: 'Suspended',
});

const bankRows = Array.from({ length: 40 }, (_, i) => ({
  id: i + 1,
  bankName: banks[i % banks.length],
  bankAbbreviation: ['ABSA', 'SBSA', 'FNB', 'NED', 'CAP'][i % 5],
  abbreviation: ['ABSA', 'SBSA', 'FNB', 'NED', 'CAP'][i % 5],
  branchName: branches[i % branches.length],
  area: ['Gauteng South', 'Western Cape', 'KwaZulu-Natal'][i % 3],
  randomAccessNumber: String(250655 + i),
  universalBranchCode: String(250655 + i),
  phoneNumber: '011 000 0000',
  managerName: `Synthetic Manager ${i + 1}`,
  fax: '011 000 0001',
  email: 'branch.manager.synthetic@example.invalid',
  provinceCommitteeName: ['Gauteng South', 'Western Cape', 'KwaZulu-Natal'][i % 3],
}));


/**
 * A.C.B. -> Runs (`01.5`). The list is the `API` table's; one run, its lines, its exclusions, a
 * prepare and a cancel are answered per shape in `acbRunAnswer` below, because a prefix match would
 * hand the list's page to every one of them.
 *
 * 🔑 THE PREPARE IS STATEFUL, and only that: a POST starts run 9002 `PREPARING`, and its first two
 * reads answer `PREPARING` before it is `PREPARED` -- the polling flow, measurable (`screens.md` ask 2).
 * ⏱️ The two reads are the run panel's own, at once, and the screen's first poll at 1 s; its second
 * poll, at 1 + 2 = 3 s, sees `PREPARED` -- well inside a surface step's wait, so it does not race it
 * (the design session's review, 2026-09-19). The count resets on EVERY POST, not per process, so
 * each row of a sweep (widths × themes) exercises the polling afresh.
 * Accounts are last-four only, as the server sends them; every name is the synthetic members'.
 */
const acbRun = (id, actionMonth, status, overrides = {}) => ({
  id, actionMonth, status, version: 1, setupDate: '2026-09-18', creationDate: null,
  makeBy: `${actionMonth}-17`, makeByPassed: false,
  purgeDate: `${actionMonth}-30`, firstDueDate: `${actionMonth}-20`, lastDueDate: `${actionMonth}-30`,
  lineCount: 30, totalAmount: 5220, totalsAgree: true, calendarAgrees: true, rateAmount: 174,
  rateEffectiveFrom: '2026-01-01', holidaySet: 'za-statutory', holidaysKnown: true, createdBy: 'system', approvedBy: null,
  startedAt: '2026-09-18T10:00+02:00', heartbeatAt: '2026-09-18T10:00+02:00', failureReason: null,
  exclusions: [
    { reason: 'NOT_DEBIT_ORDER', listed: false, detail: 'Payroll Deduction', memberCount: 48 },
    { reason: 'STATUS_NOT_ACTIVE', listed: false, detail: 'Suspended', memberCount: 3 },
    { reason: 'NO_BANK_ON_RECORD', listed: true, detail: null, memberCount: 2 },
    { reason: 'DAY_OUT_OF_WINDOW', listed: true, detail: null, memberCount: 1 },
  ],
  ...overrides,
});
const acbRuns = [
  acbRun(41, '2026-10', 'PREPARED'),
  acbRun(40, '2026-09', 'SUBMITTED'),
  acbRun(39, '2026-08', 'CANCELLED', { lineCount: 28, totalAmount: 4872 }),
];
const acbPage = (content, size) => ({ content, page: 0, size, totalElements: content.length, totalPages: 1 });
const acbLines = members.slice(0, 30).map((m, i) => ({
  id: 1000 + i, memberId: m.id, membershipNumber: m.membershipNumber, surname: m.surname, initials: m.initials,
  bankName: m.bankName, branchCode: '632005', accountLastFour: String(1000 + i * 37).slice(-4), accountType: null,
  debitDay: 20 + (i % 12), actionDate: `2026-10-${String(20 + (i % 11)).padStart(2, '0')}`, amount: 174,
  outcome: 'PENDING', rePresentsLineId: null, fileSequence: null, unpaidCode: null, unpaidReason: null, decision: null,
}));
const acbExclusions = members.slice(30, 33).map((m, i) => ({
  memberId: m.id, membershipNumber: m.membershipNumber, surname: m.surname, initials: m.initials,
  reason: ['NO_BANK_ON_RECORD', 'NO_BANK_ON_RECORD', 'DAY_OUT_OF_WINDOW'][i], detail: [m.bankName, m.bankName, '5'][i],
}));
/**
 * The Debits screen (`04.1`): run 40's unpaids -- every reason, one decided, one Invalid Account Number with its
 * banking cure -- run 41's one held-over re-debit, run 40's two held letters, and the member search's page. The
 * search answers the same page whatever is asked: it measures the table, not the matching.
 */
const UNPAID_CASES = [
  ['2', 'NOT_PROVIDED_FOR', 'RE_PRESENT', null, 'QUEUED'],
  ['4', 'PAYMENT_STOPPED', 'SUSPEND', null, 'RENDERED'],
  ['8', 'INVALID_ACCOUNT_NUMBER', null, null, 'QUEUED'],
  ['12', 'ACCOUNT_CLOSED', 'SUSPEND', null, 'RENDERED'],
  ['2', 'NOT_PROVIDED_FOR', null, 'RE_PRESENT', 'SENT'],
  ['30', 'OTHER', null, null, null],
];
const acbUnpaids = UNPAID_CASES.map(([code, reason, suggested, decided, letterStatus], i) => ({
  ...acbLines[i], id: 2000 + i, outcome: 'UNPAID', actionDate: '2026-09-25', unpaidCode: code, unpaidReason: reason,
  decision: decided, suggestedDecision: decided ? null : suggested, permissionNote: null, letterStatus,
}));
const acbHeldOver = [{
  lineId: 1900, runId: 38, memberId: members[1].id, membershipNumber: members[1].membershipNumber,
  surname: members[1].surname, month: '2026-07', amount: 174, permitted: false,
}];
const acbHeldLetters = members.slice(0, 2).map((m, i) => ({
  letterId: 3000 + i, lineId: 2000 + i, memberId: m.id, membershipNumber: m.membershipNumber, surname: m.surname,
  template: ['RE_DEBIT', 'RETURN_DEBIT'][i], reason: 'NO_EMAIL', canSend: i === 0,
}));
const acbFound = members.slice(0, 25).map((m, i) => ({
  id: m.id, membershipNumber: m.membershipNumber, title: m.title, initials: m.initials, surname: m.surname,
  bankName: m.bankName, accountLastFour: String(1000 + i * 37).slice(-4), debitDay: 20 + (i % 12), status: m.status,
}));
/*
 * The termination report (`03.5`), `GET /api/acb/termination-report`: two members the rule terminates -- one already
 * Terminated, so the report shows the status instead of the action -- and two one away, the longest surname among
 * them. Nobody is terminated by reading it, and the action stays refused until sign-in (B1).
 */
const acbTermination = [members[0], members[7], members[2], members[3]].map((m, i) => ({
  memberId: m.id, membershipNumber: m.membershipNumber, surname: m.surname,
  unpaidCount: i < 2 ? 3 : 2, terminate: i < 2, twelveMonthTotal: i < 2 ? 3 : 2,
  unpaidMonths: i < 2 ? ['2026-07', '2026-08', '2026-09'] : ['2026-08', '2026-09'],
  openingUnpaids: i === 1 ? 1 : 0, status: i === 0 ? 'Terminated' : 'Active',
}));
let acbPreparedReads = 0;

/** The A.C.B. run shapes a prefix match cannot answer; `false` when the path is not one of them. */
const acbRunAnswer = (req, path) => {
  const reply = (status, body) => ({ status, body });
  if (req.method === 'POST' && path === '/api/acb/runs') {
    acbPreparedReads = 0;
    return reply(202, acbRun(9002, '2026-10', 'PREPARING', { lineCount: 0, totalAmount: 0, exclusions: [] }));
  }
  let m = /^\/api\/acb\/runs\/(\d+)\/cancel$/.exec(path);
  if (req.method === 'POST' && m) return reply(200, acbRun(Number(m[1]), '2026-10', 'CANCELLED', { version: 2 }));
  if (req.method !== 'GET') return false;
  if (req.method === 'POST' && path === '/api/acb/members/search') {
    return reply(200, { content: acbFound, page: 0, size: 25, totalElements: 57, totalPages: 3 });
  }
  if (/^\/api\/acb\/runs\/\d+\/unpaids$/.test(path)) return reply(200, acbPage(acbUnpaids, 50));
  if (/^\/api\/acb\/runs\/\d+\/held-over$/.test(path)) return reply(200, acbPage(acbHeldOver, 50));
  if (/^\/api\/acb\/runs\/\d+\/letters\/held$/.test(path)) return reply(200, acbPage(acbHeldLetters, 50));
  m = /^\/api\/acb\/runs\/\d+\/lines\/(\d+)\/decision$/.exec(path);
  if (req.method === 'POST' && m) {
    const line = acbUnpaids.find((l) => l.id === Number(m[1])) ?? acbUnpaids[0];
    return reply(200, { ...line, decision: line.suggestedDecision ?? 'RE_PRESENT', suggestedDecision: null });
  }
  m = /^\/api\/acb\/runs\/\d+\/lines\/(\d+)\/permission$/.exec(path);
  if (req.method === 'POST' && m) return reply(200, { ...acbUnpaids[0], decision: 'RE_PRESENT', permissionNote: 'noted' });
  m = /^\/api\/acb\/runs\/\d+\/letters\/(\d+)\/handled$/.exec(path);
  if (req.method === 'POST' && m) return reply(200, acbHeldLetters.find((l) => l.letterId === Number(m[1])) ?? acbHeldLetters[0]);
  // As the server answers today: the send waits for sign-in (B1).
  if (req.method === 'POST' && /^\/api\/acb\/runs\/\d+\/letters\/\d+\/send$/.test(path)) {
    return reply(403, { status: 403, code: 'IDENTITY_REQUIRED', details: {},
      message: 'Sending a letter to a member waits until administrators sign in to this system. Nothing has been sent.' });
  }
  if (/^\/api\/acb\/runs\/\d+\/lines$/.test(path)) return reply(200, acbPage(acbLines, 50));
  if (/^\/api\/acb\/runs\/\d+\/exclusions$/.test(path)) return reply(200, acbPage(acbExclusions, 50));
  m = /^\/api\/acb\/runs\/(\d+)$/.exec(path);
  if (!m) return false;
  const id = Number(m[1]);
  if (id === 9002) {
    acbPreparedReads += 1;
    return reply(200, acbRun(9002, '2026-10', acbPreparedReads <= 2 ? 'PREPARING' : 'PREPARED'));
  }
  return reply(200, acbRuns.find((r) => r.id === id) ?? acbRuns[0]);
};

/*
 * The first screen's figures (month end `02.3`), `GET /api/members/summary`.
 *
 * 🔴 A LONGER KEY THAN `/api/members`, AND IT HAS TO BE ONE. Matching is by prefix, so without it the summary was
 * answered with the list of members -- an array, which the screen rightly shows as *not available*, and a sweep would
 * have photographed the error state as the first screen. Invented groups and invented numbers, drawn as the sheet is
 * (screens.md §0.1): nine groups, three of them core, one row under five and the row withheld beside it, a penetration
 * above a hundred, members with no workplace last, and the totals the sum of every row -- the hidden ones included,
 * as the server's are. It answers the same whatever is asked: it measures the screen, not the grouping.
 */
const summaryFigures = (onRecord, suspended, joined, left, complement) => {
  const paying = onRecord - suspended;
  return {
    onRecord, suspended, paying, joined, left, reinstated: 0, opening: onRecord - joined + left, unexplained: 0,
    joinedYearToDate: joined * 9, leftYearToDate: left * 9, complement,
    penetration: complement ? Math.round((paying / complement) * 10000) / 10000 : null,
    complementOutOfDate: Boolean(complement) && paying > complement,
  };
};
const summaryRow = (label, core, figures, hidden) => ({
  key: label, label, core, fewerThanFive: hidden === 'fewerThanFive', withheld: hidden === 'withheld',
  figures: summaryFigures(...figures),
});
const summaryRows = [
  summaryRow('INVENTED GROUP NORTH', true, [912, 31, 18, 7, 1410]),
  summaryRow('INVENTED GROUP SOUTH', true, [744, 22, 12, 5, 1180]),
  summaryRow('INVENTED GROUP EAST', true, [503, 17, 9, 4, 820]),
  summaryRow('INVENTED GROUP WEST', false, [311, 9, 6, 2, 560]),
  summaryRow('INVENTED GROUP CENTRAL', false, [208, 8, 3, 3, 390]),
  summaryRow('INVENTED GROUP COAST', false, [97, 4, 2, 1, 150]),
  summaryRow('INVENTED GROUP HARBOUR', false, [41, 1, 1, 0, 36]),
  summaryRow('INVENTED GROUP VALLEY', false, [3, 0, 0, 0, 20], 'fewerThanFive'),
  summaryRow('INVENTED GROUP RIDGE', false, [12, 1, 0, 1, 30], 'withheld'),
  { ...summaryRow('No workplace on record', null, [18, 0, 1, 0, null]), key: '~NO_WORKPLACE' },
];
const summed = (key) => summaryRows.reduce((sum, row) => sum + (row.figures[key] ?? 0), 0);
const memberSummary = {
  month: '2026-09', monthFromTheServer: true, by: 'group', area: null, region: null, workplacesServed: 'ALL',
  groupedAsAt: '2026-09-29',
  totals: summaryFigures(summed('onRecord'), summed('suspended'), summed('joined'), summed('left'), summed('complement')),
  rows: summaryRows.map((row) => (row.fewerThanFive || row.withheld ? { ...row, figures: null } : row)),
  unplaced: { statuses: [{ status: 'Inactive', members: 3 }], noWorkplace: 18, noTableCode: 0 },
  seededMembers: 0, membersFrom: null, leaversFrom: null,
  // A month not taken (04.2's rule F): the server answers both keys, and null, until the month is frozen.
  takenAt: null, takenBy: null,
};

/*
 * Month end's reads (`01.1`, `01.2`): SASBO's nine lists, each by its own route (MD41), and the workplaces a page at a
 * time -- read by the member's Workplace control on capture and the record (`01.3`), the workplaces' screen (`01.5`)
 * and the first screen's Area control (`02.3`). And the change of status (`03.1`), a POST that a prefix match would
 * answer with the list of members.
 *
 * 🔑 ANSWERED AS THE SERVER ANSWERED THEM (MD48). `src/test/month-end-examples.json` holds the server's golden answers,
 * generated from invented lists and invented workplaces and copied into this repository byte for byte -- the file the
 * suite's handlers are cut from, so a sweep and the suite see one server. Nothing in it is SASBO's (MD57). It is read
 * from the client root, where this runs (`stubDrift` reads `src/services` the same way), and a route it holds no
 * answer for stops the start by name: a shape guessed here would measure a screen nobody will ever see.
 */
const MONTH_END_EXAMPLES = 'src/test/month-end-examples.json';
const monthEndExamples = (() => {
  try {
    return JSON.parse(readFileSync(MONTH_END_EXAMPLES, 'utf8'));
  } catch (e) {
    throw new Error(`serve-dist runs from the client root, and cannot read ${MONTH_END_EXAMPLES} here (${e.message})`);
  }
})();
const golden = (route) => {
  const example = monthEndExamples.answers?.[route];
  if (example?.status !== 200) throw new Error(`${MONTH_END_EXAMPLES} has no answer for ${route}`);
  return example.body;
};
const pageRefused = monthEndExamples.refusals?.WORKPLACE_PAGE_INVALID;
if (!pageRefused) throw new Error(`${MONTH_END_EXAMPLES} has no refusal WORKPLACE_PAGE_INVALID`);
const statusChanged = golden('POST /api/members/{id}/status');
/*
 * A WORKPLACE'S VISITS (A.1). Every workplace answers the golden page -- a visit that changed the complement, and a month
 * of SASBO's own history -- and a visit recorded answers the golden visit, 201: it measures the screens, not the rule.
 */
const visitsPage = golden('GET /api/workplaces/{id}/visits');
const visitRecorded = monthEndExamples.answers?.['POST /api/workplaces/{id}/visits'];
if (visitRecorded?.status !== 201) throw new Error(`${MONTH_END_EXAMPLES} has no answer for POST /api/workplaces/{id}/visits`);
/*
 * October build: the organisers' batch print and the members at a branch, and the visit drafts -- each the server's
 * golden answer, whatever is asked: they measure the screens, not the rules.
 */
const listingsBatch = golden('GET /api/workplaces/listings');
const branchMembers = golden('GET /api/workplaces/{id}/members');
// The batch's members in one read (October build review): each id asked, a branch with the golden branch's members.
const batchMembers = golden('GET /api/workplaces/members');
const myDrafts = golden('GET /api/workplaces/visit-drafts');
const oneDraft = golden('GET /api/workplaces/visit-drafts/{id}');
const draftSavedAgain = golden('PUT /api/workplaces/visit-drafts/{id}');
const draftSubmitted = golden('POST /api/workplaces/visit-drafts/{id}/submit');
const draftCreated = monthEndExamples.answers?.['POST /api/workplaces/visit-drafts'];
if (draftCreated?.status !== 201) throw new Error(`${MONTH_END_EXAMPLES} has no answer for POST /api/workplaces/visit-drafts`);

/*
 * THE WORKPLACES. The golden page holds two rows -- right for the suite, too few to measure a sheet with -- so they
 * lead a list of invented ones cut from the same two (every field the server sends, named as it names them), with one
 * row at every cap the sheet truncates, as `members[7]` is for the register. The page and its size are the request's:
 * the capture control's search of eight gets eight, the screen's page of fifty gets fifty and a second page to turn
 * to, and a page past the last is refused as the server refuses it. The criteria are not matched -- it measures the
 * sheet, not the search.
 */
const goldenWorkplaces = golden('GET /api/workplaces').content;
const workplaces = [
  ...goldenWorkplaces,
  ...Array.from({ length: 55 }, (_, i) => ({
    ...goldenWorkplaces[i % goldenWorkplaces.length],
    id: 100 + i,
    raNumber: 910100 + i,
    name: `INVENTED WORKPLACE ${100 + i}`,
    staffComplement: 6 + ((i * 37) % 180),
  })),
];
Object.assign(workplaces[4], {
  name: 'INVENTED CENTRAL BUSINESS DISTRICT REGIONAL PROCESSING CENTRE',
  employerId: null,
  employer: null,
  employerPrinted: 'INVENTED BANK DEPARTMENTS AND SUBSIDIARIES, AS PRINTED',
  committeeName: 'INVENTED NORTH AND CENTRAL PROVINCES COMMITTEE',
  staffComplement: 12345,
});
const WORKPLACE_PAGE = 50;
const workplacePage = (page, size) => ({
  content: workplaces.slice(page * size, (page + 1) * size),
  page, size, totalElements: workplaces.length, totalPages: Math.ceil(workplaces.length / size), reason: null,
});

/*
 * A MEMBER'S HISTORY (`03.6`), `GET /api/members/{id}/history?page=&size=`. The golden page holds two entries -- a
 * field changed, and the capture -- right for the suite and too few to measure a sheet with, so they sit among invented
 * ones in the same shape, as the tab's mock draws them: a change of status with SASBO's code and the reason under it, a
 * change of workplace (the golden workplaces'), a save of three fields, and an identity number kept as its last four.
 * Newest first and then by the entry's own number, as the server orders them. The page and its size are the request's,
 * so the tab's page of fifty and its one-entry look past a page's edge are each answered as asked; a page past the last
 * is refused as the server refuses it, and a member the stub does not hold is `MEMBER_NOT_FOUND`. Every member holds the
 * same history -- it measures the tab, not the member.
 */
const goldenHistory = golden('GET /api/members/{id}/history');
const historyEntry = (fields) => ({
  ...goldenHistory.content[0],
  reason: null, code: null, codeName: null, reasonUnderCode: null, saveId: null, note: null,
  ...fields,
});
const HISTORY = [
  historyEntry({
    kind: 'STATUS', id: 1, changedAt: '2026-09-29T08:14:00Z', what: 'status', from: 'Active', to: 'Resigned',
    reason: 'DELETION_CODE', code: 2, codeName: 'Resigned Society', reasonUnderCode: 'INVENTED: MOVED AWAY',
    note: 'Invented: letter of resignation received by email.',
  }),
  historyEntry({
    kind: 'FIELD', id: 7, changedAt: '2026-09-14T13:02:00Z', what: 'workplace',
    from: '900002 INVENTED HEAD OFFICE', to: '900003 INVENTED HARBOUR', saveId: 'invented-save-7',
  }),
  historyEntry({
    kind: 'FIELD', id: 6, changedAt: '2026-09-12T07:31:00Z', what: 'address',
    from: '12 Invented Road, Invented Park', to: '4 Invented Lane, Invented Heights', saveId: 'invented-save-4',
  }),
  historyEntry({
    kind: 'FIELD', id: 5, changedAt: '2026-09-12T07:31:00Z', what: 'phone',
    from: '+27 60 555 0101', to: '+27 60 555 0188', saveId: 'invented-save-4',
  }),
  historyEntry({
    kind: 'FIELD', id: 4, changedAt: '2026-09-12T07:31:00Z', what: 'personalEmail',
    from: null, to: 'synthetic.member@example.invalid', saveId: 'invented-save-4',
  }),
  historyEntry({
    kind: 'FIELD', id: 3, changedAt: '2026-09-11T09:47:00Z', what: 'identityNumber',
    from: '****4081', to: '****4087', saveId: 'invented-save-3',
  }),
  ...goldenHistory.content,
];
const HISTORY_PAGE = 50;
const historyAnswer = (memberId, path, query) => {
  const page = Number(query.get('page') ?? 0);
  const size = Number(query.get('size') ?? HISTORY_PAGE);
  const refused = (code, message, details) => {
    const { status, body } = envelope(code, message, details);
    return { status, body: { ...body, path } };
  };
  if (!Number.isInteger(page) || !Number.isInteger(size) || page < 0 || size < 1 || size > 200) {
    return refused('HISTORY_PAGE_INVALID', null, { page: String(page), size: String(size), maxSize: '200' });
  }
  if (!members.some((member) => member.id === memberId)) {
    return refused('MEMBER_NOT_FOUND', null, { memberId: String(memberId) });
  }
  const content = HISTORY.slice(page * size, (page + 1) * size);
  if (!content.length && page > 0) {
    return refused('HISTORY_PAGE_INVALID', null, { page: String(page), size: String(size), maxSize: '200' });
  }
  return {
    status: 200,
    body: {
      ...goldenHistory, content, page, size, totalElements: HISTORY.length, totalPages: Math.ceil(HISTORY.length / size),
    },
  };
};

/*
 * MONTH END ITSELF (`04.1`-`04.10`): the months, the six statistics, the listings and the take that refuse, and the
 * statistics pack -- every answer cut from the golden ones, and every refusal the server's own envelope, so a sweep
 * photographs what the server says.
 *
 *   the months       the golden answer: the open month and the one month taken.
 *   a statistic      its golden sheets, for the month asked: the taken month as it was taken, the open month live --
 *                    `state`, `takenAt` and `takenBy` as the server answers them, and the month's name in the sheets'
 *                    titles. A month neither taken nor open is `MONTH_NOT_TAKEN`; after the open one `MONTH_NOT_BEGUN`;
 *                    one that is not a month `REQUEST_MALFORMED`; an area or region SASBO's lists do not hold
 *                    `SUMMARY_AREA_UNKNOWN`. 🔑 THE BRANCHES CARRY RULE C: two invented employers, one of fewer than
 *                    five and one withheld beside it -- the golden sheets have none, and the words a screen shows for
 *                    them are what a sweep of it must photograph.
 *   the pack         STATEFUL, as A.C.B.'s prepare is: a POST starts pack 7001 `PREPARING`, and its first two reads
 *                    answer `PREPARING` before it is `READY` -- the polling, measurable, reset on every POST. A READY
 *                    pack's workbook is an attachment of the spreadsheet's type: a small stub body, never a workbook.
 */
const MONTHS = golden('GET /api/month-end/months');
const MONTH_STATISTICS = ['new-and-deleted', 'membership', 'leavers', 'branches', 'recruitment', 'representatives'];
const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];
const monthName = (month) => `${MONTH_NAMES[Number(month.slice(5)) - 1]} ${month.slice(0, 4)}`;
/** A refusal in the server's envelope: the golden one for its code, with this request's sentence and details. */
const envelope = (code, message, details) => {
  const example = monthEndExamples.refusals?.[code];
  if (!example) throw new Error(`${MONTH_END_EXAMPLES} has no refusal ${code}`);
  return { status: example.status, body: { ...example.body, ...(message && { message }), ...(details && { details }) } };
};
// Every refusal a month end route answers here, looked up now: one the golden file lacks stops the start by name.
// (IDENTITY_REQUIRED left month end with the server's slice 2: the take and the listings are held by a permission.)
['REQUEST_MALFORMED', 'MONTH_NOT_BEGUN', 'MONTH_NOT_TAKEN', 'SUMMARY_AREA_UNKNOWN', 'PACK_NOT_FOUND',
  'HISTORY_PAGE_INVALID', 'MEMBER_NOT_FOUND']
  .forEach((code) => envelope(code));
const listingExample = golden('GET /api/month-end/{month}/listings/new-members');

/**
 * A route held by a permission, as the server's security answers it (signin-server-notes.md §4) -- before anything
 * else is read: nobody signed in is `401 SIGN_IN_REQUIRED`, a signed-in user without the permission `403
 * PERMISSION_REQUIRED`. `null` for a caller who may. The caller is the surface's planted session (`surfaceSession.mjs`).
 */
const heldBy = (req, path, permission) => {
  const user = userForToken(req.headers.authorization);
  if (!user) {
    return { status: 401, body: { status: 401, error: 'Unauthorized', message: 'Please sign in to use this part of SASBO Online.', path, code: 'SIGN_IN_REQUIRED' } };
  }
  if (!user.permissions.includes(permission)) {
    return { status: 403, body: { status: 403, error: 'Forbidden', message: 'You do not have permission to do this. Ask a user administrator if your work needs it.', path, code: 'PERMISSION_REQUIRED', details: { permission }, permission } };
  }
  return null;
};
const AREAS = new Set(golden('GET /api/reference/areas').rows.map((row) => row.letter));
const REGIONS = new Set(golden('GET /api/reference/regions').rows.map((row) => row.code));

/** The month a month end route names, judged as the server judges it (`MonthEndTakes.month`); `null` when it may be read. */
const monthRefused = (month) => {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
    return envelope('REQUEST_MALFORMED', 'That month could not be read. Name it as year and month -- 2026-09.', { field: 'month' });
  }
  if (month > MONTHS.openMonth) {
    return envelope('MONTH_NOT_BEGUN', 'That month has not begun yet.', { month, current: MONTHS.openMonth });
  }
  if (month !== MONTHS.openMonth && !MONTHS.taken.some((taken) => taken.month === month)) {
    return envelope('MONTH_NOT_TAKEN', 'That month was not taken, and a past month is read only from what was taken.',
      { month, open: MONTHS.openMonth });
  }
  return null;
};

/** Rule C, drawn: an employer of one to four, and the one withheld beside it -- invented, with no number on either. */
const ruleC = (sheet) => ({
  ...sheet,
  rows: [
    ...sheet.rows,
    { key: 'E8', label: 'INVENTED SMALL EMPLOYER', level: 'ROW', fewerThanFive: true, withheld: false, cells: sheet.columns.map(() => null) },
    { key: 'E9', label: 'INVENTED NEIGHBOURING EMPLOYER', level: 'ROW', fewerThanFive: false, withheld: true, cells: sheet.columns.map(() => null) },
  ],
});

const statisticAnswer = (statistic, month, query) => {
  const refused = monthRefused(month);
  if (refused) return refused;
  const area = query.get('area');
  const region = query.get('region');
  if (area && region) return envelope('REQUEST_MALFORMED', 'Ask for an area or a region, not both.', { field: 'region' });
  if (area && !AREAS.has(area)) return envelope('SUMMARY_AREA_UNKNOWN', 'That area is not on record here.', { field: 'area' });
  if (region && !REGIONS.has(region)) return envelope('SUMMARY_AREA_UNKNOWN', 'That region is not on record here.', { field: 'region' });
  const example = golden(`GET /api/month-end/{month}/${statistic}`);
  const taken = MONTHS.taken.find((record) => record.month === month);
  // The golden answer is the taken month's, and its sheets' titles name it: they name the month asked instead. Only
  // the titles -- the year to date's note names the months taken, and they are what they are.
  const sheets = example.sheets.map((sheet) => ({
    ...sheet,
    title: sheet.title.replaceAll(monthName(example.month), monthName(month)),
  }));
  return {
    status: 200,
    body: {
      ...example,
      month,
      state: taken ? 'TAKEN' : 'OPEN',
      takenAt: taken?.takenAt ?? null,
      takenBy: taken?.takenBy ?? null,
      area: area || null,
      region: area ? null : region || null,
      sheets: statistic === 'branches' ? sheets.map(ruleC) : sheets,
      // The open month is read live: the note saying when a month was taken is the taken month's alone.
      notes: taken ? example.notes : example.notes.filter((note) => !/ was taken on /.test(note)),
    },
  };
};

const PACK_ID = 7001;
const packExample = golden('GET /api/month-end/packs/{id}');
let pack = null;
let packReads = 0;
const packOf = (status) => ({
  ...packExample, id: PACK_ID, month: pack.month, status,
  finishedAt: status === 'READY' ? packExample.finishedAt : null,
  workbooks: status === 'READY' ? packExample.workbooks : [],
});
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Month end's own routes, each refusal carrying the path it was asked at, as the server's envelope does. */
const monthEndOwnAnswer = (req, path, query) => {
  const answer = monthEndShape(req, path, query);
  return answer?.body?.code ? { ...answer, body: { ...answer.body, path } } : answer;
};

/** Month end's own routes by their shape; `false` for a path that is not one of them. */
const monthEndShape = (req, path, query) => {
  if (req.method === 'GET' && path === '/api/month-end/months') return { status: 200, body: MONTHS };
  let m = /^\/api\/month-end\/([^/]+)\/([a-z-]+)$/.exec(path);
  if (req.method === 'GET' && m && MONTH_STATISTICS.includes(m[2])) return statisticAnswer(m[2], m[1], query);
  if (req.method === 'POST' && m && m[2] === 'pack') {
    const refused = monthRefused(m[1]);
    if (refused) return refused;
    pack = { month: m[1] };
    packReads = 0;
    return { status: 202, body: packOf('PREPARING') };
  }
  // The take (MONTH_END_TAKE) answers the month as it now stands, as the server's MonthEndMonthDto; nothing is stored.
  if (req.method === 'POST' && m && m[2] === 'take') {
    const held = heldBy(req, path, 'MONTH_END_TAKE') ?? monthRefused(m[1]);
    if (held) return held;
    const taken = MONTHS.taken.find((record) => record.month === m[1]);
    return { status: 200, body: taken ?? { month: m[1], state: 'TAKEN', takenAt: '2026-10-07T08:00:00Z', takenBy: 'Surface Tester (surface.tester)' } };
  }
  // A listing (MEMBERS_VIEW) answers the golden listing's shape for the month and listing asked -- no member in it.
  m = /^\/api\/month-end\/([^/]+)\/listings\/([^/]+)$/.exec(path);
  if (req.method === 'GET' && m) {
    return heldBy(req, path, 'MEMBERS_VIEW') ?? monthRefused(m[1])
      ?? { status: 200, body: { ...listingExample, month: m[1], listing: m[2] } };
  }
  m = /^\/api\/month-end\/packs\/(\d+)(?:\/([^/]+))?$/.exec(path);
  if (req.method === 'GET' && m) {
    const known = pack && Number(m[1]) === PACK_ID;
    if (!m[2]) {
      if (!known) return envelope('PACK_NOT_FOUND', null, { pack: m[1] });
      packReads += 1;
      return { status: 200, body: packOf(packReads <= 2 ? 'PREPARING' : 'READY') };
    }
    if (!known || packReads <= 2 || !packExample.workbooks.includes(m[2])) {
      return envelope('PACK_NOT_FOUND', null, { pack: m[1], workbook: m[2] });
    }
    return {
      status: 200,
      type: XLSX,
      headers: { 'content-disposition': `attachment; filename="sasbo-${pack.month}-${m[2]}.xlsx"` },
      raw: Buffer.from(`INVENTED STUB WORKBOOK ${m[2]} ${pack.month} -- serve-dist.mjs, not a spreadsheet\n`, 'utf8'),
    };
  }
  m = /^\/api\/month-end\/([^/]+)$/.exec(path);
  if (req.method === 'GET' && m) {
    const refused = monthRefused(m[1]);
    if (refused) return refused;
    const taken = MONTHS.taken.find((record) => record.month === m[1]);
    return { status: 200, body: taken ?? { month: m[1], state: 'OPEN', takenAt: null, takenBy: null } };
  }
  // Anything else under /api/month-end is a route the server does not have.
  return path.startsWith('/api/month-end') ? { status: 404, body: { status: 404, error: 'Not Found', path } } : false;
};

/**
 * SIGN-IN (contracts/signin.md), for the browser run: the mode is `open`, and `GET /api/auth/me` answers the user a
 * surface's planted token stands for (`surfaceSession.mjs`) -- anyone else is `SIGN_IN_REQUIRED`, as the server says.
 * Signing in is always refused (`SIGN_IN_FAILED`): a surface is signed in by planting a session, never by typing one,
 * so the refusal is the one sign-in state worth measuring. Signing out and a password change succeed and store nothing.
 * `false` when the path is not one of these.
 */
const refused = (status, code, message) => ({ status, body: { code, message, status } });
const authAnswer = (req, path) => {
  if (req.method === 'GET' && path === '/api/auth/mode') return { status: 200, body: { mode: 'open' } };
  if (req.method === 'GET' && path === '/api/auth/me') {
    const user = userForToken(req.headers.authorization);
    return user ? { status: 200, body: { user } } : refused(401, 'SIGN_IN_REQUIRED', 'Sign in to use this.');
  }
  if (req.method === 'POST' && path === '/api/auth/login') {
    return refused(401, 'SIGN_IN_FAILED', 'The username or the password is not right.');
  }
  if (req.method === 'POST' && (path === '/api/auth/logout' || path === '/api/auth/password')) return { status: 204, raw: '' };
  return false;
};

/**
 * MAINTENANCE'S USERS, POSITIONS, ROLES AND THE RECORD OF WHO DID WHAT (contracts/signin.md), for the signed-in
 * surfaces. The provisional positions and their roles as the server seeds them (`SignInSeeder`: permissions.md's, and
 * the two named in the room on 5 Oct); invented users across them, each scope a real code of the golden lists (regions
 * NORTH and SOUTH, areas A, B and Y); several pages of the record. Writes are answered and nothing is stored: a new user
 * is always id 9201, and every temporary password is the same obviously-invented string, which is what a surface
 * photographs.
 */
const ALL_PERMISSIONS = {
  USERS_ADMIN: 'Create and edit users, positions and roles; reset a password; deactivate a user',
  AUDIT_VIEW: 'Read the record of who did what',
  MEMBERS_VIEW: 'See a listing that names members, within the user\'s region or area',
  MEMBERS_CAPTURE: 'Capture a new member',
  MEMBERS_EDIT: 'Edit a member\'s record and change a member\'s status',
  REFERENCE_MAINTAIN: 'Maintain lists: banks and branches, workplaces, recruitment channels, reasons',
  SUBSCRIPTIONS_MAINTAIN: 'Change subscription amounts',
  MONTH_END_VIEW: 'Read month-end figures and statistics',
  MONTH_END_TAKE: 'Take the month, and take it again with a reason',
  MONTH_END_SEND: 'Produce, download and e-mail the month\'s packs, bank files and provider files',
  DOD_PREPARE: 'Prepare a debit order run, record unpaids and suspensions, confirm endings',
  DOD_FILE: 'Download the debit order file for the bank',
  DOD_SETTINGS: 'Debit order rates, non-working days and moving a bank\'s members to a new debit day',
  PAYROLL_PROCESS: 'Load employer payroll files, work discrepancies, raise and close queries',
  PAYROLL_VIEW: 'Read payroll reconciliation results and reports',
  VISITS_CAPTURE: 'Capture and submit a workplace visit',
  REPORTS_PRINT: 'Print organisers\' reports and location listings',
  MIGRATION_RUN: 'Run legacy data imports and their checks',
};
const PROVISIONAL = [
  ['SYSTEM_ADMINISTRATOR', 'System Administrator', Object.keys(ALL_PERMISSIONS)],
  ['HEAD_OFFICE_ADMINISTRATOR', 'Head Office Administrator', ['AUDIT_VIEW', 'MEMBERS_VIEW', 'MEMBERS_CAPTURE', 'MEMBERS_EDIT',
    'REFERENCE_MAINTAIN', 'MONTH_END_VIEW', 'MONTH_END_TAKE', 'MONTH_END_SEND', 'DOD_PREPARE', 'PAYROLL_PROCESS', 'PAYROLL_VIEW',
    'VISITS_CAPTURE', 'REPORTS_PRINT']],
  ['CHIEF_FINANCIAL_OFFICER', 'Chief Financial Officer', ['MEMBERS_VIEW', 'MONTH_END_VIEW', 'DOD_FILE', 'DOD_SETTINGS',
    'SUBSCRIPTIONS_MAINTAIN', 'PAYROLL_VIEW']],
  ['SENIOR_SECRETARIAT', 'Senior Secretariat', ['MEMBERS_VIEW', 'MONTH_END_VIEW', 'REPORTS_PRINT']],
  ['REGIONAL_SECRETARY', 'Regional Secretary', ['MEMBERS_VIEW', 'MONTH_END_VIEW', 'VISITS_CAPTURE', 'REPORTS_PRINT']],
  ['REGIONAL_ORGANISER', 'Regional Organiser', ['MEMBERS_VIEW', 'VISITS_CAPTURE', 'REPORTS_PRINT']],
  ['ORGANISER', 'Organiser', ['MEMBERS_VIEW', 'MEMBERS_CAPTURE', 'VISITS_CAPTURE', 'REPORTS_PRINT']],
  ['MSO', 'MSO (to be confirmed)', ['MEMBERS_VIEW', 'MEMBERS_CAPTURE', 'VISITS_CAPTURE', 'REPORTS_PRINT']],
];
// As the server seeds them (SignInSeeder): ordinals 10 to 80, the System Administrator's role `fixed`, each with a version.
const adminPositions = PROVISIONAL.map(([code, name], i) => ({ code, name, defaultRoleCode: code, active: true, ordinal: (i + 1) * 10, version: 1 }));
const adminRoles = PROVISIONAL.map(([code, name, permissions]) => ({ code, name, permissions, fixed: code === 'SYSTEM_ADMINISTRATOR', version: 1 }));
const SCOPES = [{ type: 'ALL', codes: [] }, { type: 'REGION', codes: ['NORTH'] }, { type: 'AREA', codes: ['A', 'B'] }, { type: 'AREA', codes: ['Y'] }];
const adminUsers = ['Ada', 'Ben', 'Cleo', 'Dumi', 'Eve', 'Femi', 'Gugu', 'Hal', 'Ivy', 'Jabu', 'Kea', 'Lulu'].map((first, i) => {
  const [code, name, permissions] = PROVISIONAL[i % PROVISIONAL.length];
  const scope = code.startsWith('REGIONAL_') ? SCOPES[1] : code === 'ORGANISER' || code === 'MSO' ? SCOPES[2 + (i % 2)] : SCOPES[0];
  return {
    id: 9101 + i, username: `${first.toLowerCase()}.synthetic`, displayName: `${first} Synthetic`,
    email: `${first.toLowerCase()}.synthetic@example.invalid`, position: { code, name }, role: { code, name }, permissions, scope,
    mustChangePassword: i === 11, lastSignInAt: i === 11 ? null : `2026-10-0${1 + (i % 6)}T0${6 + (i % 3)}:15:00Z`,
    active: i !== 10, version: 1 + (i % 3),
  };
});
const AUDIT_ACTIONS = [['SIGN_IN', 'USER', 'Signed in'], ['SIGN_IN_FAILED', 'USER', 'Sign-in refused: the password did not match'],
  ['USER_UPDATED', 'USER', 'Changed: position, scope'], ['PASSWORD_RESET', 'USER', 'Temporary password issued; 2 sessions ended'],
  ['ROLE_UPDATED', 'ROLE', 'Permissions: 1 added, 0 removed'], ['MONTH_TAKEN', 'MONTH', 'September 2026 taken: 6 statistics frozen']];
const auditEntries = Array.from({ length: 120 }, (_, i) => {
  const [action, entityType, summary] = AUDIT_ACTIONS[i % AUDIT_ACTIONS.length];
  const who = adminUsers[i % 4];
  return {
    id: 50000 - i, at: `2026-10-${String(6 - Math.floor(i / 24)).padStart(2, '0')}T${String(16 - (i % 10)).padStart(2, '0')}:${String((i * 7) % 60).padStart(2, '0')}:00Z`,
    username: who.username, displayName: who.displayName, action, entityType,
    entityId: entityType === 'MONTH' ? '2026-09' : entityType === 'ROLE' ? 'ORGANISER' : String(adminUsers[(i + 3) % 12].id), summary,
  };
});
const pageOf = (rows, query) => {
  const size = Math.min(Math.max(Number(query.get('size') ?? 50) || 50, 1), 200);
  const page = Math.max(Number(query.get('page') ?? 0) || 0, 0);
  return { content: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) };
};
const TEMPORARY = 'surface-temporary-password';
const adminAnswer = (req, path, query) => {
  if (req.method === 'GET' && path === '/api/admin/users') {
    const q = (query.get('q') ?? '').toLowerCase();
    return { status: 200, body: pageOf(adminUsers.filter((u) => !q || `${u.displayName} ${u.username}`.toLowerCase().includes(q)), query) };
  }
  if (req.method === 'POST' && path === '/api/admin/users') {
    return { status: 201, body: { user: { ...adminUsers[5], id: 9201, version: 0, mustChangePassword: true, lastSignInAt: null }, temporaryPassword: TEMPORARY } };
  }
  const user = /^\/api\/admin\/users\/(\d+)(\/reset-password)?$/.exec(path);
  if (user && req.method === 'POST' && user[2]) return { status: 200, body: { temporaryPassword: TEMPORARY } };
  if (user && req.method === 'PUT') {
    const found = adminUsers.find((u) => u.id === Number(user[1])) ?? adminUsers[0];
    return { status: 200, body: { user: { ...found, version: found.version + 1 } } };
  }
  if (req.method === 'POST' && path === '/api/admin/positions') return { status: 201, body: adminPositions[0] };
  if (req.method === 'PUT' && path.startsWith('/api/admin/positions/')) return { status: 200, body: adminPositions[0] };
  if (req.method === 'POST' && path === '/api/admin/roles') return { status: 201, body: adminRoles[0] };
  if (req.method === 'PUT' && path.startsWith('/api/admin/roles/')) return { status: 200, body: adminRoles[0] };
  if (req.method === 'GET' && path === '/api/audit') return { status: 200, body: pageOf(auditEntries, query) };
  return false;
};

/** Month end's shapes a prefix match cannot answer; `false` when the path is not one of them. */
const monthEndAnswer = (req, path, query) => {
  if (path.startsWith('/api/month-end')) return monthEndOwnAnswer(req, path, query);
  // A workplace's visits -- a prefix match would answer them with the workplaces' page.
  const visits = /^\/api\/workplaces\/(\d+)\/visits$/.exec(path);
  if (visits && req.method === 'GET') return { status: 200, body: { ...visitsPage, workplaceId: Number(visits[1]) } };
  if (visits && req.method === 'POST') return { status: 201, body: visitRecorded.body };
  // October build: a branch's members and the visit drafts -- a prefix match would answer them with the workplaces' page.
  const branch = /^\/api\/workplaces\/(\d+)\/members$/.exec(path);
  if (branch && req.method === 'GET') return { status: 200, body: { ...branchMembers, workplaceId: Number(branch[1]) } };
  if (path === '/api/workplaces/members' && req.method === 'GET') {
    const ids = (query.get('ids') ?? '').split(',').filter((id) => /^\d+$/.test(id)).map(Number);
    const [one] = batchMembers.branches;
    return { status: 200, body: { ...batchMembers, unknown: [], branches: ids.map((workplaceId) => ({ ...one, workplaceId })) } };
  }
  if (path === '/api/workplaces/visit-drafts') {
    return req.method === 'POST' ? { status: 201, body: draftCreated.body } : { status: 200, body: myDrafts };
  }
  const draft = /^\/api\/workplaces\/visit-drafts\/(\d+)(\/submit)?$/.exec(path);
  if (draft && draft[2] && req.method === 'POST') return { status: 200, body: draftSubmitted };
  if (draft && !draft[2] && req.method === 'PUT') return { status: 200, body: draftSavedAgain };
  if (draft && !draft[2] && req.method === 'GET') return { status: 200, body: oneDraft };
  if (draft && !draft[2] && req.method === 'DELETE') return { status: 204, raw: '' };
  if (req.method === 'GET' && path === '/api/workplaces') {
    const page = Number(query.get('page') ?? 0);
    const size = Number(query.get('size') ?? WORKPLACE_PAGE);
    if (!(size > 0) || !(page >= 0) || page >= Math.ceil(workplaces.length / size)) {
      return { status: pageRefused.status, body: pageRefused.body };
    }
    return { status: 200, body: workplacePage(page, size) };
  }
  // A member's history (03.6) -- a prefix match would answer it with the list of members.
  const history = /^\/api\/members\/(\d+)\/history$/.exec(path);
  if (req.method === 'GET' && history) return historyAnswer(Number(history[1]), path, query);
  // It answers the golden change whatever is asked: it measures the dialog, not the rule.
  const m = /^\/api\/members\/(\d+)\/status$/.exec(path);
  if (req.method === 'POST' && m) return { status: 200, body: { ...statusChanged, memberId: Number(m[1]) } };
  return false;
};

const API = {
  // Sign-in's five routes, each answered by `authAnswer` before this table is read; keyed here so the drift check
  // below sees every one of them answered by its own stub.
  '/api/auth/mode': { mode: 'open' },
  '/api/auth/me': { user: surfaceUser([]) },
  '/api/auth/login': {},
  '/api/auth/logout': {},
  '/api/auth/password': {},
  // Maintenance's users, positions, roles and the record (`adminAnswer` pages and writes; these keys cover the reads).
  '/api/admin/users': { content: adminUsers, page: 0, size: 50, totalElements: adminUsers.length, totalPages: 1 },
  '/api/admin/positions': adminPositions,
  '/api/admin/roles': adminRoles,
  '/api/admin/permissions': Object.entries(ALL_PERMISSIONS).map(([name, description]) => ({ name, description })),
  '/api/audit': { content: auditEntries.slice(0, 50), page: 0, size: 50, totalElements: auditEntries.length, totalPages: 3 },
  '/api/members': members,
  '/api/members/summary': memberSummary,
  // Month end's reads, from the server's golden answers (`golden`, above). The workplaces' key answers their first
  // page; every page, and its size, is answered per request by `monthEndAnswer`.
  '/api/reference/regions': golden('GET /api/reference/regions'),
  '/api/reference/areas': golden('GET /api/reference/areas'),
  '/api/reference/employer-groups': golden('GET /api/reference/employer-groups'),
  '/api/reference/employers': golden('GET /api/reference/employers'),
  '/api/reference/table-codes': golden('GET /api/reference/table-codes'),
  '/api/reference/committee-provinces': golden('GET /api/reference/committee-provinces'),
  '/api/reference/committees': golden('GET /api/reference/committees'),
  '/api/reference/deletion-codes': golden('GET /api/reference/deletion-codes'),
  '/api/reference/suspension-codes': golden('GET /api/reference/suspension-codes'),
  '/api/reference/status': golden('GET /api/reference/status'),
  '/api/workplaces': workplacePage(0, WORKPLACE_PAGE),
  // October build: the batch print by R/A number, and my visit drafts (one draft, a save and a submission are answered
  // per shape by `monthEndAnswer`). The keys are what `stubDrift` reads, so each service path is covered by name.
  '/api/workplaces/listings': listingsBatch,
  '/api/workplaces/visit-drafts': myDrafts,
  // Month end `04.1`-`04.10`. Every path under these three is answered by `monthEndOwnAnswer` first -- a statistic, a
  // pack and a month each by its shape; the keys are what `stubDrift` reads, so the services' paths are covered by
  // name. The longer two stop the shorter one answering the months or a pack by prefix.
  '/api/month-end/months': MONTHS,
  '/api/month-end/packs': packExample,
  '/api/month-end': MONTHS,
  // The runs list; one run and its sheets are answered per shape by `acbRunAnswer`.
  '/api/acb/runs': acbPage(acbRuns, 12),
  // The Debits screen's member search (`04.1`) -- a POST, answered by `acbRunAnswer` first; the key is what
  // `stubDrift` reads, so the path is covered by name rather than by accident.
  '/api/acb/members/search': { content: acbFound, page: 0, size: 25, totalElements: 57, totalPages: 3 },
  '/api/acb/termination-report': {
    countingFrom: '2026-07',
    blindness: 'Counted from the first month this system sent a file.',
    members: acbPage(acbTermination, 50),
  },
  // The dated rates the Runs screen reads (Q43): V41's one row, and no January notice in September.
  '/api/acb/rates': {
    rates: [{ effectiveFrom: '2026-01-01', amount: 174, source: 'From January -- confirmed by SASBO 2026-09-21 (Q43)' }],
    januaryNotice: null,
  },
  // The holiday calendar the Runs screen reads (D30): V47's, far from its end, so no notice.
  '/api/acb/holidays': {
    name: 'za-statutory', knownUntil: '2027-12-31', endingSoon: false, year: 2026,
    holidays: [
      { date: '2026-09-24', name: 'Heritage Day', source: 'STATUTORY' },
      { date: '2026-12-16', name: 'Day of Reconciliation', source: 'STATUTORY' },
      { date: '2026-12-25', name: 'Christmas Day', source: 'STATUTORY' },
    ],
  },
  '/api/banks': bankRows,
  '/api/banks/branches': bankRows,
  // `Locations` searches `/api/branches?...` and hands each result to `LocationListing`,
  // which renders `data.members` -- so a bare branch row produces an EMPTY table and the
  // eleven-column layout the matrix most wants to measure never appears.
  '/api/branches': bankRows.map((b) => ({
    ...b,
    branchCode: b.randomAccessNumber,
    areaRepName: 'Synthetic Area Rep',
    becMembers: '4', memnetMembers: '9', bankManager: 'Synthetic Manager',
    bankManagerEmail: 'manager.synthetic@example.invalid',
    alternativeContact: 'Synthetic Alternate', alternativeContactEmail: 'alt.synthetic@example.invalid',
    complement: '-', membership: '28', lastVisited: '2026-08-01',
    members: members.slice(0, 14).map((m) => ({
      memNo: m.membershipNumber, name: `${m.title} ${m.initials} ${m.surname}`,
      bankAt: m.bankName, subscr: m.monthlySubscription, empNum: m.employeeNumber,
      email: m.workEmail, id: m.identityNumber, cell: m.phone, joined: m.joinDate,
      susp: '-', label: 'Y', alternateAddress: '1 Percy Street, Fourways, 2191',
    })),
  })),
  '/api/recruitment-channels': Array.from({ length: 12 }, (_, i) => ({
    id: i + 1, name: `Synthetic Channel ${i + 1}`, enabled: i % 3 !== 0,
  })),
  '/api/representatives': Array.from({ length: 8 }, (_, i) => ({
    id: i + 1, name: `Synthetic Recruiter ${i + 1}`, email: 'rep.synthetic@example.invalid',
    phone: '0110000000', memberId: i + 1, roleId: 1, isElectedRep: i % 2 === 0,
    electionDate: '2024-06-01', recruiterTypeId: 1, structureIds: [],
  })),
  // `recruiterService.getRecruiters()` -- the Capture Member re-port's own endpoint (main `bb1052b`), which reached
  // this branch in P10's merge. The drift guard below refused to serve until it had a stub (P10, 2026-09-18): a
  // Recruiter drop-down answered by nothing is a capture screen measured with an empty control.
  '/api/recruiters': Array.from({ length: 8 }, (_, i) => ({ id: i + 1, name: `Synthetic Recruiter ${i + 1}` })),
  // `recruiterService.getRoles()` and `.getStructures()`. These were `/api/roles` and
  // `/api/structures`, which nothing calls, so both lists were being answered by `/api/members`
  // -- see `stubDrift` below.
  '/api/members/userRoles': [{ id: 1, name: 'Lead Recruiter' }, { id: 2, name: 'Standard Recruiter' }],
  // Whether this server emails a captured member their welcome letter (A64) -- asked before Send, and answered by
  // `/api/members` (a list) until it had a key of its own. Yes, so Send goes through `send-welcome` below as it did
  // before A64, and the sent state stays measurable.
  '/api/members/metadata/welcome-letter': { emailEnabled: true },
  '/api/members/structures/tree': [{ id: 1, name: 'INC / IC', parentId: null, children: [] }],
  '/api/provinces': ['Gauteng', 'Western Cape', 'KwaZulu-Natal'].map((name, i) => ({ id: i + 1, name })),
  '/api/areas': ['Gauteng South', 'Western Cape'].map((name, i) => ({ id: i + 1, name })),
  '/api/districts': [{ id: 1, name: 'Synthetic District' }],
  '/api/job-grades': [{ code: 'A1', description: 'Clerk' }, { code: 'B2', description: 'Officer' }],
  '/api/subscriptions': Array.from({ length: 15 }, (_, i) => ({
    id: i + 1, bankName: banks[i % banks.length], acbDebitDay: 25,
    subscriptionAmount: 182.77, isCoreBanking: i % 2 === 0,
    status: ['ACTIVE', 'PENDING', 'CANCELLED', 'INACTIVE'][i % 4],
    registrationDate: '2023-01-15',
  })),
};

/**
 * The stub routes, longest first, established ONCE.
 *
 * It was re-keyed and re-sorted on every `/api/` hit — a per-request recomputation of a
 * constant, in the one server all ~700 navigations of a full sweep pass through. Sorting here
 * also makes "longest match first" a property of a value rather than a claim in a comment.
 */
const API_KEYS = Object.keys(API).sort((a, b) => b.length - a.length);

/**
 * 🔴 A STUB THAT ANSWERS FOR THE WRONG PATH IS SILENT -- AND IT HAPPENED.
 *
 * Matching is by prefix (see below), so when the client moved `getRoles()` to
 * `/api/members/userRoles` and `getStructures()` to `/api/members/structures/tree`, both were
 * answered by `/api/members`: sixty members handed to a roles picker and to a structure tree.
 * Nothing 501'd, because something matched -- and the keys the client had moved away from sat
 * in this table answering nothing. Found 2026-09-14, by `06.3`.
 *
 * So the table is checked against the paths the services actually call, once, before serving:
 * every service path must be answered by a key that covers its whole fixed part, and every key
 * must be called by something. Either failure refuses to serve, because a sweep over the wrong
 * stubs measures pages nobody will ever see.
 *
 * ⚠️ IT READS `apiUrl('<literal>')` IN `src/services`, the same net `apiContract.test.js` casts,
 * so it has to be run from the client's root -- which is where every measurement runs from.
 */
/**
 * Service paths deliberately NOT stubbed, each with its reason -- and each asserted to still be
 * called, because an exception list nobody checks against the code rots in silence (T34).
 */
const NOT_STUBBED = {
  // A2, pinned in `apiContract.test.js`: the server has no such route, and no screen imports the
  // service that calls it. A stub here would measure against an endpoint production does not have.
  '/api/debounce': 'A2',
};

const stubDrift = () => {
  let files;
  try {
    files = readdirSync('src/services').filter((f) => f.endsWith('.js') && !f.includes('.test.'));
  } catch (e) {
    throw new Error(`serve-dist runs from the client root, and cannot read src/services here (${e.message})`);
  }
  const called = new Set();
  for (const file of files) {
    // `/api/[a-z]`: a doc comment's `apiUrl('/api/...')` is an example, not a call.
    for (const [, literal] of readFileSync(join('src/services', file), 'utf8').matchAll(/apiUrl\(\s*[`'"](\/api\/[a-z][^`'"]*)[`'"]/g)) {
      // The fixed part: everything before the first interpolation or query string.
      called.add(literal.split('${')[0].split('?')[0]);
    }
  }
  if (called.size < 10) {
    return [`read only ${called.size} service paths from src/services -- the scan is not seeing the services`];
  }

  const problems = [];
  for (const path of [...called].sort()) {
    if (path in NOT_STUBBED) continue;
    const key = API_KEYS.find((k) => path.startsWith(k));
    if (!key) problems.push(`${path} has no stub at all`);
    // What is left over may only be the slash in front of an id. Anything else is a path this
    // key was never written for.
    else if (!['', '/'].includes(path.slice(key.length))) problems.push(`${path} is answered by the shorter stub ${key}`);
  }
  for (const key of API_KEYS) {
    if (![...called].some((path) => path.startsWith(key))) problems.push(`${key} is stubbed and no service calls it`);
  }
  for (const path of Object.keys(NOT_STUBBED)) {
    if (!called.has(path)) problems.push(`${path} is excused from stubbing and no service calls it any more -- drop the excuse`);
  }
  return problems;
};

/**
 * Payloads serialised once at startup.
 *
 * `/api/branches` is 189 KB of JSON and cost 0.6ms per call to stringify; `/api/locations` is
 * 57 KB and `/api/members` 46 KB. Holding the buffers costs ~470 KB of memory and removes both
 * the CPU and ~189 KB of garbage from every hit.
 */
const API_BODY = new Map(API_KEYS.map((k) => [k, Buffer.from(JSON.stringify(API[k]), 'utf8')]));

/**
 * 🔴 UNMATCHED `/api/` PATHS ARE A LOUD 501, AND THEY USED TO BE A CHEERFUL `200 []`.
 *
 * That default is the exact failure this whole family of tools is written against. A client that
 * renames an endpoint, or a stub table that drifts from the app, produced an EMPTY TABLE on a
 * page that rendered perfectly: it cleared `pageReady`, cleared the 8-element population floor,
 * and never overflowed — because an empty page is the most reliably non-overflowing page there
 * is. Every "0 overflow" in the phase rested on this default not having fired.
 *
 * It is also not hypothetical here: the sweep that found this was itself run against a build
 * whose tables were empty, and only the population canary caught it.
 *
 * So an unmatched path answers **501** and is COUNTED, and the count is printed at shutdown and
 * folded into the exit code — a drifted stub reddens a lane instead of producing a clean sweep of
 * empty tables.
 *
 * ⚠️ MATCHING IS STILL PREFIX-BASED, deliberately: the app calls `/api/members/42/banking` and
 * `/api/banks/branches/7`, so a strict exact-match table would need every id. What changed is
 * that a path matching NOTHING is an error rather than an empty array.
 */
const unmatched = new Map();

/*
 * THE REGISTER A PAGE AT A TIME (A10): `POST /api/members/register/search` answers one page of the synthetic members
 * -- the page and size asked, in the register's own order (surname) -- and `GET /api/members/register/overview` their
 * figures and branches. A prefix match would answer both with the whole list, which the register reads as "not a page"
 * and shows as a failure on every /members surface.
 */
const registerRow = ({ id, membershipNumber, employeeNumber, title, initials, surname, firstNames, bankName, branch, status }) =>
  ({ id, membershipNumber, employeeNumber, title, initials, surname, firstNames, bankName, branch, status });
const registerPage = (query) => {
  const size = Number.isInteger(query?.size) && query.size > 0 ? Math.min(query.size, 100) : 50;
  const ordered = [...members].sort((a, b) => a.surname.localeCompare(b.surname) || a.id - b.id);
  const totalPages = Math.max(1, Math.ceil(ordered.length / size));
  const page = Math.min(Math.max(0, Number.isInteger(query?.page) ? query.page : 0), totalPages - 1);
  return {
    content: ordered.slice(page * size, (page + 1) * size).map(registerRow),
    page, size, totalElements: ordered.length, totalPages, sort: 'surname,asc',
  };
};
const registerOverview = () => {
  const by = (status) => members.filter((m) => m.status === status).length;
  return {
    total: members.length, active: by('Active'), pending: by('Pending'), suspended: by('Suspended'),
    newThisMonth: 3, newDebitOrders: 2, updatedToday: 1,
    branches: [...new Set(members.map((m) => m.branch))].sort(), asOf: '2026-10-07',
  };
};

const server = createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const path = decodeURIComponent(url.pathname);

  if (path.startsWith('/api/')) {
    // The two writes the capture form's welcome letter needs, answered as the server does (MemberController): a saved
    // member comes back WITH its id, and `send-welcome` is an EMPTY 200 -- the shape A50 was about, so a measurement of
    // the "sent" state goes through the same read the application does. Nothing is stored: every save is id 9001.
    if (req.method === 'POST' && path === '/api/members') {
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ id: 9001 }));
      return;
    }
    if (req.method === 'POST' && /^\/api\/members\/\d+\/send-welcome$/.test(path)) {
      res.writeHead(200);
      res.end();
      return;
    }
    if (req.method === 'POST' && path === '/api/members/register/search') {
      let raw = '';
      req.on('data', (chunk) => { raw += chunk; });
      req.on('end', () => {
        let query = {};
        try { query = JSON.parse(raw || '{}'); } catch { /* an unreadable body asks for the register's first page */ }
        res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(registerPage(query)));
      });
      return;
    }
    if (req.method === 'GET' && path === '/api/members/register/overview') {
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(registerOverview()));
      return;
    }
    // The shapes a prefix match cannot answer: sign-in, A.C.B.'s runs, and month end's pages and change of status.
    const shaped = authAnswer(req, path) || adminAnswer(req, path, url.searchParams) || acbRunAnswer(req, path)
      || monthEndAnswer(req, path, url.searchParams);
    if (shaped) {
      // A JSON body, unless the shape carries bytes of its own type (the pack's workbook, an attachment).
      res.writeHead(shaped.status, { 'content-type': shaped.type ?? 'application/json; charset=utf-8', ...shaped.headers });
      res.end(shaped.raw ?? JSON.stringify(shaped.body));
      return;
    }
    const key = API_KEYS.find((k) => path.startsWith(k));
    if (!key) {
      // 🔴 LOGGED ON FIRST SIGHT, not only at shutdown. On Windows a measurement session ends
      // this process with a forced kill, which runs no SIGINT/SIGTERM handler — so a report
      // that only prints on the way out is a report that never prints. Once per distinct path,
      // so a route requested 400 times does not bury everything else.
      if (!unmatched.has(path)) console.error(`⚠ NO STUB: ${path} — answered 501`);
      unmatched.set(path, (unmatched.get(path) ?? 0) + 1);
      res.writeHead(501, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        error: 'no stub for this path',
        path,
        hint: 'add it to API in tools/serve-dist.mjs — an empty array here would measure as a '
          + 'page that renders fine and never overflows',
      }));
      return;
    }
    res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    res.end(API_BODY.get(key));
    return;
  }

  const rel = normalize(path).replace(/^([/\\])+/, '');
  const file = join(ROOT, rel);
  // One stat, not `existsSync` followed by `statSync` — two syscalls for one question.
  if (rel && isFile(file)) {
    res.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' });
    createReadStream(file).pipe(res);
    return;
  }

  // Everything else is a client route: the SPA shell.
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
  createReadStream(join(ROOT, 'index.html')).pipe(res);
});

const drift = stubDrift();
if (drift.length) {
  console.error('⚠ the stub table has drifted from what the services call -- refusing to serve:');
  for (const line of drift) console.error(`    ${line}`);
  process.exit(1);
}

// 🔴 EXPLICITLY 127.0.0.1, NOT `localhost` AND NOT THE DEFAULT (T23).
server.listen(PORT, '127.0.0.1', () => {
  const { address, port } = server.address();
  console.log(`serving ${ROOT} on http://${address}:${port}  (api stubbed, synthetic data)`);
});

/**
 * On the way out, say what the app asked for that this server does not have.
 *
 * 🔑 A STUB THAT DRIFTED IS SILENT BY NATURE, so the report is the point. The 501s above make a
 * single request loud; this makes the RUN loud, because a measurement session ends by killing
 * this process and nobody reads a log they were not told to look at. Non-zero exit so a script
 * that starts the server, sweeps and stops it can tell.
 */
const reportUnmatched = () => {
  if (!unmatched.size) {
    console.log('every /api/ path the app requested had a stub.');
    process.exit(0);
  }
  console.error(`\n⚠ ${unmatched.size} /api/ path(s) had NO STUB and were answered 501:`);
  for (const [path, hits] of [...unmatched].sort((a, b) => b[1] - a[1])) {
    console.error(`    ${String(hits).padStart(4)} × ${path}`);
  }
  console.error(
    '  Whatever consumed those paths measured an EMPTY surface. Add them to API and re-run —\n'
    + '  an empty table renders perfectly, clears every readiness check, and never overflows.',
  );
  process.exit(1);
};

process.on('SIGINT', reportUnmatched);
process.on('SIGTERM', reportUnmatched);
