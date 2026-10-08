// @vitest-environment jsdom -- axe walks a real DOM tree; there is nothing to scan without one
import { beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from '../pages/members/Members'
// The record's History tab is a lazy chunk; in the registry before the scan, so it waits on the page, not on the box.
import '../pages/members/MemberHistory.jsx'
import CaptureMember from '../pages/members/CaptureMember'
import BankListing from '../pages/bank_branch/BankListing'
import Home from '../pages/Home'
import MonthlyMenu from '../pages/admin/MonthlyMenu'
import MonthEndStatistic from '../pages/admin/MonthEndStatistic'
import { renderWithProviders } from './renderWithProviders'
import { expectNoA11yViolations } from './assertions'
import { server } from './msw/server'
import { monthEndAnswer, monthEndBody } from './msw/monthEndExamples'
import { smokeHandlers } from './msw/smokeLayer'
import { pageFixtures } from './msw/pageFixtures'
import { setupUser } from './user'
import { registerHandlers } from './msw/memberRegister'

/**
 * THE ACCESSIBILITY GATE, on the surfaces named below -- the three this phase named, the first screen since month
 * end's `02.3` (MR-ROB-37), and since the 5 Oct Show & Tell Home's three views and the figures on Month end.
 *
 * WHAT IS DELIBERATELY OFF, AND WHY SAYING SO IS BETTER THAN PRETENDING
 * `color-contrast` is disabled, in `expectNoA11yViolations` rather than here,
 * so it cannot be re-enabled in one file and forgotten in another. jsdom has
 * no canvas, so axe cannot compute contrast at all -- and crucially it does
 * not FAIL when it cannot, it silently reports nothing. A rule that always
 * passes because it never ran is worse than an absent rule: it reads as
 * coverage. Contrast is a device-matrix job and is out of this arc's scope,
 * recorded rather than implied.
 *
 * STATE-GATED SURFACES ARE OPENED FIRST. Scanning a page whose content is
 * behind a tab or a loaded list means scanning an empty shell, which passes
 * trivially. Each case below waits for the surface to actually be there.
 */


beforeEach(() => {
  server.use(...smokeHandlers, ...pageFixtures)
})

describe('accessibility', () => {
  it('the Members list has no violations', async () => {
    server.use(...registerHandlers([]))
    const { container } = renderWithProviders(<MembersPage />)
    await screen.findByText(/No members match these criteria/i)
    await expectNoA11yViolations(container)
  })

  it(
    'the Members list has no violations with rows rendered',
    async () => {
      // The empty table and the populated table are different DOM. Scanning
      // only the empty one would miss every violation that lives in a row --
      // and, since A10, the headings that sort and the pager's page size.
      server.use(
        ...registerHandlers([
          { id: 1, firstNames: 'Synthetic', surname: 'Testperson', status: 'Active' },
        ]),
      )
      const { container } = renderWithProviders(<MembersPage />)
      await screen.findByText(/Testperson/)
      await expectNoA11yViolations(container)
    },
  )

  /**
   * 🔴 A28's PIN WAS HERE, AND IT IS DELETED RATHER THAN EDITED.
   *
   * It asserted the violation set was exactly `['select-name']`. The tempting change was
   * `.toEqual([])` -- one character, same file, and completely wrong: a pin records a
   * defect, so when the defect goes the pin goes with it. Editing it would have turned a
   * record of something broken into a duplicate of the two tests above, which already
   * assert no violations by discovery rather than by list.
   *
   * The fix is in `Members.jsx`: the "Identity Type" filter is `SelectInput` now, which
   * carries a real `<label htmlFor>`. See `knownDefects.js` for the retirement note and
   * for the SECOND site the register never named.
   */

  it('the record\'s Change status dialog has no violations, with SASBO\'s codes listed (month end 03.3, MV-12)', async () => {
    // A state-gated surface, opened first: the record, its Membership tab, the dialog, and a leaving status, so that the
    // code list is there to be scanned rather than an empty dialog.
    server.use(
      ...registerHandlers([{ id: 1, membershipNumber: 'M0001', firstNames: 'Synthetic', surname: 'Testperson', status: 'Active' }]),
      monthEndAnswer('GET /api/reference/deletion-codes'),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await user.click(await screen.findByRole('button', { name: /open the record for/i }))
    await user.click(await screen.findByRole('button', { name: /Membership & Subscriptions/i }))
    await user.click(screen.getByRole('button', { name: 'Change status' }))
    const dialog = await screen.findByRole('dialog', { name: 'Change status' })
    await user.selectOptions(within(dialog).getByLabelText(/^New status/), 'Resigned')
    await waitFor(() => expect(within(dialog).getByLabelText(/^SASBO’s deletion code/)).toBeEnabled())

    // Scoped to the dialog, as Modal's and DateField's scans are: the dialog is portalled out of `container`, and a
    // scan of the whole document costs whatever else it holds. `region` is off for the same reason as theirs -- a
    // dialog is not a page, and the landmarks are the shell's, asserted above.
    await expectNoA11yViolations(dialog, { rules: { region: { enabled: false } } })
  })

  describe('the record\'s History tab (month end 03.6, MR-ROB-37)', () => {
    // The golden page's own entry, with invented values in its shape: the mock's sheet -- a change of status with SASBO's
    // code, the reason under it and a note, a workplace, a save of three fields, a number kept as its last four, a capture.
    const golden = monthEndBody('GET /api/members/{id}/history')
    const entry = (fields) => ({ ...golden.content[0], reason: null, code: null, codeName: null, reasonUnderCode: null, note: null, ...fields })
    const sheet = [
      entry({ kind: 'STATUS', id: 1, what: 'status', from: 'Active', to: 'Resigned', reason: 'DELETION_CODE', code: 2,
        codeName: 'Resigned Society', reasonUnderCode: 'INVENTED: MOVED AWAY', saveId: null, note: 'Invented: a letter.' }),
      entry({ id: 13, what: 'workplace', from: '900002 INVENTED HEAD OFFICE', to: '900003 INVENTED HARBOUR', saveId: 's13' }),
      entry({ id: 12, what: 'phone', from: '+27 60 555 0101', to: '+27 60 555 0188', saveId: 's10' }),
      entry({ id: 11, what: 'personalEmail', from: null, to: 'synthetic.member@example.invalid', saveId: 's10' }),
      entry({ id: 10, what: 'address', from: '12 Invented Road', to: '4 Invented Lane', saveId: 's10' }),
      entry({ id: 9, what: 'identityNumber', from: '****4081', to: '****4087', saveId: 's9' }),
      ...golden.content,
    ]
    const member = { id: 1, membershipNumber: 'M0001', firstNames: 'Synthetic', surname: 'Testperson', status: 'Resigned' }

    const openHistory = async () => {
      const user = setupUser()
      const view = renderWithProviders(<MembersPage />)
      await user.click(await screen.findByRole('button', { name: /open the record for/i }))
      await user.click(await screen.findByRole('button', { name: 'History of changes' }))
      return { ...view, tab: await screen.findByRole('region', { name: 'History of changes, newest first' }) }
    }

    it('its sheet has no violations -- every kind of row the mock draws, and the pager', async () => {
      server.use(
        ...registerHandlers([member]),
        http.get('*/api/members/:id/history', () => HttpResponse.json({ ...golden, content: sheet, totalElements: sheet.length })),
      )
      const { container, tab } = await openHistory()
      await waitFor(() => expect(tab).toHaveAttribute('data-history', 'ready'))
      expect(within(tab).getByText('Only the last four digits are kept.')).toBeInTheDocument()
      await expectNoA11yViolations(container)
    })

    it('its failed state has no violations -- the alert, Try again and the Details', async () => {
      server.use(
        ...registerHandlers([member]),
        http.get('*/api/members/:id/history', () => HttpResponse.error()),
      )
      const { container, tab } = await openHistory()
      await waitFor(() => expect(tab).toHaveAttribute('data-history', 'error'))
      expect(within(tab).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
      await expectNoA11yViolations(container)
    })
  })

  it('the CaptureMember form has no violations', async () => {
    const { container } = renderWithProviders(<CaptureMember />)
    await waitFor(() => expect(screen.getByLabelText(/^Surname/)).toBeInTheDocument())
    await expectNoA11yViolations(container)
  })

  it('the Bank listing has no violations', async () => {
    server.use(
      http.get('*/api/banks/branches', () =>
        HttpResponse.json([
          { id: 1, bankName: 'Synthetic Bank', branchCode: '000000', universalBranchCode: '000000' },
        ]),
      ),
    )
    const { container } = renderWithProviders(<BankListing />)
    await waitFor(() => expect(container.querySelectorAll('*').length).toBeGreaterThan(5))
    await expectNoA11yViolations(container)
  })

  it.each([
    ['Cards', 'cards'],
    ['List', 'list'],
    ['Table', 'table'],
  ])('Home has no violations in its %s view -- the modules and the switch that lays them out (the 5 Oct Show & Tell)', async (name, view) => {
    // Each view is different DOM -- cards, a ruled list, a table with its caption and heads -- so each is opened and
    // scanned, the cards (the view a browser opens on) included.
    const user = setupUser()
    const { container } = renderWithProviders(<Home />)
    await user.click(screen.getByRole('button', { name }))
    await waitFor(() => expect(container.querySelector('[data-home-view]')).toHaveAttribute('data-home-view', view))
    expect(screen.getByRole('button', { name })).toHaveAttribute('aria-pressed', 'true')
    await expectNoA11yViolations(container)
  })

  it('the Month end page\'s figures have no violations -- the strip, their sheet and the lines beneath it (02.3 on Month end, MR-ROB-37)', async () => {
    // The server's own golden answer (MD48): a sheet with rows, a total and the marks the figures draw on them, for the
    // open month the address names. And the lists' status, for the line beneath the sheet (MO24), scanned with the rest.
    server.use(
      monthEndAnswer('GET /api/month-end/months'),
      monthEndAnswer('GET /api/members/summary'),
      monthEndAnswer('GET /api/reference/status'),
    )
    const { container } = renderWithProviders(<MonthlyMenu />, { initialEntries: ['/admin/monthly-menu?month=2026-09'] })
    await screen.findByText('INVENTED GROUP CORE')
    await screen.findByText(/^Grouped by SASBO's lists as printed on/)
    await expectNoA11yViolations(container)
  })

  it('the Month end page has no violations -- its month, its statistics, the pack ready with its links, the listings (04.1, MR-ROB-37)', async () => {
    // A state-gated surface, opened first: the months read, the open month chosen so Take the month is there with its
    // reason, and the pack asked for -- the golden answer is a READY pack, so its two links are scanned too.
    server.use(monthEndAnswer('GET /api/month-end/months'), monthEndAnswer('POST /api/month-end/{month}/pack'))
    const user = setupUser()
    const { container } = renderWithProviders(<MonthlyMenu />, { initialEntries: ['/admin/monthly-menu?month=2026-09'] })
    await user.click(await screen.findByRole('button', { name: 'Prepare the pack for September 2026' }))
    await screen.findByRole('link', { name: /^Download the statistics workbook/ })
    expect(screen.getByRole('button', { name: 'Take September 2026' })).toBeDisabled()
    await expectNoA11yViolations(container)
  })

  it('a statistic\'s screen has no violations -- its criteria, its sheets with rule C, and its notes (04.3-04.8, MR-ROB-37)', async () => {
    // The branches, with a row of fewer than five and one withheld beside it, in the golden answer's own shape: the
    // words and their descriptions are part of what is scanned.
    const branches = monthEndBody('GET /api/month-end/{month}/branches')
    const hidden = (key, label, rule) => ({ key, label, level: 'ROW', fewerThanFive: rule === 'few', withheld: rule === 'withheld', cells: [null, null, null, null, null, null] })
    server.use(
      monthEndAnswer('GET /api/month-end/months'),
      http.get('*/api/month-end/:month/branches', () => HttpResponse.json({
        ...branches,
        sheets: branches.sheets.map((sheet, index) => (index === 1
          ? { ...sheet, rows: [...sheet.rows, hidden('E8', 'INVENTED SMALL EMPLOYER', 'few'), hidden('E9', 'INVENTED NEIGHBOURING EMPLOYER', 'withheld')] }
          : sheet)),
      })),
    )
    const { container } = renderWithProviders(<MonthEndStatistic />, {
      initialEntries: ['/admin/monthly-menu/branches?month=2026-08'],
      path: '/admin/monthly-menu/:statistic',
    })
    await screen.findByText('fewer than five')
    await screen.findByRole('heading', { level: 2, name: 'Notes' })
    await expectNoA11yViolations(container)
  })

  it('the Month end page has no violations while its figures are not available -- the shared fixture\'s empty array', async () => {
    // The state every other test of the page sees: the strip's "not available" and the sheet's sentence.
    server.use(monthEndAnswer('GET /api/month-end/months'))
    const { container } = renderWithProviders(<MonthlyMenu />, { initialEntries: ['/admin/monthly-menu?month=2026-09'] })
    await screen.findByText('The figures are not available from this server yet.')
    await expectNoA11yViolations(container)
  })
})

describe('the a11y gate can actually fail', () => {
  it('reports a violation when one is deliberately introduced', async () => {
    // The gate's own reddening change, run inline and permanently. Without it,
    // "zero violations" is indistinguishable from "axe did not run" -- which
    // is precisely the failure mode `color-contrast` has under jsdom, one
    // block above. A gate that has never been seen to fail is not a gate.
    const { container } = renderWithProviders(
      <div>
        {/* 🔑 THE ONE PLACE IN THIS REPOSITORY WHERE MISSING `alt` IS THE POINT, so the
            lint rule and this test assert opposite things and the test is the stronger of
            the two. `jsx-a11y/alt-text` reads this as the defect it is designed to catch;
            what it cannot see is that the defect is the FIXTURE. Disabled here, at the one
            site, with the reason -- never at config level, because a rule switched off for
            the file would then be off for every real `<img>` a future test renders, which
            is exactly the masking L20 describes.

            It cannot hide a genuine regression either: the assertions below REQUIRE axe to
            report `image-alt` here, so an `alt` added to quieten the linter turns this test
            red rather than green. */}
        {/* eslint-disable-next-line jsx-a11y/alt-text -- deliberately invalid: this is the gate's own reddening fixture, and the assertions below require it to fail */}
        <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" />
        <input type="text" />
      </div>,
    )

    // Both halves, because they come from different places and only one of them
    // was ever asserted.
    //
    // The first is this repository's own sentence, naming the human consequence.
    // The second is `toHaveNoViolations`'s own report -- and asserting it is what
    // proves THE MATCHER did the work. 🔴 `vitest-axe@0.1.0` ships an EMPTY
    // `dist/extend-expect.js`, so a registration that silently registers nothing
    // is a live hazard in this exact version. Without the second assertion this
    // test would pass on the prefix alone, which the prefix would supply even if
    // the matcher had been replaced by something that threw and reported
    // nothing at all.
    const failure = expectNoA11yViolations(container)

    await expect(failure).rejects.toThrow(/accessibility violation/i)
    await expect(failure).rejects.toThrow(/image-alt/)
  })
})
