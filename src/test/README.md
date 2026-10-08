# The client test suite — what it is, and how to add to it

This is the file to read before adding a test here. It is the client
counterpart of `server/src/test/README.md`.

**Every number below expires. Re-measure; never re-quote.**

## Part 1 — The commands

| | Command | What it is for |
| :-- | :-- | :-- |
| **Everything** | `npm test` | **The test gate.** Runs the suite from outside via `scripts/suite-floor.mjs` -- asserting the total and the **exit code** -- then the runtime witness cross-check. Shuffled, `retry: 0`, JUnit XML to `reports/junit.xml`. ⚠️ The bundle budget is deliberately NOT in here; it needs a build, and this name must not grow one |
| **Inner loop** | `npm run test:fast` | Services, utils, hooks, context and the guards — what you run on save |
| **Screens** | `npm run test:ui` | Pages, the route smoke and the a11y gate. Slower by an order of magnitude |
| **Raw runner** | `npm run test:raw` | `vitest run` with no guards around it, for a tight edit loop. **It is not the gate** and it cannot see a short run |
| **Coverage** | `npm run test:coverage` | Thresholds enforced per directory; fails the run if unmet |
| **Bundle** | `npm run build && npm run budget` | Proves the test tooling stayed out of the shipped product |

`npm run build` and `npm run lint` are **not** deepened by any of this. A new
name is opt-in; an existing name is a change to everybody's workflow made
without asking.

## Part 2 — The shape

```
src/test/
  setup.js                 jest-dom, axe matchers, MSW, browser stubs, per-test reset
  renderWithProviders.jsx  MemoryRouter + ThemeProvider; extras are opt-in
  assertions.js            expectErrorState, expectFieldRejected, expectNoA11yViolations
  assertNonVacuousScan.js  the non-vacuity canary, and every floor it guards
  knownDefects.js          the defect register: every row, its reason, disabled() and pin()
  serviceCatalogue.js      every service module and method, DISCOVERED not listed
  user.js                  setupUser() — the one userEvent configuration (options pass through)
  deferred.js              a promise the TEST settles — how "slow" is written without a clock
  msw/server.js            unhandled request => ERROR, never a warning
  msw/handlers.js          deliberately empty; tests declare their own
  msw/pageFixtures.js      the mount-time calls a page test must answer
  msw/smokeLayer.js        the route smoke's empty-200 layer + endpoint witness
```

Tests are **co-located** beside the unit under test, and split **by concern**
rather than by component — `CaptureMember.roundtrip.test.jsx` names what it
covers, so a red file names the concern.

## Part 3 — The seven rules

1. **Assert behaviour, not implementation.** Query by role, label and visible
   text. A Tailwind refactor must not redden this suite; that is the whole
   reason this layer is cheaper than the browser.
2. **No real network call, ever.** MSW intercepts and an unhandled request is
   an **error**. A silent passthrough is a test that depends on a running
   backend and passes for exactly as long as someone's `bootRun` is up.
3. **The suite supplies its own configuration.** `vite.config.js` sets
   `test.env`; nothing reads `client/.env`. See Part 5.
4. **Every assertion must be capable of failing** — and that is *demonstrated*,
   not assumed. This phase caught one of its own pins asserting a fact that was
   true of the loading render, so it could never fail. It was found by running
   a reddening change, not by reading the test.
5. **A failure message names the consequence, not the mismatch.** Enforced by
   the shared helpers rather than by review.
6. **Nothing is skipped for unreliability.** A skip means a real defect, named
   through `disabled(DEFECTS.X)`, with a live `pin()` beside it.
   `defectPopulation.test.js` asserts the correspondence in both directions.
7. **Fixtures carry no real member data.** Synthetic identity and account
   numbers only — coverage reports and failure output get forwarded.

## Part 4 — What it costs, measured 2026-09-05

| | Tests | Wall clock |
| :-- | :-- | :-- |
| `npm test` | 355 — 334 passing, 21 skipped | **47–58s** |
| `npm run test:fast` | 297 | **16–25s** |
| `npm test --maxWorkers=1` | 355 | 60–74s |

**The ceiling is 120s and the floor is 260 tests**, both set from those figures
with headroom. The floor is a **non-vacuity canary**, not a coverage target: it
sits deliberately below the real count and is never raised to match a new
actual.

