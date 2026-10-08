# SASBO automation testing

Browser and API journey tests for SASBO Online, written in Gherkin and run with Playwright.

**On the name.** This repository is not scoped to end-to-end browser journeys and never was. It
already owns API journeys, and it will own the cross-repo orchestration and the merged report as
well. `automation-testing` names that whole role; `e2e` named one technique inside it. It
deliberately does not take the `sasbo-online-` prefix its two siblings use — those are the two halves
of one product, and this repository asserts **both** and belongs to neither, so it sits at the
`sasbo-` level rather than beneath either half. Read that as chosen, not as an inconsistency to fix.

**This repository owns every `.feature` file in the system.** The application repositories keep their
own fast test layers — component tests on the client, JUnit on the server — and neither carries a
Cucumber dependency.

> ⚠️ **The harness is built; no journey is written yet.** Everything reusable exists — runner, BDD
> layer, page-object and API-client bases, the container stack, reporting, the guards. The first
> journey is next, and **it should need nothing from this list.** If writing a journey means adding
> harness code, that is a defect in the harness rather than a cost of the journey.

---

## Why this is a separate repository

The client and the server are two independent repositories on separate release cadences. These tests
assert the behaviour of **both**, so they belong to neither — putting them inside one would make that
repository the owner of tests about the other, and a server-only change would need a client commit to
verify.

## Requirements

- **Node** — the version in [`.nvmrc`](.nvmrc). Install with `nvm use`.
- **Docker** — the suite runs against a containerised stack (database, API, and the built client).

## Running it

```bash
npm ci
npm test
```

`npm test` runs five projects: **`harness`**, which tests this suite's own machinery and needs
neither a browser nor a running application; **`journeys`**, the Gherkin scenarios that share the
stack and run at once; **`journeys-acb`**, the debit order runs (Debit Order Deductions, the module
once called A.C.B.), one at a time once those have finished, because each prepares a real run for a
real month; **`journeys-monthly`**, month end's, one at a time after the debit order runs, because
they read a figure, change one thing and read it again, and a member another
journey changed in between would move it; and **`journeys-serial`**, the scenarios that take the API
container down and put it back — given a project of their own, with every other project named as its
dependencies, because that is the only construct that makes them genuinely exclusive of everything
else. Each script is `bddgen && playwright test …` — the generate step is never optional, because `playwright-bdd` compiles `.feature` files into specs and a
stale `.features-gen/` is a suite testing yesterday.

**No credentials, and no `.env` or `.env.local` file.** Those are git-ignored, so a fresh clone has
none; a suite that needed one would be a suite nobody else could run.

**The exit code is the whole result.** `0` means every test passed, anything else means it did not,
and there is no third meaning. Nothing needs parsing to find that out — a run that prints a green
summary and exits non-zero has still failed.

### The stack

```bash
npm run stack:up     # postgres + the API image + the built client
npm test
npm run stack:down
```

The compose file consumes application images **by tag**, never by building from a sibling directory —
see [`docker-compose.yml`](docker-compose.yml) for the two commands that produce them locally. The
API under test is the **container that actually deploys**, not a development process: that build
carries Spring Boot devtools, which changes classloading and caching, so the two are different
programs and testing the one that never ships is false confidence.

> 🔑 **The containerised run is the authority.** A native run is a debugging convenience and its
> result is not evidence. If the two disagree, the container is right — and the disagreement is
> itself a finding, because it means the suite depends on something about one machine.

#### Two stacks at once

Compose keys every container, network and volume off the **project name**, so a fixed one is not a
label — it is a shared address. Two sessions running `docker compose up` from two worktrees were
never starting two stacks; they were both addressing one, and `docker compose down -v` in either
destroyed the other's database mid-run. That arrives as a scenario failing on a connection reset,
which reads as flake in the suite rather than as a second session existing.

Give the second stack an id, and blank the three host ports so Docker assigns free ones:

```bash
export SASBO_STACK_ID=p7
export SASBO_DB_HOST_PORT= SASBO_API_HOST_PORT= SASBO_CLIENT_HOST_PORT=
npm run stack:up
docker compose port client 80    # the port Docker chose, for a browser
```

> 🔑 **Empty is not the same as unset here.** The compose file substitutes `${VAR-default}`, not
> `${VAR:-default}`. Unset gives the fixed default a human wants; **empty** publishes on an ephemeral
> port, which is what a second stack wants. A fixed host port is a global singleton, and two of them
> collide at `up` however well isolated everything else is.

Unset `SASBO_STACK_ID` and the project is `sasbo-automation-testing-default` on the usual ports.
Nothing about a single-session run changes.

