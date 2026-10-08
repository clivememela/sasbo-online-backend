/**
 * THE PAGES `App.jsx` SPLITS OFF, LOADED AHEAD OF ANY TEST THAT WAITS FOR ONE TO APPEAR.
 *
 * 🔴 A COLD IMPORT IS NOT A CONDITION, AND IT WAS INSIDE ONE. The route smoke (`App.routes.test.jsx`) and the heading
 * contract (`headingContract.test.jsx`) wait up to 5 s for a route's Suspense fallback to leave -- and for a split route
 * that window also held the page module's FIRST import: transformed, evaluated, its whole import graph with it. That is
 * the machine's speed, not the page's behaviour. On the owner's laptop, 2026-09-30, the ACB debits page alone outran
 * the 5 s every time, and a page that renders perfectly well failed as "never got past its Suspense fallback", while
 * the same commit passed on CI. A timeout decided by the box is flake; a longer timeout would only move the line.
 *
 * So the import is paid HERE, at module scope, where no test or hook timeout runs, and the waits time only what they
 * claim to: React swapping the fallback for a page that has already arrived. `lazy()` then resolves from the module
 * cache -- the same module, the same instance -- so nothing the tests observe changes.
 *
 * 🔑 `allSettled`, NOT `all`. A page whose import THROWS must still fail at its own route, by name, which is the class
 * the route smoke exists for: warming it here must not turn one broken page into one unattributed failure of the file.
 */
import appSource from '../App.jsx?raw'

/** Every module `App.jsx` imports lazily, as written there -- `./pages/acb/AcbDebits.jsx`. */
export const SPLIT_PAGES = [...appSource.matchAll(/import\(\s*'(\.\/[^']+)'\s*\)/g)].map((match) => match[1])

/**
 * How many `lazy(` DECLARATIONS `App.jsx` makes -- an assignment, so the two comments that mention `lazy(` are not
 * counted. The completeness canary: when it and `SPLIT_PAGES` disagree, a page is declared in a spelling the scan above
 * cannot read, and its first import is back inside a timed wait.
 */
export const LAZY_DECLARATIONS = [...appSource.matchAll(/=\s*lazy\(/g)].length

/** Load every split page once; resolves when all have settled, whether they loaded or threw. */
export const warmSplitPages = () =>
  Promise.allSettled(SPLIT_PAGES.map((specifier) => import(/* @vite-ignore */ `../${specifier.slice(2)}`)))
