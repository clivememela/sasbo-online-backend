// @vitest-environment jsdom -- renders the member register in each of its three states
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectErrorState } from '../../test/assertions'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { registerFails, registerHandlers, registerOverview, registerPage } from '../../test/msw/memberRegister'

/**
 * THE THREE-STATE RULE, TESTED WHERE IT IS CLAIMED.
 *
 * Loading, empty, failed-with-retry. The rule exists because an error rendered
 * as an empty table is a CONFIDENT WRONG ANSWER: it tells an administrator
 * there are no members when the backend is down. Those are different facts and
 * only a test can tell them apart -- from the outside they are the same
 * screen, which is exactly why nobody notices.
 *
 * This is the flagship list page of the application, so it is where the rule
 * matters most.
 *
 * ✅ **A27 IS CLOSED, by `04.2`.** When these were written the page had NEITHER a loading
 * state nor an error state: the mount effect's entire failure handling was `console.error`
 * and `membersList` simply stayed `[]`. The register now renders through the `Table`
 * primitive, whose four data states are separate branches by construction. The three pins
 * that recorded the defect are deleted, the two standard-facing tests below are un-skipped,
 * and the "cannot be told apart" case is INVERTED rather than removed -- it is the
 * assertion that would notice the day someone reintroduced the old shape.
 *
 * Every assertion about the failed state goes through `expectErrorState` from
 * `src/test/assertions.js` rather than being hand-rolled here. A second
 * bespoke error-state assertion is the thing that helper exists to prevent:
 * the contract holds because one function enforces it, not because every
 * author remembered both halves of it.
 */

/** The empty state's own words. The whole defect turned on this string. */
// card.html's own sentence for the empty register; the search-shaped variant reads
// "No member matches “…”" and is not this test's subject.
const EMPTY_TEXT = /No members match these criteria/i

const MEMBERS = [
  {
    id: 1,
    firstNames: 'Synthetic',
    surname: 'Testperson',
    identityNumber: '9001010000080',
    status: 'Active',
  },
]

/**
 * The register answering with `members` -- its page, its figures and each record (A10) -- or, given a status, its page
 * refused with it. An array of handlers either way, to spread into `server.use`.
 */
const membersRespond = (members, status = 200) =>
  (status === 200 ? registerHandlers(members) : [registerFails(status)])

/**
 * An answer that does not arrive until the test says so, so the loading state is observable
 * BY CONSTRUCTION rather than by winning a race.
 *
 * 🔴 IT USED TO BE A 50ms `setTimeout`, AND THAT WAS A REAL, REPRODUCED FLAKE. Found by the
 * Capture Member session on 2026-09-18 and handed here: always this file, always the LOADING
 * test, always its closing `findByText`, with the captured DOM showing the skeleton still
 * rendering. The mechanism is that testing-library's `findBy*` carries its OWN 1000ms default
 * -- NOT vitest's `testTimeout: 20_000` from `vite.config.js` -- so "eventually" was pinned to
 * one second, and one second had to contain mounting the whole members page in jsdom PLUS the
 * deliberate 50ms PLUS React's commit. On a loaded box it did not.
 *
 * 🔑 THE FIX IS NOT A LONGER TIMEOUT, AND THE DIFFERENCE IS THE WHOLE POINT. Raising 1000 to
 * 5000 was the proposal and it was declined in writing: a wider window is the same race with a
 * longer fuse, and it makes the flake rarer without making it absent -- which is strictly
 * worse, because a flake that fires once a month is one nobody can ever diagnose. At the
 * observed rate it was reproducible in minutes, and that is what made the mechanism findable.
 * So the WALL-CLOCK DEPENDENCE goes instead: nothing here is timed, and the request answers
 * when this test releases it.
 *
 * It is also strictly stronger than what it replaces. The three loading assertions below used
 * to pass because 50ms happened to be longer than a mount -- a duration comparison. They are
 * now guaranteed: the handler CANNOT answer before `release()`, whatever the box is doing.
 * (That was latent rather than live: RTL flushes the commit inside `act()` before the
 * assertions run, so it is not known ever to have fired.)
 *
 * ⚠️ THE ORDER IS LOAD-BEARING, AND IT READS LIKE A HELPER RATHER THAN A PROTOCOL. Create it
 * and `server.use()` it BEFORE rendering, and keep `release` reachable from the test body. Get
 * that wrong and the request is never released, so the test HANGS to vitest's 20s
 * `testTimeout` instead of failing at 1s -- slower and more confusing than the flake this
 * replaces.
 */
const membersRespondWhenReleased = () => {
  const answer = deferred()
  return {
    // A10: the page and the figure strip are two reads now, and both wait -- a strip that answered first would print
    // its counts beside a register that has not.
    handlers: [
      http.post('*/api/members/register/search', async ({ request }) => {
        const query = await request.json()
        await answer.promise
        return HttpResponse.json(registerPage(MEMBERS, query))
      }),
      http.get('*/api/members/register/overview', async () => {
        await answer.promise
        return HttpResponse.json(registerOverview(MEMBERS))
      }),
    ],
    release: () => answer.resolve(),
  }
}

