# The contract every new screen inherits

**Who this is for:** whoever builds the next screen in this application, without having read the
design arc that produced these rules. You should not have to. Everything below is a rule the
build already follows, every one of them is held by a test that will go red, and each carries the
defect that put it there — because a rule whose reason has been forgotten is a rule somebody
deletes.

**What this is not.** It is not the design system: the tokens, the type ramp and the named visual
rules are in [`DESIGN.md`](DESIGN.md), and the parts you build from are in
[`COMPONENTS.md`](COMPONENTS.md). This file is the *behavioural* contract — the things a screen
must do to be correct, not the things it must look like.

> 🔑 **The one sentence version.** A screen may not claim anything it has not checked: not that a
> list is empty, not that a save failed, not that a date is a date. Nearly every rule below is
> that sentence applied to a different part of the page.

---

## 0 · Before you write anything

Run the gate once, on a clean tree, so you know what green looks like before you change it:

```bash
npm ci
npm test          # the whole suite; no database, no backend, no credentials
npm run lint      # 0 errors and 0 warnings. Both. See §7
npm run build && npm run budget
```

⚠️ **If something is red before you have written a line, stop and read §8 first.** On this machine
a starved or contended box reports as a failing suite, and three separate arcs have drawn a wrong
conclusion from that in a single week.

---

## 1 · Colour, and why you may not type one

**Every colour comes from a semantic token. No raw Tailwind colour utility, no `dark:` twin.**

- `bg-surface`, `text-ink`, `border-line` — never `bg-white`, `text-gray-700`, `border-slate-200`.
- **Never write `dark:`** on a token utility. The tokens are redefined for the dark theme in
  `src/assets/styles/index.css`; a `dark:` variant beside a token is a second, competing source of
  truth that will drift from the first.
- Held by `src/test/paletteLiterals.test.js`, which fails the suite on either.

**`ink-faint` is for icons and dividers. It is never text.** It measures 3.65:1 on the dark theme,
below the AA floor. Held by `src/test/inkFaintIsNeverText.test.js`.

**Placeholders are text.** WCAG 1.4.3 applies to them, and they must be `ink-muted`, not `ink-faint`.
This is not pedantry: General Form shipped 120 placeholders that failed AA, on a screen every
other check had certified.

> **Why so absolute:** the baseline for this arc counted **838 `dark:` sites and 104 raw colour
> utilities**. Every one had looked reasonable in isolation. The token layer only works if nothing
> is allowed to route around it.

---

## 2 · A screen that fetches has three states, and "empty" is not one of them until it answers

Any screen that loads data must render **loading**, **empty** and **error** distinctly, and use
[`DataState`](src/components/common/DataState.jsx) rather than hand-rolling them.

**A request that has not answered yet is not an answer.** The members list used to render its empty
table synchronously on mount, so the first thing every user saw, every time, was *"no members"*,
which then flickered into data. Against a slow or broken backend they simply read "no members" and
believed it.

- The loading state must not print a row of zeros either — an empty figure strip is the same lie in
  numbers.
- The error state must offer a **retry**, and must never say "no members".
- Held by `Members.states.test.jsx` and `src/test/listStates.test.js`, which walks every page under
  `src/pages/` and fails any that fetches without handling failure.

---

## 3 · Writes: the honesty rules

These are the rules the independent senior review spent most of its findings on, and they are the
ones most easily got wrong by a competent developer in a hurry.

**① A dialog cannot be closed while its write is in flight.** Pass `busy` to `Modal` (or `loading`
to `ConfirmDialog`) and it refuses Escape, the scrim and the X for the duration. Without it a user
dismisses the dialog mid-save and is left with no idea whether it happened.

**② A failure is said inside the dialog it happened in.** Not behind the scrim, not in a toast that
outlives the thing it refers to, not in a console. Use `DialogNotice`
(`src/components/common/DialogNotice.jsx`).