> ### The "under 10s" target was not met, and here is the honest number
>
> The plan asked for a suite fast enough to run on save, and said that if the
> honest figure exceeded 10s the fix was to **split the lane** — never to drop
> the box. So: the full suite is ~50s and the inner loop ~20s.
>
> **Where the time goes**, measured rather than guessed: building a jsdom per
> test file was **48% of total wall clock**. The pure files — the SA ID table,
> the source-scanning guards, and the service tests, which need `fetch` but
> never a document — get no jsdom, and `setup.js` guards its DOM-shaped parts so
> one setup serves both. That alone took the suite from **95s to 47s** and the
> inner loop from 45s to 25s.
>
> 🔑 **And the default was later inverted, which is worth another ~40%.** `node`
> is now the environment a file gets unless it asks for a DOM, rather than jsdom
> being the environment a file has to escape. A file that renders something
> declares `@vitest-environment jsdom` and says what it renders. Measured
> 2026-09-09, two runs each way on one host: **~36s with the node default
> against ~60s with the jsdom default**, identical results. See `vite.config.js`
> for the reasoning — the speed was the smaller half of the argument.
>
> What remains is real work: 27 route mounts and ~120 MSW-intercepted calls.
> `npm run test:fast` is the on-save loop; the full gate is a pre-commit one.

## Part 5 — Two traps this suite already fell into

**`client/.env` is committed**, and until 2026-09-22 it pointed
`VITE_API_BASE_URL` at the deployed backend (it names `http://localhost:8080`
now). Tests inherited it silently: `api.test.js` first asserted against it
by accident, and — worse — the files running **without a DOM** *depended* on
it, because Node's `fetch` refuses a relative URL. Moving `.env` aside failed
**72 tests**. A fresh clone has the file, since it is tracked, so this would
never have surfaced as a CI failure; it would have surfaced the day someone
untracked it. `test.env` in `vite.config.js` fixes it: the suite now passes
with **`.env` and `.env.local` both absent**.

**`vitest-axe@0.1.0` ships an empty `dist/extend-expect.js`.** The documented
`import 'vitest-axe/extend-expect'` registers nothing, silently. The matchers
are registered by hand from `vitest-axe/matchers`, and `harness.test.jsx`
asserts they are present — which is the only reason it was noticed.

## Part 6 — Adding a domain

1. Write the test beside the unit, named for the **concern**.
2. Need responses? `server.use(...)` in the test. Need a page to mount?
   `...pageFixtures` first, then your own handlers, which take precedence.
3. Found a defect? Add a row to `knownDefects.js` with its evidence, land the
   standard-facing test `it.skip(disabled(DEFECTS.X, ...))`, and a live
   `it(pin(DEFECTS.X, ...))` beside it. Both, or `defectPopulation.test.js`
   reddens — and it will tell you which half is missing.
4. Run one reddening change against your new test and watch it fail.

---

## Part 7 — The API contract guards, and how to refresh them

Three files in this directory are **copies of artifacts the server generates**:

| File | What it carries |
| :-- | :-- |
| `api-surface.txt` | every route the server actually serves, `METHOD /path`, sorted |
| `api-vocabulary.json` | the payment-type vocabulary *with its `requiresBankAccount` rule*, and the error envelope's key set |
| `debit-window.json` | the A.C.B. run-window rule as data: the day each debit day 20–31 becomes in every month of 2026–2027, in two holiday sets — `src/utils/debitDate.js` must reproduce every cell |

They are what lets a static, sub-30ms test prove this browser cannot call an
endpoint the server does not have — with **no browser, no database and no
running server**.

### Refreshing them

In the **server** repository:

```
./gradlew generateApiSurface     # regenerates all three from the live code
git add api-surface.txt api-vocabulary.json debit-window.json
```

Commit there **first** — the guard here is useless without the artifact it
reads. Then copy all three into this repository:

```
cp <server>/api-surface.txt      src/test/api-surface.txt
cp <server>/api-vocabulary.json  src/test/api-vocabulary.json
cp <server>/debit-window.json    src/test/debit-window.json
npm test
```

> 🔑 **Nothing here reads across the repository boundary.** `client/` and
> `server/` are independent checkouts with independent histories; a test that
> reached into a sibling directory would pass on the machine where both happen
> to be cloned side by side and fail everywhere else. The artifact crosses as a
> **committed file** or it does not cross.

### The three guards

