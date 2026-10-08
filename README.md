# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## Building a new screen

Read these first. None of them assumes you know how the application got here.

- **[`SCREEN-CONTRACT.md`](SCREEN-CONTRACT.md)** — the rules every new screen must satisfy, each with the
  defect that put it there and the test that fails if it is broken.
- **[`DESIGN.md`](DESIGN.md)** — the design system: tokens, type, and the named rules the build follows.
- **[`COMPONENTS.md`](COMPONENTS.md)** — every shared component, every state it can be in, and when to use it.

`npm run lint` fails on any warning. `npm test` is the whole suite.

## Running the tests

```bash
npm ci
npm test
```

That is the whole thing. `npm test` runs the entire suite — services, pure logic, hooks,
context, screens, the route smoke and the accessibility checks — and needs no database, no
running backend, and no credentials of any kind.

**It does not need a `.env` or `.env.local` file.** Those are git-ignored, so a fresh clone
has none, and a suite that needed one would be a suite nobody else could run. If you have
them, they are ignored by the tests rather than read.

**Node is pinned** to the version in `.nvmrc` (`24.14.0`), also declared as `engines.node`
in `package.json`. Use `npm ci` rather than `npm install`: it installs exactly the committed
lockfile, so two machines on two days get the same tree.

### What it produces

| Path | What it is |
| --- | --- |
| `reports/junit.xml` | JUnit XML, written alongside the console output on every run |
| `coverage/` | HTML and JSON coverage, written by `npm run test:coverage` |

Both are build output and are git-ignored.

### The exit code is the whole result

`0` means every test passed. Anything else means it did not. There is no other meaning, and
nothing else needs parsing to find out — a suite that prints a green summary and exits
non-zero has still failed.

### Dependency vulnerabilities — `npm run audit`

```bash
npm run audit          # npm audit --audit-level=high
```

🔴 **DELIBERATELY NOT PART OF `npm test`, and the reason is not convenience.** Every other guard here
is a function of the code: the same tree gives the same answer today and next month. This one is a
function of the code **and of what the world has published since**, so wiring it into the gate makes
a green tree turn red overnight with nothing changed — which is a flake, and this suite does not
have flaky tests. Vulnerability scanning belongs on a schedule and before a release, where a new
advisory is news rather than an interruption.

⚠️ **Measured 2026-09-08, so the deferral is informed rather than blind: 13 vulnerabilities — 2 low,
2 moderate, 9 high.** One is a direct dependency (`vite`); the rest are transitive. Most are build
tooling that never reaches a browser, but **`react-router` is not** — it ships. None is critical, and
none was known before this was run, because nothing had ever run it.

> *(For comparison, the automation suite reports **zero**. It pins two devDependencies exactly and
> that is the whole tree — an argument for small dependency sets that is easier to make with a
> number.)*

### Useful subsets

These are conveniences for a tight edit loop. **None of them is the suite** — only `npm test`
is, and it is the one to run before pushing.

```bash
npm run test:watch                 # re-run on change
npm run test:fast                  # services, utils, hooks, context — no jsdom screens
npm run test:ui                    # the screens and the route smoke
npm run test:coverage              # adds the coverage report and enforces its floors
npm run lint                       # ESLint, at its committed warning baseline
```

### Some tests are skipped on purpose, and none of them is flaky

Skipped tests here are **known defects, asserted rather than fixed**: each is written to the
*correct* behaviour, left disabled, and named so the reason travels with it —

```
KNOWN DEFECT A6 -- choosing Payroll must switch on the payroll branch...
```

Beside most of them sits a second test pinning the *current, wrong* behaviour, so that fixing
the defect turns one red and tells you to close the other out. **Nothing here is skipped for
being unreliable**, no test is retried, and a flaky test is a bug to fix rather than a result
to re-roll.

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Local Development with .env files

To simplify local configuration, `.env` files have been added to both projects. These files are ignored by git to keep your local credentials safe.

### Frontend (`sasbo-online`)
1. A `.env` file has been created in the root directory.
2. It contains `VITE_API_BASE_URL=http://localhost:8080`.
3. Vite automatically loads this variable. You can use it in your code via `import.meta.env.VITE_API_BASE_URL`.

### Backend (`sasbo-online-backend`)
1. A `.env` file has been created in the backend root directory.
2. It contains database credentials and the server port.
3. **How to load it:**
   - **IntelliJ/WebStorm:** Install the **"EnvFile"** plugin. In your Run Configuration, go to the "EnvFile" tab and add the `.env` file.
   - **Terminal:** Run `export $(cat .env | xargs)` before running `./gradlew bootRun`.
   - **Manual:** Copy the values into your IDE's "Environment Variables" section in the Run Configuration.

## Deployment

None, for now. Since 2026-09-22 the project runs locally: it left the incumbent developer's Vercel and Render,
so this repository carries no `vercel.json` or `render.yaml`. `npm run dev` serves the client on
`http://localhost:5173`, the one origin the backend's CORS policy allows, and the tracked `.env` points
`VITE_API_BASE_URL` at the backend on `http://localhost:8080` (`./gradlew bootRun` in the server repository).

Address suggestions need a Google Maps browser key of your own, in `.env.local` (git-ignored):

```
VITE_GOOGLE_API_KEY=<your key>
```

Without one, the address fields offer no suggestions and still take a typed address. Restrict the key to your
referrers and to the Places API in the Google Cloud console: it ships in the browser bundle by design.