#### Building the two application images

The compose file consumes them by tag, so they are built once from wherever those repositories live:

```bash
# the client — build its production bundle first, with an EMPTY API base so the
# bundle calls same-origin and nginx proxies /api to the server
(cd <the client repo> && VITE_API_BASE_URL= npm run build)
docker build -f docker/client.Dockerfile -t sasbo-online-client:local <the client repo>/dist

# the server — its own Dockerfile, unmodified
docker build -t sasbo-online-backend:local <the server repo>
```

#### If a build or install fails with a certificate error

Your outbound HTTPS is being intercepted and re-signed — a corporate proxy or an endpoint-security
product. **A JVM and Node each read their own trust store, not the operating system's**, so a
container trusts the public roots and nothing else. Whatever fixes this on your host buys nothing
inside a container. Three different symptoms, one cause:

| Where | What you see |
| --- | --- |
| `npm ci` in the suite container | `UNABLE_TO_VERIFY_LEAF_SIGNATURE` — reads as an npm registry outage |
| Gradle in the server image build | `PKIX path building failed` — reads as the Gradle repositories being down |
| Gradle on your host | *"Trust store file … does not exist"*, then every resolution fails |

For **the suite**, point `SASBO_CA_BUNDLE` at the proxy's root certificate:

```bash
SASBO_CA_BUNDLE=/path/to/proxy-root.crt npm run stack:up
```

##### Where that certificate actually comes from

🔴 **This paragraph is the whole point of the section.** The instruction above used to stop at
*"point it at the proxy's root certificate"* — which assumes you have a file you have almost
certainly never seen. The product installs its root into the operating system's trust store and
never puts it on disk where you can reach it, so the escape hatch existed and could not be opened.
Extract it yourself, in PEM, which is the only format Node reads here:

**Windows** — the interceptor's root is in the machine store. Find it by subject, then export it:

```powershell
Get-ChildItem Cert:\LocalMachine\Root |
  Where-Object { $_.Subject -match 'Norton|Symantec|Zscaler|Netskope|Fortinet|Palo Alto|BlueCoat' } |
  Select-Object Subject, Thumbprint
```

```powershell
$c = Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Thumbprint -eq '<the thumbprint>' }
$b64 = [Convert]::ToBase64String($c.RawData, 'InsertLineBreaks')
"-----BEGIN CERTIFICATE-----`n$b64`n-----END CERTIFICATE-----" |
  Out-File -FilePath $HOME\proxy-root.pem -Encoding ascii
```

**macOS** — `security find-certificate -a -p -c '<name>' /Library/Keychains/System.keychain > ~/proxy-root.pem`

**Any platform, if you cannot find it by name** — ask the connection what it is presenting, which
is authoritative where a name search is guesswork:

```bash
openssl s_client -showcerts -connect registry.npmjs.org:443 </dev/null 2>/dev/null |
  openssl x509 -outform PEM > proxy-root.pem
```

⚠️ **Keep the file OUTSIDE this repository.** It is a certificate, not a secret, but the
committed-secret guard scans tracked files and a `.pem` in the tree is exactly the shape it is
looking for. A home directory or a scratch path is the right home for it.

For **the server image**, the fix cannot go in that repository's Dockerfile — it is not ours to
change — so the base image is substituted instead, leaving their build to run verbatim:

```bash
docker build -f docker/gradle-with-ca.Dockerfile -t sasbo-gradle-ca:local <dir holding proxy-root.crt>
docker build --build-context gradle:9.5-jdk21=docker-image://sasbo-gradle-ca:local \
  -t sasbo-online-backend:local <the server repo>
```

Unset and unsubstituted — in CI and on any machine without interception — none of this applies.
Nothing else changes.

### Watching it run — four ways, one suite

| Command | What it is for |
| --- | --- |
| `npm run test:headed` | See the browser |
| `npm run test:live` | Headed, one worker — the demo option |
| `npm run test:ui` | Playwright's time-travel UI. **The right one for writing tests** |
| `npm run test:debug` | Step through with the inspector |
| `npm run test:trace <zip>` | Replay a finished run, frame by frame, after the fact |

**All four assume a running stack.** The one command that does not is
[`npm run demo`](tools/demo.mjs): Docker up, both application images built the way the gate builds
them, the compose stack up on its host ports, Playwright against it, the stack down when the browser
closes. The file's header is the sequence.

```bash
npm run demo                       # the Playwright UI, against a freshly built stack
npm run demo -- --headed           # every scenario, browser visible
npm run demo -- --live             # one worker, journeys only — the demo option
npm run demo -- --headless         # the gate's run, natively
npm run demo -- --keep             # leave the stack up afterwards; it prints the teardown
```

