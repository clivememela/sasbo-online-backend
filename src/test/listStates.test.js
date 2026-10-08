import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

import { REPO_ROOT } from './apiContractScan.js'
import {
  assertBlankingPreservedCode,
  assertNonVacuousScan,
  assertNothingWasSkipped,
  FLOORS,
} from './assertNonVacuousScan.js'
import { blankingDestroyedCode, readSources, unreadableDirectories } from './sourceScan.js'

/**
 * 🔴 A27 WAS ONE INSTANCE OF A CLASS, AND THE PLAN SAID SO BEFORE ANYONE LOOKED.
 * `IMPLEMENTATION.md`: *"A27 is probably one instance of a class — MobiZone found the same
 * defect on 15 surfaces."* `04.2` closed it on the member register. `04.3` then found the
 * same shape on two more screens, and both were WORSE than A27 had been:
 *
 *     bankService.getBanks().then(setBanks)
 *     memberService.getMembers().then(setMembersList)
 *
 * No `.catch` at all. A failed request became an unhandled rejection and the screen
 * rendered *"No banks match your search"* — a confident wrong answer with not even a
 * console line behind it. A27 at least logged.
 *
 * 🔑 SO THE FIX IS A RULE, NOT THREE REPAIRS. This asserts the property that made all
 * three defects possible: **a page that fetches must be able to say the fetch failed.**
 * The next list screen anybody adds is covered by a test written for the last one.
 *
 * ⚠️ WHAT IT DELIBERATELY DOES NOT CHECK. It cannot tell a GOOD error state from a
 * present one — that is what `Members.states.test.jsx` does, by driving a 500 and asserting
 * both halves of the three-state rule. This is the cheap structural half: it catches the
 * page that never had the option, which is the one that ships.
 */

/** A page that fetches: it calls a service module and renders. */
const FETCHES = /\b\w+Service\s*\.\s*\w+\s*\(/

/**
 * ➕ OR IT IMPORTS A SERVICE'S READS BY NAME (month end, 2026-09-30). A screen that needs two of a service's reads
 * imports those two, so its chunk carries none of the others -- `import { getAreas } from
 * '../../services/referenceService.js'` -- and that is a fetch as surely as `referenceService.getAreas()`. The pattern
 * above cannot see it: found building month end's P4, whose two new pages it did not count as fetching at all, so the
 * rule held over a population that had quietly lost them.
 */
const IMPORTS_A_READ = /import\s*\{[^}]+\}\s*from\s*['"][^'"]*\/services\/\w+Service(?:\.js)?['"]/
const fetches = (code) => FETCHES.test(code) || IMPORTS_A_READ.test(code)

/**
 * A REJECTION PATH — something that actually runs when the promise rejects.
 *
 * 🔴 THE FIRST VERSION OF THIS WAS TOO GENEROUS, AND THE MUTATION PROOF IS WHAT SAID SO.
 * It also accepted `setListState`, `setError` and an `error` binding, on the reasoning
 * that a narrower pattern would convict correct code and get switched off. Deleting the
 * `.catch` from `BankBranch` — the exact defect this guard exists for — left
 * `setListState('loading')` and `setListState('ready')` in the file, so the pattern still
 * matched and the guard stayed GREEN.
 *
 * 🔑 IT WAS MATCHING THE MACHINERY, NOT THE WIRING. A page carrying an error state
 * variable and no `.catch` is not a page that handles failure; it is a page that LOOKS
 * like one, which is strictly worse — the next reader sees the state variable and stops
 * asking. Only a rejection handler runs on a rejection, so only a rejection handler
 * counts.
 *
 * ⚠️ The reasoning that produced the loose version was not silly, and it is kept here
 * because it is the trap: "do not be so strict that the guard gets switched off" is good
 * advice that, applied without a mutation to check it against, produces a guard that
 * cannot fail.
 */