let consoleError

describe('the Members list', () => {
  beforeEach(() => {
    // Still spied: the page logs the failure as well as showing it, and an unmocked
    // console.error would be real noise on a green run.
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
  })

  describe('the EMPTY state', () => {
    it('says so plainly when the server genuinely has no members', async () => {
      server.use(...membersRespond([]))
      renderWithProviders(<MembersPage />)

      expect(await screen.findByText(EMPTY_TEXT)).toBeInTheDocument()
    })

    it('and its foot and figures say the same thing', async () => {
      // The PRESENCE half of K1's case below: the same two locators, seen to match when "no
      // members" is the truth. Their absence under a failure proves nothing without this (T37).
      server.use(...membersRespond([]))
      renderWithProviders(<MembersPage />)

      await screen.findByText(EMPTY_TEXT)
      expect(screen.getByText(/^No members match$/)).toBeInTheDocument()
      expect(within(screen.getByTestId('figure-strip')).getAllByText('0').length).toBeGreaterThan(0)
    })
  })

  describe('the LOADED state', () => {
    it('renders the members the server returned', async () => {
      server.use(...membersRespond(MEMBERS))
      renderWithProviders(<MembersPage />)

      expect(await screen.findByText(/Testperson/)).toBeInTheDocument()
      // The empty text must be gone. Asserted because a table that renders
      // both its rows and its empty message is a real bug that reads as fine.
      expect(screen.queryByText(EMPTY_TEXT)).not.toBeInTheDocument()
    })
  })

  describe('the LOADING state', () => {
    it('says it is loading before it says it is empty', async () => {
      // A request that has not answered yet is not an answer. The page used to render
      // its empty table synchronously on mount, so the FIRST thing every user saw,
      // every time, was "no members" -- which then flickered into data.
      const members = membersRespondWhenReleased()
      server.use(...members.handlers)
      renderWithProviders(<MembersPage />)

      try {
        expect(screen.getByRole('status')).toBeInTheDocument()
        expect(screen.queryByText(EMPTY_TEXT)).not.toBeInTheDocument()
        // K1: nor does it print a row of zeros while it waits.
        expect(within(screen.getByTestId('figure-strip')).queryByText('0')).not.toBeInTheDocument()
      } finally {
        // 🔑 `finally`, SO A FAILING ASSERTION CANNOT LEAVE A PENDING REQUEST BEHIND IT. An
        // unreleased handler would sit unresolved for the rest of the file and muddy the
        // diagnosis of whatever failed -- the next author would be reading a hung request
        // that their own test did not cause.
        members.release()
      }

      // ...and it does eventually finish. Without this the test above would also pass
      // against a page that renders a spinner and never anything else -- which is A22's
      // shape, one row over.
      expect(await screen.findByText(/Testperson/)).toBeInTheDocument()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })
  })

  describe('the FAILED state', () => {
    it('shows an error with a retry, not "no members"', async () => {
      server.use(...membersRespond({ status: 500, error: 'Internal Server Error' }, 500))
      renderWithProviders(<MembersPage />)

      // 🔑 WAIT FIRST. `expectErrorState` queries SYNCHRONOUSLY -- it checks both halves
      // of the rule at the instant it is called, which is what makes it a rule and not a
      // poll. Called straight after render it asserts about the LOADING screen, where
      // there is legitimately no error affordance yet, and reports the confident-wrong-
      // answer failure for a page that is behaving perfectly.
      await screen.findByRole('alert')

      // Both halves, through the shared helper: the error affordance is
      // present AND the empty-state text is absent.
      const { retry } = await expectErrorState({ emptyText: EMPTY_TEXT })
      expect(retry, 'a failed list must offer a way to try again').not.toBeNull()
    })

    it('says nothing about the count while the register cannot be reached', async () => {
      // 🔴 K1 (`06.3`), found by a capture of the running app, not by this file. The error state
      // above was right and the sheet around it was not: the pager's foot said "No members match"
      // and the figure strip printed a row of zeros, so the page still told an administrator the
      // union was empty -- two lines under the sentence saying the server had not answered.
      // `expectErrorState` looks for the EMPTY STATE's words, which neither of them prints.
      server.use(...membersRespond({ status: 500 }, 500))
      renderWithProviders(<MembersPage />)
      await screen.findByRole('alert')

      expect(screen.queryByText(/no members match/i)).not.toBeInTheDocument()
      expect(within(screen.getByTestId('figure-strip')).queryByText('0')).not.toBeInTheDocument()
      // And the month's figure (SASBO, 18 Sep 2026) says nothing either -- not even its second line.
      expect(screen.getByTestId('figure-strip')).not.toHaveTextContent(/by debit order/)
    })

    it('the retry actually re-runs the request, and recovers', async () => {
      // 🔑 AN ERROR STATE WITH A DEAD BUTTON IS STILL A DEAD END, and that is invisible
      // from the outside: the affordance is there, it has the right name, and pressing
      // it does nothing. The assertion above cannot tell those apart -- this can.
      const user = setupUser()
      server.use(...membersRespond({ status: 500 }, 500))
      renderWithProviders(<MembersPage />)

      await screen.findByRole('alert')
      const { retry } = await expectErrorState({ emptyText: EMPTY_TEXT })

      server.use(...membersRespond(MEMBERS))
      await user.click(retry)

      expect(await screen.findByText(/Testperson/)).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('the retry goes back to LOADING, rather than sitting on the error until the answer lands', async () => {
      /*
       * 🔴 THE BEHAVIOUR `10.4` MOVED, AND NOTHING WAS ASSERTING IT.
       *
       * `setListState("loading")` used to be the first line of `loadMembers`, which the mount
       * effect called -- a `react-hooks/set-state-in-effect` error as of this phase. It was a
       * no-op on mount (the state already initialises to "loading"), so it was hoisted out of
       * the fetch and onto the retry path, where it is the only place it ever did work.
       *
       * ⚠️ THE EXISTING RETRY TEST ABOVE CANNOT SEE THAT. It asserts the request re-runs and the
       * rows arrive, which stays true whether or not the screen ever goes back to loading -- so
       * wiring `onRetry` to the wrong function would have left it green while the user pressed
       * Try again and watched the error message sit there until the data appeared. Six retry
       * sites were rewired in that batch; this is what proves the wiring.
       *
       * Deterministic on purpose: the second answer is held until this test releases it, so the
       * loading assertion is guaranteed rather than winning a race against a real delay.
       */
      const user = setupUser()
      server.use(...membersRespond({ status: 500 }, 500))
      renderWithProviders(<MembersPage />)

      await screen.findByRole('alert')
      const { retry } = await expectErrorState({ emptyText: EMPTY_TEXT })

      const second = membersRespondWhenReleased()
      server.use(...second.handlers)
      try {
        await user.click(retry)

        expect(screen.getByRole('status')).toBeInTheDocument()
        // The must-pass beside it: the failure it replaced is GONE. A screen that stacked the
        // spinner on top of the old error would satisfy the line above and still be wrong.
        expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      } finally {
        second.release()
      }

      expect(await screen.findByText(/Testperson/)).toBeInTheDocument()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })

    it('CAN be told apart from a genuinely empty list', async () => {
      // 🔴 THE INVERSION. This case used to assert the two screens were IDENTICAL, with a
      // message saying "if these now differ, check A27". They differ. It is kept, the
      // right way round, because it is the one assertion that compares the two screens
      // against each other rather than each against a fixture -- so it is what would
      // notice the day a well-meaning change made a failed fetch fall back to `[]` again.
      server.use(...membersRespond({ status: 500 }, 500))
      const failed = renderWithProviders(<MembersPage />)
      await screen.findByRole('alert')
      await expectErrorState({ emptyText: EMPTY_TEXT })
      const whenDown = failed.container.textContent
      failed.unmount()

      server.resetHandlers()
      server.use(...pageFixtures, ...membersRespond([]))
      const empty = renderWithProviders(<MembersPage />)
      await screen.findByText(EMPTY_TEXT)
      const whenEmpty = empty.container.textContent

      expect(
        whenDown,
        'The backend being down and the union having no members render the same screen ' +
          'again. That is A27 exactly: an administrator reads "no members" as a fact ' +
          'about the union rather than a fact about the server.',
      ).not.toBe(whenEmpty)
    })
  })

  describe('the network never being reached at all', () => {
    it('is reported as a failure too, not as "no members"', async () => {
      // A rejected fetch and a 500 arrive at the page by different paths -- one throws
      // before there is a response to read -- and only one of them was ever exercised.
      server.use(registerFails('network'))
      renderWithProviders(<MembersPage />)

      await screen.findByRole('alert')
      const { retry } = await expectErrorState({ emptyText: EMPTY_TEXT })
      expect(retry).not.toBeNull()

      /*
       * 🔑 THE ASSERTION CHANGED SHAPE IN `06.1b`, AND IT GOT STRICTER RATHER THAN LOOSER.
       *
       * It used to require `console.error('Failed fetching records array', err)` -- a bare
       * call with the whole rejection as its second argument. EH-D10 replaced every such
       * call with one sink, because `06.1a` had just made every rejection carry `body`, the
       * server's parsed envelope: on a member endpoint, that is the member. So a test
       * demanding the old call was requiring the leak.
       *
       * What is asserted now is BOTH halves -- that the failure is still reported at all,
       * which is what the old test was for, and that what is reported is the redacted line.
       */
      await waitFor(() => expect(consoleError).toHaveBeenCalled())

      const [tag, line] = consoleError.mock.calls.at(-1)
      expect(tag).toBe('[sasbo]')
      // A10: the register's read is `Members.loadRegister` since it reads a page.
      expect(line).toMatchObject({ context: 'Members.loadRegister', kind: 'network' })
      // The whole emitted line, not field by field: a sink that copied `body` wholesale
      // under a key this test did not think to check would pass a narrower assertion.
      expect(JSON.stringify(line)).not.toMatch(/membershipNumber|identityNumber|surname/i)
    })
  })
})
