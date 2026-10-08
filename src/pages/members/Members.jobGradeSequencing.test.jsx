// @vitest-environment jsdom -- the record's job-grade list: when it is fetched, and whose answer wins
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { jobGradeService } from '../../services/jobGradeService.js'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * 🔴 R3-F4 (P9's independent review): THE RECORD FETCHED JOB GRADES ON EVERY OPEN, UNSEQUENCED.
 *
 * The effect keys on `editForm.bankName`, which is set whenever a record OPENS, not only while editing -- so simply
 * looking at a member cost a job-grades request (and, for a bank name with a space, the bank listing before it) on the
 * screen used most, while the read view never uses the answer: it prints `editForm.jobGrade` directly. Worse, the
 * fetch had no ticket and no cancellation, so changing bank A → B while editing, with A's answer arriving LAST, left
 * A's grades under bank B -- and a grade from the wrong bank could then be saved. The arc built `useAsyncOptions` for
 * exactly this class (R-ROB-G).
 *
 * The pairs: ① a record merely opened asks for no grades, and the must-pass that editing still loads them;
 * ② an older answer cannot overwrite a newer one, with the newer bank's grades still offered as the must-pass half.
 */

const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Gradeholder',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll',
  bankName: 'ABSA',
  jobGrade: 'G7',
}

const panel = () => within(screen.getByTestId('member-panel'))

async function openTheRecord() {
  const user = setupUser()
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  await user.click(await screen.findByRole('button', { name: /Employment & Bank/i }))
  await panel().findByText('Job Grade')
  return user
}