const HANDLES_FAILURE = /\.catch\s*\(|\bcatch\s*\(|\.then\s*\([^)]*,\s*(?:\(|function)/

/**
 * 🔄 OR THE PAGE DELEGATES BOTH HALVES, AND BOTH HALVES ARE CHECKED (main `8c6b675`).
 *
 * `CaptureMember` stopped carrying a `.catch` of its own when main moved its save into the welcome-letter
 * dialog: its remaining requests are the reference lists, which `useReferenceData` catches for it, and it
 * renders that failure with `DataState`. That is handling, in the two places this guard cares about --
 * somebody catches, and the user is told -- so it is recognised rather than exempted.
 *
 * 🔴 BOTH, NEVER EITHER. A page that calls the hook and never renders its error is exactly the shape this
 * guard exists to catch (`A27`: a fact about the SERVER stated as a fact about the DATA), and the canary
 * below is a page that does precisely that. The hooks are named, not pattern-matched: a new hook that
 * handles failure has to be added here deliberately, which is a decision somebody makes once.
 *
 * ➕ `usePagedRead` (ACB 03.5, decided here): the A.C.B. sheets page through it, it catches every read and
 * answers `state === 'error'`, and the sheet says so through the `Table` it renders -- whose `error` is the
 * failure shown. `AcbHeldOver` passed until now only on an unrelated `catch` in its permission save.
 */
const DELEGATES_FAILURE = /useReferenceData\s*\(|useAsyncOptions\s*\(|usePagedRead\s*\(/
const SHOWS_FAILURE = /<DataState\b|<LoadFailureFrame\b|<Table\b[^>]*?\berror=/
const handlesFailure = (code) =>
  HANDLES_FAILURE.test(code) || (DELEGATES_FAILURE.test(code) && SHOWS_FAILURE.test(code))

/**
 * Pages that fetch nothing of their own, or fetch through a component that does.
 *
 * Each is here with a reason, and the reason is checked: a page on this list that DOES
 * fetch reddens the test, so the list cannot quietly become an exemption.
 */
const NO_FETCH_OF_THEIR_OWN = {}

function pages() {
  return readSources(join(REPO_ROOT, 'src', 'pages'), { root: REPO_ROOT, pattern: /\.jsx$/ })
    .filter((source) => !/\.test\./.test(source.path))
}

describe('every page that fetches can say the fetch failed', () => {
  it('the predicate itself: delegation counts only when the failure is also SHOWN', () => {
    // 🔑 A page that fetches through the hook and renders its error is handled...
    expect(handlesFailure("const refs = useReferenceData(LOADERS)\n<DataState state={refs.state} />")).toBe(true)
    // ...and the same page with nothing on screen for it is NOT, which is the whole point of the pair.
    expect(handlesFailure('const refs = useReferenceData(LOADERS)')).toBe(false)
    expect(handlesFailure('<DataState state="error" />')).toBe(false)
    // The original shape still passes on its own.
    expect(handlesFailure('service.get().catch(() => setError(true))')).toBe(true)
    // ➕ 03.5: a sheet paged through `usePagedRead` whose table is given the failure is handled...
    expect(handlesFailure("const { state } = usePagedRead(read)\n<Table rows={rows}\n  error={state === 'error' ? 'Could not load.' : null} />")).toBe(true)
    // ...and the same sheet whose table is never told is NOT.
    expect(handlesFailure('const { state } = usePagedRead(read)\n<Table rows={rows} loading={false} />')).toBe(false)
  })

  it('the predicate itself: a read imported by name from a service is a fetch; an import from elsewhere is not', () => {
    expect(fetches("import { getAreas } from '../../services/referenceService.js'\ngetAreas().then(setAreas)")).toBe(true)
    expect(fetches('import { monthEnd } from "../../services/monthEndService"')).toBe(true)
    // The canary's other half: a named import from a module that is not a service fetches nothing.
    expect(fetches("import { EMPTY, formatInt } from '../../lib/format.js'")).toBe(false)
    expect(fetches("import { MONTH_END_REFUSAL } from '../../config/monthEnd.js'")).toBe(false)
  })

  it('finds no page whose only outcome is data', () => {
    const sources = pages()

    assertNothingWasSkipped(unreadableDirectories(), 'the list-state scan')
    assertBlankingPreservedCode(blankingDestroyedCode(sources))
    assertNonVacuousScan(sources, FLOORS.PAGE_FILES, 'page components under src/pages/')

    // 🔑 THE MUST-PASS HALF, and without it this whole file is satisfied by a regex that
    // matches nothing. If `FETCHES` stopped matching, no page would fetch, none could be
    // an offender, and the suite would report the rule holding over an empty population.
    const fetching = sources.filter((source) => fetches(source.code))

    expect(
      fetching.length,
      'No page appears to call a service at all. That is a broken pattern, not an ' +
        'application that stopped fetching — and it makes every assertion below vacuous.',
    ).toBeGreaterThan(5)

    const silent = fetching
      .filter((source) => !handlesFailure(source.code))
      .map((source) => source.path)

    expect(
      silent,
      'These pages call a service and have no way to represent a failure — no `.catch`, ' +
        'no `try`, no error state. A rejected request becomes an unhandled rejection and ' +
        'the screen renders its EMPTY state: "no members match your search" when the ' +
        'backend is down. That is register row A27, which `04.2` closed on the member ' +
        'register, and the plan predicted it was one instance of a class.',
    ).toEqual([])
  })

  it('the exemption list names only pages that genuinely do not fetch', () => {
    // The list is empty today. The assertion exists so that the day somebody adds to it,
    // the entry has to be TRUE — a page listed as "fetches nothing" that calls a service
    // reddens this immediately, which is what stops the list becoming a way to pass.
    const sources = pages()
    const wronglyListed = Object.keys(NO_FETCH_OF_THEIR_OWN).filter((path) => {
      const source = sources.find((candidate) => candidate.path === path)
      return source && fetches(source.code)
    })

    expect(
      wronglyListed,
      'A page is exempted as "fetches nothing of its own" and calls a service. Either the ' +
        'exemption is stale or it was never true.',
    ).toEqual([])

    const missing = Object.keys(NO_FETCH_OF_THEIR_OWN).filter(
      (path) => !sources.some((source) => source.path === path),
    )
    expect(missing, 'An exempted page is not on disk under that path.').toEqual([])
  })
})
