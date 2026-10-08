// @vitest-environment jsdom -- searching from a later page, and the frame in between
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Outlet, Route, Routes } from 'react-router-dom'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * 🔴 SEARCH FROM PAGE 3 AND THE REGISTER SAID "NO MEMBER MATCHES" -- FOR MEMBERS IT WAS ABOUT TO SHOW. `10.4`.
 *
 * When the register filtered every member in the browser, its page was clamped by an effect: search from page 3 for a
 * surname five members share, and React committed one frame with the page still 3 and one page in total -- an EMPTY
 * slice -- drawing **No member matches "..."** over members it was about to show. `10.4` derived the page during
 * render, and this file watched that transition.
 *
 * 🔁 SINCE A10 THE PAGE IS THE SERVER'S ANSWER, and the claims stand as they were, re-aimed:
 *   - a new search asks for the FIRST page of its answer (`useRegisterSearch`), and while it is asked the register is
 *     LOADING -- the answer for the old question is no longer current -- so no frame says nobody matches;
 *   - the must-pass: a search that matches nobody does say so, so the watch can see the title it watches for;
 *   - clear the search afterwards and the register is on its first page, not thrown back to page 3.
 *
 * The page is mounted under the smallest shell that supplies the topbar's search through the outlet, as the real one
 * does -- the one path that drives the search. Pages of 25 (the smallest the screen offers), in membership-number order,
 * so page 3 is members 51 to 55; five members share a surname nobody else has. The register OPENS on page 3 -- the page
 * is the address bar's since A10, so a link or a refresh lands there -- rather than paging to it: two pages of 25 rows
 * drawn for nothing cost seconds on a loaded box, and a test's verdict must not depend on load.
 */
const SIZE = 25
const NEEDLES = 5

const MEMBERS = Array.from({ length: 2 * SIZE + NEEDLES }, (_, i) => ({
  id: i + 1,
  membershipNumber: `M${String(i + 1).padStart(5, '0')}`,
  firstNames: 'Synthetic',
  surname: i < NEEDLES ? 'Needleworth' : `Haystack${i}`,
  status: 'Active',
}))

const number = (n) => `M${String(n).padStart(5, '0')}`

/** The smallest shell that gives the page what the real one does: the topbar's search, through the outlet. */
function Shell({ search }) {
  return <Outlet context={{ search, setSearch: () => {} }} />
}

const page = (search) => (
  <Routes>
    <Route element={<Shell search={search} />}>
      <Route path="/" element={<MembersPage />} />
    </Route>
  </Routes>
)

/**
 * Records whether `pattern` was EVER written into the document while the observer ran -- including for a frame
 * that was removed again before anybody looked.
 *
 * 🔴 IT READS THE MUTATION RECORDS, NOT THE CURRENT TEXT. A MutationObserver callback is a microtask; inside `act()`
 * React can write a title and remove it before the callback runs, so the current text is clean while the frame
 * happened. Every node added is delivered in `addedNodes` even if it is gone by delivery time, which is the one
 * thing a transient frame leaves.
 */
function watchFor(pattern) {
  let seen = false
  const inspect = (records) => {
    for (const record of records) {
      if (record.type === 'characterData' && pattern.test(record.target.textContent ?? '')) seen = true
      for (const node of record.addedNodes) {
        if (pattern.test(node.textContent ?? '')) seen = true
      }
    }
  }
  const observer = new MutationObserver(inspect)
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  if (pattern.test(document.body.textContent ?? '')) seen = true
  return {
    stop: () => {
      inspect(observer.takeRecords())
      observer.disconnect()
      return seen
    },
  }
}

let watcher

describe('the register, searched from a later page', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    server.use(...registerHandlers(MEMBERS))
  })

  afterEach(() => watcher?.stop())

  async function onPageThree() {
    const view = renderWithProviders(page(''), { initialEntries: [`/?size=${SIZE}&sort=membershipNumber&page=3`] })
    // Page 3 is the last five rows.
    expect(await screen.findByText(number(2 * SIZE + 1))).toBeInTheDocument()
    expect(screen.queryByText(number(1))).not.toBeInTheDocument()
    return view
  }

  it('never claims "no member matches" on the way to the members that do', async () => {
    const { rerender } = await onPageThree()

    watcher = watchFor(/No member matches/)
    rerender(page('needleworth'))

    // Every one of the five arrives...
    expect(await screen.findByText(number(1))).toBeInTheDocument()
    expect(screen.getByText(number(NEEDLES))).toBeInTheDocument()

    // ...and at NO point on the way did the page say none of them existed.
    expect(
      watcher.stop(),
      'The register drew "No member matches" for a search that matches five members -- a frame showing the old ' +
        'page\'s answer as the new question\'s. A question the server has not answered is LOADING.',
    ).toBe(false)
  })

  it('the must-pass: a search that genuinely matches nothing DOES say so, so the watch above can see it', async () => {
    // 🔑 WITHOUT THIS THE TEST ABOVE COULD PASS VACUOUSLY -- an observer that was never able to see the empty title
    // (wrong text, wrong node, never attached) reports "not seen" whether or not the frame happened.
    const { rerender } = await onPageThree()

    watcher = watchFor(/No member matches/)
    rerender(page('nobody-has-this-surname'))

    expect(await screen.findByText(/No member matches/)).toBeInTheDocument()
    expect(watcher.stop()).toBe(true)
  })

  it('clear the search afterwards and you are on page 1, not thrown back to page 3', async () => {
    // A new search asks for the first page of its answer, and so does clearing it: page 3 of the whole register is a
    // page nobody chose for it.
    const { rerender } = await onPageThree()

    rerender(page('needleworth'))
    expect(await screen.findByText(number(NEEDLES))).toBeInTheDocument()

    rerender(page(''))
    expect(await screen.findByText(number(1))).toBeInTheDocument()
    expect(screen.queryByText(number(2 * SIZE + 1))).not.toBeInTheDocument()
  })
})