**③ Never claim an outcome nobody checked.** This is the important one and it has one home:

```js
import { writeMayHaveLanded } from '../services/parseApiError.js'
```

If a write fails with anything **other** than a 4xx — a timeout, a dropped connection, a 5xx — the
server may well have applied it. Copy that says *"not saved"* is then a false statement that invites
the user to do it twice. Say what is true: that it could not be confirmed, and where to check.
**Eleven write-failure sites** in this application obey that rule; a twelfth must too.

**④ The request deadline covers the body, not just the headers.** `requestJson` enforces it. If you
write a new fetch path, it goes through the service layer — a response whose headers arrive and whose
body never does is a hang, and it was a live defect on a row the register had already called closed.

---

## 4 · Dates

**There is one date format and one date control.**

- Rendering: `formatDate` from `src/lib/format.js`. The server sends `[y, m, d]`, and React renders
  an array by concatenation — so a raw `LocalDate` prints as **`201941`** for 1 April 2019. That
  shipped, in two different dialogs.
- Input: [`DateField`](src/components/form/DateField.jsx), or `<Field type="date">`, which routes to
  it. **Native `<input type="date">` is banned** — the owner's ruling is that the date field is
  choose-only — and `npm run lint` fails on one (`no-restricted-syntax`, which reads the element, so a
  conditional or computed `type` is caught too).
- **A date-only value never passes through `Date` or `toISOString`.** `new Date("2019-04-01")` parses
  as midnight **UTC**, so `toISOString().split("T")[0]` is a day out for two hours of every South
  African day. `src/lib/format.js` explains this at length; read it before you reach for `Date`.
- An absent date is the one absence mark (`—`), never `Invalid Date` and never an empty box in a
  field that is showing a record.

---

## 5 · Structure, focus and the shell

- **Exactly one `<h1>` per route.** Held by `src/test/headingContract.test.jsx`, which mounts every
  path-bearing route and counts.
- **No native `alert()`, `confirm()` or `prompt()`.** They cannot be styled or themed, they block the
  thread, and passed an `Error` they print `[object Error]`. `npm run lint` fails on one
  (`no-restricted-globals` / `no-restricted-properties`). Use `ConfirmDialog` for a question, a notice for an
  outcome.
- **No `console.*` in `src/`.** It is a privacy gate, not a tidiness one: this application once
  logged whole member records — names, identity numbers, bank accounts — and `Auth.jsx` logged
  `{ email, password }` on every login attempt. `src/lib/reportError.js` is the one permitted call
  site. `no-console` is at `error`.
- **The error boundary is keyed by route**, so a page that throws does not trap the user on it.
  If you add a new shell, keep the `key={pathname}`.
- **Below 1024px the closed navigation drawer is `inert`**, so it is out of the tab order, and
  opening it moves focus into it.
- **Touch targets are at least 44px** on a coarse pointer. Measured, not assumed.

---

## 6 · Responsive

- **No static viewport units.** `100vh` is the viewport *without* the browser chrome that is
  actually on a phone screen, so anything capped in `vh` puts its bottom edge — usually the Save
  button — below the fold on exactly the devices where that matters. Use `dvh`. `h-screen` and
  `w-screen` are `100vh`/`100vw` wearing Tailwind names and are banned with them. Held by
  `src/test/viewportUnits.test.js`.
- **Responsive steps are expressed in `rem`**, because what they measure — text and content
  widths — is in rem. A `px` step breaks silently at a 125% root font size (WCAG 1.4.4).
- A component that can be squeezed responds to **its own container**, not to the window.

---

## 7 · The gate you must leave green

| Check | The bar |
| :-- | :-- |
| `npm test` | Every test passes. **A skipped test is a decision**, and it must name a register row. |
| `npm run lint` | **0 errors and 0 warnings.** `jsx-a11y` and the five `react-hooks` rules are at `error`. |
| `npm run build` | Exit 0. |
| `npm run budget` | Every row under its ceiling. **Reclaim before you spend** — the ceiling is raised jointly and with a measurement, never to fit a change. |

