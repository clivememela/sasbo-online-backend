/**
 * The defects this suite asserts deliberately, and does not fix.
 *
 * WHY A SKIPPED TEST IS EVER ACCEPTABLE HERE
 * There is no flaky list and nothing is skipped for unreliability. A skipped
 * test in this repository means exactly one thing: a test written to the
 * CORRECT behaviour, against a defect that is real, left visibly failing by
 * design. Fixing the defect is a production change to someone else's
 * repository, and this suite's job is to make it visible rather than to repair
 * it while nobody is looking.
 *
 * EVERY ROW CARRIES TWO TESTS, NOT ONE  <-- the client goes further than the server
 * The server half of this arc landed twelve rows, each with a skipped test.
 * A skipped test NEVER RUNS, so nothing in the suite notices the day a defect
 * is fixed -- the test stays skipped, the register stays stale, and the
 * repair is invisible. Two of the server's twelve rows carry a hand-written
 * "still reproduces" pin; the other ten do not.
 *
 * Here that pin is a PROPERTY OF THE REGISTER rather than a habit:
 *
 *   disabled(row)  the standard-facing test. Skipped. Says what SHOULD happen.
 *   pin(row)       a live test that RUNS on every suite run and asserts the
 *                  defect's CURRENT behaviour. The day someone fixes the
 *                  defect, the pin goes red and names the row -- which is the
 *                  signal to delete both tests and close the register row.
 *
 * `defectPopulation.test.js` asserts the correspondence in both directions:
 * every skipped test cites a row here, every row here has both a skipped test
 * and a live pin, and no test anywhere is skipped without citing a row.
 *
 * Row identifiers match the arc's defect register exactly. They are the only
 * thing that must never drift.
 *
 * 🔴 THE IDENTIFIERS ARE A SHARED NAMESPACE WITH NO ALLOCATOR, AND THIS FILE
 * HOLDS ONLY A SUBSET OF IT. Rows are numbered once, in the arc's register,
 * across all three repositories. This file carries the ones the browser suite
 * can assert -- never all of them, and never a number invented here.
 *
 * P4 got this wrong and it is worth the paragraph. It needed rows for the two
 * missing endpoints, did not find them in THIS file, and concluded they were
 * new -- inventing A21 and A22. Both already existed twice over: A1 and A2 had
 * predicted exactly these two endpoints and even named P4 as the phase that
 * would confirm them, while A21 and A22 were P2's rows for something else
 * entirely. So the work confirmed a standing prediction while citing two
 * unrelated defects, and a reader looking up A21 would have found a null
 * `jobGradeId` and concluded the note was wrong. (B2, the committed key, was
 * right only because the plan happened to name the number.)
 *
 * ⇒ BEFORE ADDING A ROW: search the register, not this file. Absence here means
 * the browser has no test for it yet, which is the common case and is not
 * evidence the defect is unknown.
 *
 *     grep -nE '^\| \*\*[A-F][0-9]+\*\*' <the register>
 */

const define = (row, headline, detail) => ({ row, headline, detail })