It needs the server and client checkouts findable *(`SASBO_WORKSPACE`, or `--client`/`--server`)*
and `node_modules` in the client checkout. ⚠️ **Unset, the checkouts resolve to `server/` and
`client/` at the workspace root** — in a worktree layout those are the parked primaries, which can be
weeks stale or deliberately broken, and the images would be built from them. Point the flags at the
worktrees, relative to the workspace root:

```bash
npm run demo -- --server .worktrees/server/<branch> --client .worktrees/client/<branch>
```

**Showing it to someone?** The wait is the server's Spring Boot start, about a minute on a quiet
host and several on a starved one. Pre-warm: run it with `--keep` ten minutes before, close the
Playwright window, and run it again when the audience is there — against a healthy stack the `up`
is instant and the window is up in seconds. Free the host first *(other sessions, other stacks)*:
below about 1.5 GB free the server can take longer than compose waits, and the demo then waits once
more before refusing. Tear the kept stack down afterwards; it prints the command.

It runs **one Playwright worker** unless `SASBO_SUITE_CPUS` says otherwise: natively, every worker is a
Chromium sharing the host with the Docker VM serving it, and the default count reddened the bank
listing on this host while one worker matched the gate's figures *(measured, in the file)*. A native
run is not the verdict — F9.

**None of these is a separate suite.** All read the same feature files through the same
[`playwright.config.js`](playwright.config.js) — a demo suite would drift from the real one inside a
month and then demonstrate something no longer true. Headless is always the authoritative mode; the
rest are affordances. Note that headed **inside** the pinned Playwright image executes but cannot be
watched — that container has no display, so live viewing means a native run against the stack.

For showing the suite to someone, the **Cucumber HTML report and the trace viewer are usually the
better demonstration** than a live run: they survive the meeting, can be re-read, and do not depend
on a green run happening while somebody watches.

### Reports

Three, from one run, because three audiences read the same execution and separate runs can disagree:

| Path | Audience |
| --- | --- |
| `reports/cucumber/report.html` | People |
| `reports/cucumber/report.json` | Any tool reading scenario results. **It is the only artifact carrying each scenario's tags** — `@known-broken`, `@defect:A30` — because `playwright-bdd` holds tags in its own data rather than as Playwright annotations, so they never reach the JUnit XML |
| `reports/junit.xml` | CI. Every test-reporting integration reads it; without it a red build just says *"job failed"* |

All are build artefacts and none is committed. Traces and screenshots especially: they capture
whatever was on screen, which includes member data.

### Pointing it at a different environment

The suite never hardcodes a path or a URL to the application repositories. Both targets come from the
environment, so the same suite runs against a local stack, a container stack, or a deployed
environment with no code change:

| Variable | Default |
| --- | --- |
| `SASBO_CLIENT_URL` | `http://localhost:5173` |
| `SASBO_API_URL` | `http://localhost:8080` |

This is enforced, not just documented: [`tests/harness/invariants.spec.js`](tests/harness/invariants.spec.js)
fails the build if any file in this repository contains a relative path reaching into a sibling
checkout.

## Layout

| Directory | Holds |
| --- | --- |
| [`features/`](features) | Every `.feature` file in the system |
| [`steps/`](steps) | Step definitions — one-liners that delegate, and nothing else |
| [`pages/`](pages) | All UI logic. [`BasePage`](pages/BasePage.js) is what page objects extend |
| [`api/`](api) | All REST logic. [`ApiClient`](api/ApiClient.js) is what API clients extend |
| [`support/`](support) | Target resolution, the destructive guards, per-scenario state, the fixtures every step imports, the non-vacuity canary |
| [`tests/harness/`](tests/harness) | Tests of this suite's own machinery |
| [`scripts/`](scripts) | The pre-run clean |
| [`tools/`](tools) | 🔑 **Not part of the suite.** See below |
| [`.github/workflows/`](.github/workflows) | [`gate.yml`](.github/workflows/gate.yml) — the merged gate on GitHub, dispatch only: the three lanes of `tools/test-all.mjs` on three named commits, one verdict. Written by the deploy arc; every recipe it runs stays in `tools/`. The harness's source invariants scan it like any other file |

### `tools/` — the three-repository orchestrator, and why it is a lodger

