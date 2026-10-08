# `steps/`

**Step definitions are one-liners that delegate.** That is the whole convention, and it is
load-bearing rather than stylistic.

```js
import { Given, When, Then } from '../support/fixtures.js'

When('the branch is saved', async ({ banksPage }) => banksPage.saveBranch())
```

All real logic lives in [`../pages/`](../pages) and [`../api/`](../api). Two things follow, and both
are the reason:

- **A selector change touches one file**, not every `.feature` that mentions that screen.
- **The BDD layer stays replaceable.** Page objects and API clients are plain Playwright with no
  dependency on this layer at all. If `playwright-bdd` ever stops being the right choice, these files
  and the feature files are rewritten; everything they delegate to survives untouched.

**Import `Given`/`When`/`Then` from [`../support/fixtures.js`](../support/fixtures.js), never from
`playwright-bdd` directly.** Importing from the library gets bindings without this suite's fixtures,
so `api` and `world` arrive undefined — and it surfaces as a null-reference inside a step rather than
as anything that mentions fixtures.