export const DEFECTS = {
  A9: define(
    'A9',
    'the client re-implements a rule the server publishes, and they disagree',
    'PaymentType.requiresBankAccount() is the server\'s authority and it is exposed at ' +
      'GET /api/members/metadata/payment-types. The client never calls it: ' +
      'usePaymentTypeValidation hardcodes bankAccountFieldDisabled: isPayrollDeduction. ' +
      'This is MASKED BY A6 today -- the branch is dead, so the rule never runs. Fixing ' +
      'A6 switches on code nothing has ever tested, which is the dangerous shape: a ' +
      'repair that activates untested behaviour.',
  ),

// A6 is RETIRED, 2026-09-17, by the capture-member re-port. It read: "the payment-type branch is
// dead code -- usePaymentTypeValidation compares against 'Payroll Deduction' and
// PAYMENT_TYPE_OPTIONS offers 'Payroll', so the branch can never be true."
//
// The BROWSER is what moved, not the hook. The server's PaymentType is PAYROLL("Payroll
// Deduction") and it is now on main (829f252), published in `api-vocabulary.json`, so the option
// takes the server's name and `Members.jsx` folds the legacy stored value onto it instead of the
// other way about. `usePaymentTypeValidation.test.jsx` keeps the scan that pinned this, flipped:
// exactly one offered option now reaches the branch.
//
// ⚠️ A9 IS UNCHANGED AND STAYS OPEN, and that is the honest reading of the pair. A9's own text
// warned that fixing A6 "switches on code nothing has ever tested". What it switches on is
// reachable now, but it is still not RUNNING: no screen imports usePaymentTypeValidation --
// checked again on the day A6 closed -- so both screens carry their own `paymentType === "Debit
// Order"` test and the hook's hardcoded rule reaches no member. Closing A6 changed the hook's
// reachability, not the application's behaviour, and A9's pin is what still says so.
//
// A7 is RETIRED, 2026-09-17, by the capture-member re-port: both screens compare against
// IDENTITY_TYPE_PASSPORT, so a passport member needs a date of birth on Capture AND Edit.
// A11 is RETIRED, 2026-09-09, by the design arc's 03.1.
//
// It read: "no error boundary -- one render error blanks the whole application".
// `ErrorBoundary` now sits beside the <Outlet> in both layouts, so a throwing page is
// a broken panel and the shell survives. Its pin was deleted and its standard-facing
// test un-skipped in the same commit -- flipped, not repaired (BR-5) -- and
// `src/test/errorBoundary.test.jsx` is now the regression pair rather than the record
// of a defect.
//
// It is removed rather than marked closed because this file's population IS the set of
// open rows: `defectPopulation.test.js` requires every entry here to have a live pin,
// and a "closed" entry with no pin is exactly the shape that guard exists to reject.
//
// ⚠️ What is NOT closed with it: `06.1` owns the DESIGNED failed state. The boundary's
// fallback today is plain and deliberately says so.

// A12 is deliberately NOT a row here, and that is a CORRECTION to the register
// rather than an omission.
//
// A12 reads: "four of nine client services do not check response.ok on every
// call, and two have no catch at all... so .json() succeeds and the component
// receives { status: 500, error: ... } AS DATA."
//
// The first half is true as a source observation. The SECOND half -- the
// consequence, which is what makes it a defect -- does not reproduce.
// Measured 2026-09-05 by executing all 31 methods under a 500 carrying the
// envelope: recruiterService, recruitmentChannelService and
// subscriptionsService all REJECT, because their shared `handleResponse` does
// check `r.ok`. Having no `catch` is harmless on its own; a rejection simply
// propagates to the caller, which is the correct behaviour anyway.
//
// The methods that really do hand a failure back as data are three, and each
// has its own row with its own evidence: A13, A25 and A26. Keeping A12 as a
// fourth, vaguer row would double-count them and leave a claim in the register
// that an executed test contradicts.
//
// What stands from A12 is recorded where it is provable: the CASE 2 assertion
// in services.errorContract.test.js runs against every method, so if a service
// ever does start resolving an error envelope, it reddens there and names the
// method.

// A13 is RETIRED, 2026-09-10, by the design arc's 06.1a.
//
// It read: "bankService turns a failed request into an empty list". `getBankNames` ended in
// `.catch(err => { console.error(...); return [] })`, so a 500, a network failure and a
// non-JSON body ALL resolved with an empty array -- and eight consumers across seven screens
// then rendered "no banks". A down backend and a database with no banks in it were the same
// screen, and the only trace was a console line no user sees. bank being the reference domain
// is what made it worse: it was the pattern every other domain was told to copy.
//
// It is on `requestJson` now (conventions.md §16) and REJECTS on all three cases, so an empty
// array means the server genuinely returned no banks. Closed under BR-12, with the owner's nod
// recorded in the 2026-09-09 dialog.
//
// Its two pins were deleted and its entry removed from the errorContract's BREACHES map in the
// same commit -- flipped, not repaired (contract 4).
//
// ✅ And the INNER `/api/banks` lookup, closed in `06.4` (F-P54). It fell back to `[]`, which was
// called deliberate -- an ENRICHMENT, so losing it should empty one column rather than the page, EH-D9's
// "partial failure stays partial". The degradation was right; its silence was not: Bank Listing printed
// "-" for every code and answered a search by code with "no banks match". `getBankListingDetailed`
// reports `codes: 'failed'` now, the page says the codes are missing, and
// `noSwallowedRejection.structural.test.js` convicts any `.catch(() => [])` that comes back.

// A14 is RETIRED, 2026-09-10, by the design arc's 06.1a.
//
// It read: "no request timeout anywhere -- a hung backend spins forever". Zero
// AbortController, zero `signal:`, zero timeout of any kind across `src/services/`. It was the
// failure mode with the longest time to detection in this application precisely because it
// looks like the system working: an administrator reads a permanent spinner as "slow" rather
// than "down", so nobody is called and nothing is escalated.
//
// Every service now threads an `AbortSignal` through `requestJson`, with the ceilings published
// in conventions.md §16 -- 10s reads, 20s writes, and a "taking longer than expected" notice at
// 5s that belongs to the component. A deadline we imposed rejects as `kind: 'timeout'`,
// retryable; a caller superseding its own request is `kind: 'abort'` and is not a failure at all.
//
// 🔑 ITS STATIC HALF IS WHY THIS ROW COULD NOT BE SPLIT, and it shaped the commit. That pin
// scanned `src/services/*.js` for `AbortController|signal:|AbortSignal|setTimeout` and asserted
// the match list was EMPTY -- so a ceiling on SOME services would have failed the scan without
// closing the row. The ceiling therefore landed across all eight services at once.
//
// Both pins deleted, the standard-facing test un-skipped, and the static assertion INVERTED
// rather than deleted: it now requires the service layer to still be able to express a ceiling,
// so a refactor that quietly drops the signal threading turns it red and names the row.
//
// 📨 The diff shape is owed to the Capture Member arc, whose own copy of this pin flips when the
// branches meet. Their session was offline when this landed; recorded in repoState.md.

  // A23 is RETIRED, 2026-10-06, by the October build's bugs stream: subscriptionsService.create POSTs the
  // subscription's row to /api/subscriptions (it POSTed { name } to /api/recruitment-channels). Its pin was deleted
  // and its standard-facing test un-skipped in the same commit (services.errorContract.test.js).

// A25 is RETIRED, 2026-09-10, by the design arc's 06.1b.
//
// It read: "jobGradeService.getByBank can never report a failure". The method ended in
// `.catch(() => JOB_GRADE_OPTIONS)`, written as a graceful degradation and genuinely one until
// JOB_GRADE_OPTIONS was EMPTIED -- after which every 500, dropped connection and HTML gateway
// page resolved as `[]` and both callers rendered "No job grades available". Neither change was
// wrong alone; the defect is what they became together, which is why it survived two reviews.
//
// 🔑 THE ROW'S OWN CRITERION IS WHY IT COULD NOT RIDE 06.1a WITH A13, A14 AND A24: "the method
// rejects AND the caller renders an error". Deleting the catch alone would have rejected into
// `CaptureMember` and `Members`, neither of which had an error state for a reference list -- an
// unhandled rejection where a quiet fallback used to be, which is a worse application and not a
// closed row. Both clauses landed in one commit: the catch is gone, `JOB_GRADE_OPTIONS` is gone
// from the service and from both callers, and each caller's Job Grade field renders the failure
// with a retry (EH-D9, per-panel: the other eight fetches on the capture form keep working).
//
// Its three pins were deleted and its three standard-facing cases un-skipped in the same commit
// -- flipped, not repaired (BR-5): the method's entry in `BREACHES` is removed, so the ordinary
// CASE 2 / 3 / 4 assertions now apply to it by discovery, and it joins the population of
// `what a rejection carries`.
//
// Removed rather than marked closed, for the reason A11 records: this file's population IS the
// set of open rows.

  A26: define(
    'A26',
    'bankService.updateBank echoes the caller input back as if the server had saved it',
    'On a 2xx whose body is not JSON -- a proxy or gateway page, typically -- updateBank ' +
      'falls through to "return { ...data, id }": the object the CALLER just passed in, ' +
      'handed back as the saved record. The tolerance is there for a legitimate empty ' +
      '200 body, but it does not distinguish an empty body from an unparseable one, so ' +
      'the screen shows the edit as confirmed when nothing was written. Measured ' +
      '2026-09-04.',
  ),

// A27 is RETIRED, 2026-09-10, by the design arc's 04.2.
//
// It read: "the Members list has no loading state and no error state -- only 'no
// members'". The register now renders through the `Table` primitive, whose four data
// states are separate branches by construction: `loading` is a live status row, `error`
// carries the message AND a retry that re-runs the fetch, and `error` can never render as
// `empty`. The page tracks one `listState` value rather than two booleans, so the illegal
// fourth combination cannot be reached.
//
// Its three pins were deleted and its two standard-facing tests un-skipped in the same
// commit -- flipped, not repaired (BR-5). The "CANNOT be told apart" test was INVERTED
// rather than deleted: it now asserts the two screens DIFFER, which is the same fact
// stated the right way round and the thing that would notice a regression.
//
// Removed rather than marked closed, for the reason A11 records: this file's population
// IS the set of open rows.
//
// ⚠️ What is NOT closed with it: `06.1b` owns generalising these states across the
// remaining list screens, and the shared error CONTRACT. This closes the row on the
// surface the row was written about.

// A28 is RETIRED, 2026-09-10, by the design arc's 04.2.
//
// It read: "the Members 'Identity Type' filter is a select with no accessible name",
// which axe rated CRITICAL. It was a bare <select> under a <span>Identity Type</span> --
// a span is not a label, so nothing associated the two and a screen-reader user reached a
// dropdown announced only as "combo box". It is now `SelectInput`, which carries a real
// <label htmlFor> through `Field`.
//
// Its pin was deleted and both standard-facing a11y tests un-skipped in the same commit.
// ⚠️ The FIX went in with the flip and the ASSERTION was never touched -- the pin
// asserted `['select-name']` exactly, and it is gone rather than edited to expect `[]`.
//
// 🔑 It had a SECOND site the register never named: the BEC committee <select> in the
// member dialog had the same defect, and the a11y gate could not see it because that gate
// scans the LIST page. Found by sweeping the class rather than fixing the list
// (contract 17), and fixed in the same commit through `Field`.

  B6: define(
    'B6',
    'apiUrl()\'s SSRF guard is bypassed by a backslash',
    'api.js strips a leading protocol and leading FORWARD slashes, with a comment ' +
      'saying it does so to prevent open redirect and SSRF. It does not strip ' +
      'BACKSLASHES -- and every URL parser that implements the WHATWG standard treats ' +
      'a backslash as a slash for http/https. So "\\\\\\\\evil.example/steal" survives the ' +
      'guard as "/\\\\\\\\evil.example/steal" and the browser resolves that to ' +
      'http://evil.example/steal: a different origin, reached by the one function ' +
      'written to make that impossible. "/\\\\evil.example/x" and "/\\\\/evil.example/x" ' +
      'do the same. Measured 2026-09-04 against Node\'s WHATWG URL parser, which is the ' +
      'same algorithm browsers use. The fix is one character in the existing character ' +
      'class. Exploitability depends on whether any caller passes a path it did not ' +
      'construct itself -- but a guard is not sound because its callers happen to be ' +
      'safe today, and this one is asserted, not assumed.',
  ),

// A24 is RETIRED, 2026-09-10, by the design arc's 06.1a.
//
// It read: "error detail does not survive the service layer". Every service threw
// `new Error(\`HTTP ${status}: ${statusText}\`)`, discarding `validationErrors` -- the one part
// of the Spring envelope that is stable and field-addressable -- so a form could not show
// "identityNumber: must be 13 digits" even though the server had sent exactly that. The status
// went with it, so a caller could not tell a 400 it caused from a 500 it did not. And
// `statusText` is the EMPTY STRING over HTTP/2, which every deployment of this application uses:
// the whole of a failed capture's diagnosis was `HTTP 500:` with nothing after the colon.
//
// A QUIETER HALF, worth recording because it is the part a reader would not predict: three
// modules (recruiter, recruitmentChannel, subscriptions) did `Promise.reject(msg)` with the raw
// response TEXT, so a caller's ordinary `catch (e) { e.message }` was `undefined` and even the
// free-text message was lost by anyone handling the failure the normal way.
//
// Every service is on `requestJson` now and rejects with an `Error` carrying `message`, `status`,
// `validationErrors`, `kind` and `retryable` (conventions.md §16). The helper itself came verbatim
// from the Capture Member arc's `phase/capture-member` f020af9 -- deliberately, because two error
// paths in one service layer is worse than either -- and this arc added `kind`, `retryable` and
// the ceiling.
//
// Its two pins were deleted and the standard-facing test un-skipped, plus three new live
// assertions: the closed `kind` vocabulary over the whole discovered population, and that a 5xx is
// retryable while a 4xx is not (so no retry button lies).

  A1: define(
    'A1',
    'the province -> district -> area cascade is dead',
    'locationService.getDistricts requests GET /api/districts?provinceId=... and no ' +
      'controller maps that path -- confirmed against the generated api-surface.txt, ' +
      'which is read out of the live routing table rather than out of anyone remembering. ' +
      'The cascade therefore cannot advance past its first step, and it fails silently: ' +
      'the request errors and the district list simply stays empty with nothing shown ' +
      'to the user. The register predicted this and named P4 as the phase that would ' +
      'confirm it; the artifact is that confirmation.',
  ),

  A2: define(
    'A2',
    'the browser calls /api/debounce, an endpoint that has never existed',
    'debounceService requests GET /api/debounce?fn=...&delay=... and no controller ' +
      'maps it. Debouncing is a browser concern and there is no plausible server ' +
      'implementation, so this reads as a misunderstanding rather than a missing ' +
      'feature -- the repair is almost certainly to delete the service. Recorded ' +
      'rather than deleted because this arc asserts defects and does not fix them.',
  ),

}