**On `react-hooks/set-state-in-effect`, which is now an error and which you will meet:** the rule is
not a style preference. An effect that writes state the render could have derived causes cascading
renders; an effect that writes the *form* from the form overwrites people mid-keystroke. Let the
thing that owns the state set it — a fetch owns its own loading/ready/error transitions — rather than
setting state on the way into an effect.

### Writing the tests

- **The test fails first**, for the real reason, and you have seen it fail. A guard that has never
  been red proves nothing: delete the code it guards and watch the lane go red before you believe it.
- **Every red fixture has a must-pass beside it.** The accepting half is what catches a guard that
  started failing for the wrong reason.
- **A scanning guard needs a canary** (`assertDetectorCanary`) proving it convicts what it must and
  acquits what it must, and a floor (`assertScanIsHonest`) proving it read its source at all. A scan
  that matches nothing reports no offenders and goes green — which is exactly what success looks like.
- **Aim a guard at the fact, not at the spelling.** A predicate written against one way of writing a
  thing fails blind and loud the day somebody writes it the other way.
- **Import modules at the top of the test file.** An `await import()` inside a test body is billed to
  that test's timeout, and a lazily-loaded route chunk will not arrive inside it.
- ⚠️ **Drive the controls.** The single worst defect this arc found — five report screens that could
  not be opened at all, because choosing a Form Type crashed the route — survived every automated
  pass because those screens render perfectly until somebody *chooses* something, and nothing had
  ever driven the control. **If a screen has a select, a filter or a picker, a test must use it.**

---

## 8 · Measuring on this machine

This is a 16 GB box shared by several concurrent sessions, and it will lie to you.

- **A test timeout under 1 GB free memory is starvation, not a defect.** Read the free memory before
  you read the failure.
- **Free RAM is a symptom; the process list is the fact.** A box at 1 GB free with three suites on it
  is not the same box as one at 1 GB free with none. Enumerate what is running before any
  timing-sensitive measurement, and record that list beside the number.
- **Announce a measurement before you take it**, and name what you are *leaving* running rather than
  claiming a quiet box.
- **A re-run is not evidence.** If something was red and is now green, you have two results and no
  explanation. Establish the cause first; then the re-run means something.
- **Fix a flake at its wall-clock dependence, never at its threshold.** Raising a timeout makes a
  flake rarer without making it absent, which is worse: one that fires monthly can never be
  diagnosed. Make the thing deterministic instead.

---

## Open requests — not rules yet

Things another part of the project has asked this contract for, recorded so they are not lost. None is built.

- **A *Terminated* member status** *(requested by the ACB arc; confirmed needed by SASBO's answers of 2026-09-18)*. A
  member whose debit order has failed three months running is terminated, and ACB's screens must show it. That needs a
  status token pair (text and background) and a `StatusChip` variant that meet AA **on the page and on the membership
  card's metal**, both themes, like the five statuses that exist. Note SASBO's own meaning of the neighbouring status:
  *Suspended* is "still pending and not terminated as yet". The status vocabulary itself belongs to the Capture Member
  arc; this contract owns only how it looks. Build it with the first screen that needs it, not before.

---

## 9 · When a rule here is wrong

Some of it will be, eventually. The rule is: **re-aim the predicate at the fact, and never contort
the code to satisfy a guard.** A guard that cries wolf gets switched off by whoever is unlucky enough
to hit it, and then it protects nothing. If a rule is genuinely wrong, change it here, change its
test, and write down what you learned — the way every rule above was written down.

*Derived from the design arc, P0–P10, 2026-09. The evidence for each rule is in
`planning/branches/design/` — `findings.md` for what went wrong, `measurementTraps.md` for how the
instruments lied, and `p9Audit.md` for the independent review that produced §3.*