| Guard | What it catches |
| :-- | :-- |
| `apiContract.test.js` | a path this browser calls that the server does not serve. Two are known and named on `KNOWN_MISSING` — `/api/districts` (**A1**) and `/api/debounce` (**A2**) — and that list is asserted **exactly**, so an endpoint that starts working reddens too |
| `vocabulary.parity.test.js` | a string constant that has drifted, and an error envelope the browser parses by the wrong keys. The `*.parity.test.js` name is its own category: a drift guard should read as one |
| `committedSecrets.test.js` | key-shaped material in a **tracked** file. The allowlist is **empty**: its one entry, `.env` for row **B2**, went on 2026-09-22 when the key left the tracked file (it stays in history until its owner rotates it) |

### `scripts/contract-witness.mjs` — the runtime counterpart

The endpoint guard is a static scan, and **a discovery guard with no runtime
counterpart is grading its own homework**: if its regex quietly stops matching a
module, it reports fewer endpoints and still passes. P3's route smoke mounts all
27 routes and records every request actually made to
`reports/observed-endpoints.json`; this script compares the two.

```
npm test              # runs the suite, then this cross-check, in order
```

An endpoint **observed but not discovered** fails it — the scan missed something
real. An endpoint **discovered but not observed** is only printed: most need a
user action the smoke never performs.

> ⚠️ **It is a script and not a test, for an order reason.** The witness file is
> written by another test file *during* the run, and this suite runs in hostile
> order — so a test that read it would pass or fail depending on the shuffle.
> That is a flake by construction. Reading it after the suite has finished is
> deterministic.

### Three things about these guards that are easy to get wrong

| | |
| :-- | :-- |
| **The same literal can be a defect in one file and correct in another** | `'Payroll Deduction'` in `usePaymentTypeValidation` is dead code — the form's value comes from `PAYMENT_TYPE_OPTIONS` and can never be that string. The identical comparison in `Members.jsx` is **live and correct**: `MemberDto.paymentType` is a String passed straight through from storage, so a legacy row really can carry it. This is why `EXPECTED` is keyed by **site**, not by string |
| **`status` is two different vocabularies** | `sub.status === 'ACTIVE'` is right: subscription status is the server's `SubscriptionsStatus` enum, serialised as enum names. `STATUS_OPTIONS` is the *member* vocabulary and is a different list that happens to share a field name. Both are recorded as `ACCEPTED` in `EXPECTED` with that reason |
| **Path exclusions come before the secret scan, never after** | `package-lock.json` holds **409** `integrity` hashes *(counted 2026-09-05 — the plan recorded 276 on 2026-09-04; a count expires)*, each indistinguishable from a key. Scan it and the first run reports hundreds — which does not get triaged, it gets silenced. Scope the **input**; never raise the threshold |

### Every row goes in `knownDefects.js`, and its number comes from the register

These guards needed rows for the two missing endpoints and the committed key.
Their first draft recorded them *inside the guards*, in `KNOWN_MISSING` and
`ALLOWLIST`, which quietly created a **second register** that
`defectPopulation.test.js` could not see: no skipped test, no live pin, no
correspondence check. That is the exact failure the register exists to prevent,
built by the phase that was supposed to be preventing it. They are now rows here
like every other, and the guards' tables hold **the row object itself** rather
than a copy of its prose.

🔴 **And the numbers were wrong.** Not finding them in *this* file, P4 concluded
they were new and invented **A21** and **A22**. Both already existed twice over:
**A1** and **A2** had predicted exactly these two endpoints and even named P4 as
the phase that would confirm them, while **A21/A22 are P2's rows for entirely
different defects** — a null `jobGradeId`, and controllers reaching past the
service layer. So the work confirmed a standing prediction while citing two
unrelated defects, and a reader looking up A21 would have found the wrong thing
and concluded the note was wrong. (**B2**, the committed key, was right only
because the plan happened to name the number.)

> **The identifiers are a shared namespace with no allocator, and this file
> holds only a subset of it.** Absence here means the browser has no test for a
> row yet — the common case — and is *not* evidence the defect is unknown.
> Search the register before allocating anything.

The same review found that a copied row had **already drifted**: **A7** said the
defect was in `CaptureMember` alone, and the scan had found it in `Members.jsx`
too. The row is corrected; if a table had gone on paraphrasing it, nothing would
ever have said so.