// A37 and A38 are RETIRED, 2026-09-17, by the capture-member re-port: memberService.normalizeMember
// unfolds the server's delimited list fields for every consumer, and the Structures tab no longer
// renders either list (stakeholder shape, 2026-09-17). Proved in Members.structures.test.jsx (a
// string-shaped record opens, reads and edits) and memberService.normalize.test.js.

// B2 is RETIRED from this file, 2026-09-22 (local only): the incumbent developer's Google key left the tracked
// .env with his Vercel and Render, its pin was deleted and its test runs live in committedSecrets.test.js, with the
// allowlist empty. The key is still in history and live until its owner rotates it -- the register's half, which no
// test of the tree can hold.

// A29 is RETIRED, 2026-10-06, by the October build's bugs stream: the branch dialog converts the API's record to its
// form's strings on the way in and the form to BankBranchDto's keys on the way out (pages/bank_branch/branchForm.js),
// so Edit opens a form whose address is a line of text and whose Branch is the record's branchName. Its pin was
// deleted and its standard-facing test un-skipped in the same commit (BankBranchModal.a29.test.jsx, which now drives
// Edit end to end on the API's own shape).

// A47 is RETIRED, 2026-09-17, by the design arc's K1 (a95e7b9, 2026-09-14), met at the merge of
// main into design/06-close.
//
// It read: "the failed member register still counts zero around its own error". On that
// branch the figure strip reads "not available" and the pager "Could not load members" when
// GET /api/members fails -- probed before the flip, not assumed from the red pin. The pin was
// deleted and its standard-facing test un-skipped in the same commit (BR-5); the pin had been
// written two days after K1, against main, which did not yet have it.
//
// Removed rather than marked closed, for the reason A11 records: this file's population
// IS the set of open rows.

export const DEFECT_ROWS = Object.keys(DEFECTS)

/**
 * The title of the SKIPPED, standard-facing test for a row.
 * Used as `it.skip(disabled(DEFECTS.A13), ...)`.
 */
export const disabled = (d, what) =>
  `KNOWN DEFECT ${d.row} -- ${what ?? d.headline}. Written to the correct behaviour and ` +
  `left visibly failing by design. ${d.detail}`

/**
 * The title of the LIVE pin for a row -- the test that runs and asserts the
 * defect still reproduces.
 * Used as `it(pin(DEFECTS.A13), ...)`.
 */
export const pin = (d, what) =>
  `STILL REPRODUCES ${d.row} -- ${what ?? d.headline}. This test asserts the CURRENT, ` +
  `WRONG behaviour on purpose. If it is red, the defect has been FIXED: delete this pin ` +
  `and un-skip the ${d.row} test beside it, then close the register row.`