[`tools/test-all.mjs`](tools/test-all.mjs) runs all three repositories' gates and
[`tools/merge-reports.mjs`](tools/merge-reports.mjs) merges their JUnit XML into one document. They
are **not** part of this suite. Nothing under any other directory imports them, `npm test` never
touches them, and this repository runs standalone in a fresh clone without them — which is asserted,
not promised: see *the suite does not depend on the orchestrator* in
[`tests/harness/invariants.spec.js`](tests/harness/invariants.spec.js).

They live here because they need to live **somewhere**. Both sat at the workspace root, tracked by no
repository at all — no history, no diff to review a change against, no revert, and nothing to restore
from. That is a poor home for the thing that produces the three-repository verdict.

⚠️ **They are still scanned by every invariant this repository has**, including the one forbidding a
file from naming a sibling checkout. An orchestrator of three checkouts obviously needs to find them,
so it is **told** where they are — `SASBO_WORKSPACE`, or per-repository flags — which is the same
shape the suite itself uses for `SASBO_CLIENT_URL` and `SASBO_API_URL`. Unset, it discovers the
workspace by walking up for an ancestor that actually contains the three checkouts, and refuses to
guess if it cannot find one.

```bash
node tools/test-all.mjs                 # all three, then the merged report
node tools/test-all.mjs --only client   # one stage; the others report DID NOT RUN
node tools/test-all.mjs --only suite --determinism   # the suite lane as npm run test:gate: every test three times
```

## Conventions

These are load-bearing, not stylistic. They are what keeps a Gherkin suite readable after a year.

- **Step definitions are one-liners that delegate.** All real logic lives in page objects and API
  clients. This keeps the BDD layer replaceable and stops a selector change from touching every
  feature file.
- **Scenarios describe the journey, never the mechanism.** *"When the branch is saved"*, not *"When a
  POST is sent to `/api/banks/branches`"*. A scenario a non-engineer cannot read has paid Cucumber's
  cost and taken none of its benefit.
- **Every scenario seeds and cleans its own state.** Scenarios run in parallel; one that depends on
  another's leftovers passes alone and fails under load.
- **No fixed waits, ever.** Playwright's assertions retry on their own. A hard sleep is a flake with
  a delay.
- **Retries are zero.** A retry that turns a red run green is a mechanism for not knowing. A flaky
  test is fixed, never re-run.
- **No real member data in any fixture.** Synthetic ID and account numbers only — reports and traces
  are artefacts that get attached to builds and forwarded.
- **Prefer accessible selectors** — role, label, visible text. Reach for a test id only where no
  accessible name exists, and treat needing one as a finding about the UI.
- **A scenario claims only an identity the server checks.** Until the October build the
  application's sign-in navigated without validating anything, so `Given I am logged in` would have
  passed while asserting precisely nothing, and no scenario claimed one. Sign-in is real now: a
  scenario signs in as one of the demonstration users the stack seeds -- by POSITION ("the Chief
  Financial Officer"), never by a person -- through `POST /api/auth/login`, and the server decides
  what that position may do. Their password is generated for each stack and written nowhere
  ([`support/demoUsers.js`](support/demoUsers.js)); every other journey stays anonymous, as sign-in's
  open mode allows.
- **Two independent guards before anything destructive.** A positive rule (the name matches the exact
  shape this suite mints) *and* a negative one (it matches nothing on a never-touch list), both from
  [`support/guards.js`](support/guards.js) so the creator and the cleaner cannot drift apart. The
  database this runs against is a members register with no restore path.
- **Cleanup runs *before* the suite, not only after.** npm skips `posttest` when `test` fails, and a
  failed or interrupted run is exactly the one that leaves state behind. A teardown that only runs on
  success cleans up after the runs that did not need it.

### What is enforced rather than asked for

Four of the conventions above are checked by [`tests/harness/`](tests/harness) on every run, because
a rule that depends on someone remembering it is not a rule:

- no relative path reaching into a sibling checkout;
- no fixed wait, anywhere;
- no citation of a path under the plans;
- and a **non-vacuity canary** ([`support/assertNonVacuousScan.js`](support/assertNonVacuousScan.js))
  behind every scan, because a guard that works by discovery **fails open** — a broken glob matches
  nothing, finds no offenders, and reports a green run of zero scenarios.

The floors that canary holds are **not coverage targets** and must never be raised to match reality.
A floor equal to the actual count is fragile without being stricter.

## Branching

Work happens on a branch, in its own worktree — never directly on `main`. Worktrees keep concurrent
sessions from colliding over one working directory and index.

```bash
git worktree add ../.worktrees/sasbo-automation-testing/<slug> -b <branch>
```

Branches are named `phase/<nn>-<slug>`, matching the delivery sequence in the plans.