describe('the record’s job grades', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    server.use(
      ...registerHandlers([MEMBER]),
      http.get('*/api/banks', () => HttpResponse.json([{ name: 'ABSA' }, { name: 'Capitec' }])),
    )
  })

  // The service spies below are per test: a spy left in place carries its calls into the next test, and ② looks
  // its bank's call up by name.
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('① a record that is only being read asks for no job grades', async () => {
    /*
     * 🔑 THE WITNESS IS THE CALL, NOT A NETWORK HIT AFTER A SLEEP (RF). This slept a real 50ms and then counted
     * requests -- so a fetch that started later than that would have passed it. The record's effect calls
     * `getByBank` SYNCHRONOUSLY, and every step of `openTheRecord` runs inside `act`, which flushes effects: by
     * the time it returns, a call that was going to happen has happened.
     */
    const asked = vi.spyOn(jobGradeService, 'getByBank')
    await openTheRecord()

    // The grade is still shown, from the record itself -- so this is "not fetched", not "not rendered".
    expect(panel().getByText('Job Grade').nextElementSibling).toHaveTextContent('G7')
    expect(asked, 'opening a record to READ it fetched its bank’s job grades').not.toHaveBeenCalled()
  })

  it('the must-pass: entering edit mode does fetch them', async () => {
    server.use(
      http.get('*/api/job-grades', () => HttpResponse.json([{ value: 'G7', label: 'G7' }, { value: 'G8', label: 'G8' }])),
    )
    const asked = vi.spyOn(jobGradeService, 'getByBank')
    const user = await openTheRecord()

    await user.click(screen.getByRole('button', { name: /Edit Member/i }))
    await within(await screen.findByLabelText(/^Job Grade/i)).findByRole('option', { name: 'G8' })
    expect(asked).toHaveBeenCalledWith('ABSA')
  })

  it('② an older bank’s answer cannot overwrite a newer one', async () => {
    const { promise: absaAnswered, resolve: answerAbsa } = deferred()
    const asked = vi.spyOn(jobGradeService, 'getByBank')
    server.use(
      http.get('*/api/job-grades', async ({ request }) => {
        if (new URL(request.url).searchParams.get('bankCode') === 'ABSA') {
          await absaAnswered
          return HttpResponse.json([{ value: 'ABSA-ONLY', label: 'ABSA-ONLY' }])
        }
        return HttpResponse.json([{ value: 'CAPITEC-ONLY', label: 'CAPITEC-ONLY' }])
      }),
    )
    const user = await openTheRecord()
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))

    const bank = await screen.findByLabelText(/^Bank Where Employed/i)
    await within(bank).findByRole('option', { name: 'Capitec' })
    await user.selectOptions(bank, 'Capitec')

    const grade = await screen.findByLabelText(/^Job Grade/i)
    await within(grade).findByRole('option', { name: 'CAPITEC-ONLY' })

    // 🔑 WAIT FOR THE STALE ANSWER ITSELF, NOT FOR A CLOCK (RF). The screen's own `.then` was attached to this
    // promise first, so by the time awaiting it here resumes, the screen has had the late answer -- and `act`
    // has rendered whatever it did with it.
    const absaCall = asked.mock.calls.findIndex(([bank]) => bank === 'ABSA')
    expect(absaCall, 'the old bank was never asked, so its late answer proves nothing').toBeGreaterThanOrEqual(0)
    answerAbsa()
    await act(async () => { await asked.mock.results[absaCall].value.catch(() => {}) })
    expect(
      within(grade).queryByRole('option', { name: 'ABSA-ONLY' }),
      'the old bank’s late answer replaced the grades of the bank now chosen',
    ).toBeNull()
    expect(within(grade).getByRole('option', { name: 'CAPITEC-ONLY' })).toBeInTheDocument()
  })
  it('③ while a new bank’s grades are loading, the OLD bank’s are not on offer', async () => {
    /*
     * 🔴 R3-F4's HAZARD, ONE STEP EARLIER THAN ② CAUGHT IT. `10.4`.
     *
     * ② proves an older bank's LATE answer cannot overwrite a newer one. It does not look at the
     * gap BEFORE the newer answer lands -- and in that gap the list still held the old bank's
     * grades, because nothing cleared them when the bank changed; only the next answer did. So
     * switch ABSA -> Capitec on a slow connection and ABSA's grades sat under "Capitec", one
     * click from being saved against the wrong bank. That is the defect R3-F4 exists to prevent,
     * reached by waiting instead of by racing.
     *
     * Found while taking `react-hooks/set-state-in-effect` to zero: the fix for that warning is
     * to key the result by bank and attempt and show it only while it is CURRENT, which closes
     * this gap as a consequence. Written against the code BEFORE that fix, so it is red first.
     */
    const capitec = deferred()
    server.use(
      http.get('*/api/job-grades', async ({ request }) => {
        if (new URL(request.url).searchParams.get('bankCode') === 'Capitec') {
          await capitec.promise
          return HttpResponse.json([{ value: 'CAPITEC-ONLY', label: 'CAPITEC-ONLY' }])
        }
        return HttpResponse.json([{ value: 'ABSA-ONLY', label: 'ABSA-ONLY' }])
      }),
    )
    const user = await openTheRecord()
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))

    // ABSA's list is in, and is on offer -- the starting point is real, not assumed.
    const grade = await screen.findByLabelText(/^Job Grade/i)
    await within(grade).findByRole('option', { name: 'ABSA-ONLY' })

    const bank = await screen.findByLabelText(/^Bank Where Employed/i)
    await within(bank).findByRole('option', { name: 'Capitec' })
    try {
      await user.selectOptions(bank, 'Capitec')

      expect(
        within(screen.getByLabelText(/^Job Grade/i)).queryByRole('option', { name: 'ABSA-ONLY' }),
        'ABSA’s grades were still offered under Capitec while Capitec’s were loading',
      ).toBeNull()
    } finally {
      capitec.resolve()
    }

    // The must-pass: the new bank's grades DO arrive, so "not offered" above is the gap closing,
    // not a list that has stopped working.
    await within(screen.getByLabelText(/^Job Grade/i)).findByRole('option', { name: 'CAPITEC-ONLY' })
  })
})
