// @vitest-environment jsdom -- renders the capture form and reads its <option> elements
import { beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * THE JOB-GRADE DROPDOWN: the label is shown, the VALUE is submitted -- NT-3.
 *
 * WHY THIS IS THE ONE DROPDOWN THAT NEEDS ITS OWN TEST
 * Every other select on this form is fed a plain array of strings from
 * `memberConstants`, where the displayed text and the stored value are the same
 * thing and cannot disagree. This one is fed `{value, label}` OBJECTS from the
 * server -- `{value: "C1", label: "C1 - Clerk - Permanent"}` -- so display and
 * storage are two different fields for the first time, and a mapping that
 * confuses them is invisible on screen in one direction and corrupts data in the
 * other.
 *
 * 🔴 THE REGRESSION IT PINS is `Field`'s option mapping collapsing back to the
 * bare `opt`:
 *
 *     <option key={opt} value={opt}>{opt}</option>
 *
 * With object options that renders `[object Object]` as both the value and the
 * text -- so the screen shows nonsense AND the form submits nonsense. It is a
 * one-line simplification that looks harmless beside every other select on the
 * form, which is exactly why it needs an assertion rather than a convention.
 *
 * ✅ IT NOW ASSERTS THE FAILURE PATH TOO, AND THAT IS `06.1b` CLOSING A25.
 *
 * This block used to say the opposite -- that the failure path was A25's, a live
 * register row asserted by a skip and a pin in `services.errorContract.test.js`,
 * and that re-asserting it here would turn a deliberate red green. That was
 * correct while the row was open. It is now closed, and the reason the closure
 * needed a test in THIS file is the row's own criterion:
 *
 *     "the method rejects AND the caller renders an error"
 *
 * `services.errorContract.test.js` proves the first clause, by discovery, over
 * every service method. Nothing but a test that drives this screen can prove the
 * second -- and a fix that satisfied only the first would be a regression, since
 * before it there was at least a quiet fallback where there would now be an
 * unhandled rejection.
 *
 * This file covers the SUCCESS shape, which nothing covered, and now the FAILURE
 * shape from the side a user sees.
 *
 * 🔑 AND IT DRIVES THE REAL SCREEN rather than rendering the select in
 * isolation. `Field` is not exported, and exporting it to test it would be a
 * test dictating the component's shape -- the dropdown is reached the way an
 * administrator reaches it: choose a bank, and the grades for that bank arrive.
 */

/** A bank the form can resolve, and the grades that bank answers with. */
const BANK = { id: 1, bankName: 'Bidvest Bank Limited', universalBranchCode: '462005' }

const GRADES = [
  { value: 'C1', label: 'C1 - Clerk - Permanent' },
  { value: 'BA1', label: 'BA1' },
]

/**
 * The endpoints this screen touches, declared before the catch-all.
 *
 * ⚠️ ORDER MATTERS AND IT HAS BITTEN THIS SUITE BEFORE. `pageFixtures` is a
 * single wildcard `http.get` over every api path, returning an empty array, and
 * msw answers with the FIRST matching handler -- so a catch-all registered ahead
 * of these would swallow every one of them and the dropdown would render empty
 * for a reason no assertion names. The specific handlers go first.
 *
 * (The glob is described rather than quoted here on purpose: written out, its
 * closing characters end this comment block. An earlier draft slipped a
 * zero-width space in to prevent that, which `no-irregular-whitespace` caught --
 * correctly, since an invisible character in source is a worse trade than a
 * sentence.)
 */
beforeEach(() => {
  server.use(
    http.get('*/api/banks', () => HttpResponse.json([BANK])),
    http.get('*/api/banks/branches', () => HttpResponse.json([BANK])),
    http.get('*/api/job-grades', () => HttpResponse.json(GRADES)),
    ...pageFixtures,
  )
})

/**
 * Choose a bank through the searchable control.
 *
 * 🔴 THE LABEL IS ATTACHED TO NOTHING, so this cannot use `getByLabelText` --
 * and that is a defect in the screen rather than a shortcut taken here. Every
 * "searchable" field renders its `<label htmlFor>` pointing at an id the control
 * underneath never sets, so a screen reader announces an unlabelled text box.
 * That is register row `A35`, asserted by the browser suite; this reaches the
 * input through the label's own container, which is the same accommodation the
 * page object makes, for the same reason.
 */
async function chooseBank(user) {
  await openEmploymentTab(user)

  const label = await screen.findByText(/^Bank Where Employed/)
  const input = label.closest('div').querySelector('input')

  await user.type(input, 'Bidvest')
  await user.click(await screen.findByRole('option', { name: BANK.bankName }))
}

/**
 * Open the tab the bank and job-grade fields live on.
 *
 * The form opens on `Personal Information` and renders ONE tab's fields at a
 * time, so neither field exists in the DOM until this runs -- and the failure
 * without it is `Unable to find an element with the text`, which reads as a
 * renamed label rather than as an unopened tab.
 *
 * ⚠️ Matched with a leading anchor rather than exactly: a tab's accessible name
 * gains an asterisk the moment a field on it is invalid, so `{ name: 'Employment
 * & Bank', exact: true }` works until the first validation error and then stops.
 * Nothing here triggers one today; the anchor means nothing here has to keep
 * being true.
 */
async function openEmploymentTab(user) {
  await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
}

/**
 * ⚠️ JOB GRADE IS A COMBOBOX NOW, NOT A NATIVE `<select>` -- 02.5/CM-37, the same commit that let a
 * grade be typed. `allowCustom` exists only on `SearchableSelect`, and a native select cannot accept
 * a value that is not one of its options, so the control had to change for the capability to exist.
 *
 * Everything the assertions below are ABOUT is unchanged: the option's text is the bare grade code
 * and the value it carries is the same code. What changed is that the options exist only while the
 * list is open, so each one opens it first.
 */
const gradeCombobox = () =>
  screen.getByText(/^Job Grade/).closest('div').querySelector('input')

async function openGrades(user) {
  const input = gradeCombobox()
  await user.click(input)
  return input
}

describe('the job-grade dropdown', () => {
  it('shows the grade CODE as its text and carries the same code as its value', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)

    // The <option> elements themselves, because that is where the two fields
    // separate: `textContent` is what the administrator reads and `value` is
    // what the form submits. Asserting only the visible text would pass while
    // the wrong thing was being saved -- which is this defect class exactly.
    //
    // 🔑 SINCE MAIN a23fb81 (2026-09-16) THE TEXT IS THE BARE GRADE, not "C1 - Clerk -
    // Permanent": the fixture still sends the long label, and the service now forces
    // the label to the grade. The `[object Object]` regression this pins is unchanged.
    await openGrades(user)
    const clerk = await waitFor(() => screen.getByRole('option', { name: 'C1' }))

    // In a listbox the stored value rides on `aria-selected`/the commit rather than on a DOM
    // `value`, so the pairing is asserted where it now lives: the row reads 'C1', and choosing it
    // puts 'C1' -- not the decorated label -- in the field.
    expect(clerk.textContent).toBe('C1')
    await user.click(clerk)
    await waitFor(() => expect(gradeCombobox()).toHaveValue('C1'))
  })

  it('renders a grade with no description as its bare code, with no dangling separator', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)

    // 🔑 THE SHAPE THAT SHIPPED BROKEN, kept as a client-side witness. The
    // server's label builder joined all three parts and then scrubbed the
    // separators it did not want, so a grade with no description and no contract
    // type reached the browser as `"BA1 -"`. It is fixed on the server and
    // asserted there; this is the assertion from the side a user actually sees,
    // and it is the reason the fixture above carries a one-part grade at all.
    await openGrades(user)
    const bare = await waitFor(() => screen.getByRole('option', { name: 'BA1' }))

    expect(bare.textContent).toBe('BA1')
    expect(bare.textContent).not.toMatch(/-\s*$/)
  })

  it('offers no grades at all until a bank is chosen', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmploymentTab(user)

    // The dropdown is disabled without a bank and `JOB_GRADE_OPTIONS` is empty,
    // so the screen shows "No job grades available". Asserted because it is the
    // state the form OPENS in -- the one every administrator sees first -- and
    // because it proves the two assertions above are about grades that arrived
    // from the server rather than about anything the component had already.
    // The empty state is the combobox's PLACEHOLDER now rather than an option row -- the same
    // sentence, in the place a combobox has to put it. Still asserted for the same reason: it is
    // the state the form opens in, and it proves the two assertions above are about grades that
    // arrived from the server rather than anything the component already held.
    await waitFor(() =>
      expect(gradeCombobox()).toHaveAttribute('placeholder', 'No job grades available'))
  })
})

describe('changing the bank -- main e56dbe4', () => {
  /**
   * A grade is a fact about ONE bank, so a grade chosen for the first bank must not
   * ride along to the second. Main set out to do this on both member forms; the edit
   * form's half is `Members.jobGrade.test.jsx` ③, and nothing asserted this one.
   *
   * 🔑 BOTH BANKS ANSWER WITH THE SAME GRADES, AND THAT IS WHAT MAKES THIS NON-VACUOUS.
   * Had the second bank answered with different grades, a form that never cleared
   * anything would still read `''`: a controlled <select> whose value is not among its
   * options displays none of them. With the same list, `C1` stays valid for the second
   * bank, so the ONLY thing that can empty the field is the reset itself.
   */
  const SECOND_BANK = { id: 2, bankName: 'Capitec Bank Limited', universalBranchCode: '470010' }

  beforeEach(() => {
    server.use(
      http.get('*/api/banks', () => HttpResponse.json([BANK, SECOND_BANK])),
      http.get('*/api/banks/branches', () => HttpResponse.json([BANK, SECOND_BANK])),
    )
  })

  /** Choose C1 for the first bank, and hand back the control it was chosen in. */
  async function chooseClerkForFirstBank(user) {
    renderWithProviders(<CaptureMember />)
    await chooseBank(user)

    await openGrades(user)
    await user.click(await screen.findByRole('option', { name: 'C1' }))
    return gradeCombobox()
  }

  it('keeps a grade chosen after the bank -- the must-pass half', async () => {
    // Without this, a form that cleared the grade on EVERY render would pass the test
    // below. The grades have arrived and the fetch has settled before the choice.
    const user = setupUser()
    const grade = await chooseClerkForFirstBank(user)

    await waitFor(() => expect(grade).toHaveValue('C1'))
    expect(screen.queryByText('Job Grade could not be loaded.')).not.toBeInTheDocument()
  })

  it('clears the grade when a different bank is chosen', async () => {
    const user = setupUser()
    const grade = await chooseClerkForFirstBank(user)
    expect(grade).toHaveValue('C1')

    // Through the control's own clear button: typing into the closed box APPENDS to the
    // selection it displays, so the search would read "Bidvest Bank LimitedCapitec".
    await user.click(screen.getByRole('button', { name: /^Clear Bank Where Employed/ }))
    const bankInput = (await screen.findByText(/^Bank Where Employed/)).closest('div').querySelector('input')
    await user.type(bankInput, 'Capitec')
    await user.click(await screen.findByRole('option', { name: SECOND_BANK.bankName }))

    await waitFor(() => expect(gradeCombobox()).toHaveValue(''))
  })
})

describe("when the job grades cannot be fetched -- A25’s second clause", () => {
  /**
   * The grades endpoint fails; everything else on the screen is healthy.
   *
   * 🔑 ONE OF NINE, NOT ALL NINE, AND THAT IS THE POINT (EH-D9). This screen has
   * nine mount fetches. Failing all of them would prove a page-level error state,
   * which is the thing this unit is arguing AGAINST: a bank whose grades cannot be
   * fetched must not blank a capture form the user has half filled in.
   */
  const gradesFail = (status = 500) =>
    server.use(
      http.get('*/api/job-grades', () =>
        HttpResponse.json({ message: 'Grades are unavailable.' }, { status }),
      ),
    )

  it('says the field failed, and does NOT say the bank has no grades', async () => {
    // 🔴 THIS IS THE WHOLE OF A25, AS A USER MET IT. `getByBank` ended in
    // `.catch(() => JOB_GRADE_OPTIONS)`, and that constant had been emptied, so a
    // 500 rendered as "No job grades available" -- the screen stating, confidently,
    // a fact about the member's BANK that it had no evidence for.
    //
    // Both halves are asserted, and the second is the one that would catch a
    // careless repair: it is not enough that an error appears, the empty copy must
    // be ABSENT. A component that rendered both would pass a test that only looked
    // for the error. (conventions §8, EH-D4.)
    gradesFail()
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)

    expect(await screen.findByText('Job Grade could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText('No job grades available')).not.toBeInTheDocument()
  })

  it('offers a retry, and the retry actually re-runs the fetch', async () => {
    // EH-D4: an error state with no way out is a dead end. Asserting the BUTTON
    // exists would not prove that, so this drives it: the endpoint is repaired
    // between the failure and the click, and the grades then arrive.
    gradesFail()
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)
    await screen.findByText('Job Grade could not be loaded.')

    server.use(http.get('*/api/job-grades', () => HttpResponse.json(GRADES)))
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    // The grades arrived: the field is a working combobox again, and C1 is in its list.
    await waitFor(() => expect(screen.queryByText('Job Grade could not be loaded.')).not.toBeInTheDocument())
    await openGrades(user)
    expect(await screen.findByRole('option', { name: 'C1' })).toBeInTheDocument()
  })

  it('leaves the rest of the form working -- the failure is per-panel', async () => {
    // EH-D9. The bank field is the one immediately beside the failed one and it is
    // fed by a DIFFERENT service, so if a reference-list failure were being handled
    // at page level rather than per panel this is where it would show.
    gradesFail()
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)
    await screen.findByText('Job Grade could not be loaded.')

    // The bank the user chose is still chosen, and the tabs still navigate.
    expect(screen.getByDisplayValue(BANK.bankName)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^Personal Information/ }))
    expect(await screen.findByText(/^Surname/)).toBeInTheDocument()
  })

  it('does NOT offer a retry when retrying cannot work', async () => {
    // 🔑 THE HALF THAT KEEPS THE RETRY HONEST. `06.1a` derives `retryable` once, in
    // `parseApiError`: a 4xx is the server saying the request was wrong, so retrying
    // it unchanged produces the same refusal and a button that cannot work is worse
    // than none. Without this assertion "always show a retry" would pass every other
    // test in this block.
    gradesFail(400)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await chooseBank(user)

    expect(await screen.findByText('Job Grade could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})

/**
 * 02.5 / CM-37 -- LIST FIRST, TYPING ALLOWED, AND THE SCREEN SAYS WHICH HAPPENED.
 *
 * The seeded grade list is per-bank and incomplete: SASBO's own data carries grades no bank row has,
 * so an administrator who cannot type one cannot capture the member at all. The server is already
 * built for it -- `jobGradeMatching` in `api-vocabulary.json` publishes the normalisation and the
 * MATCHED / CAPTURED / CLEARED outcomes, and `MemberDto.jobGradeOutcome` reports which one occurred.
 *
 * 🔑 THE NEAR MISS IS A PROMPT, NEVER A SUBSTITUTION. `SBG4` for `SBG04` is the realistic mistake
 * where grades are zero-padded. Silently correcting it is the failure this whole unit exists to fix:
 * a grade that appeared to save, saved as something else, and said nothing.
 *
 * ⚖️ The `allowCustom` prop this rests on was specified by the design arc, which owns
 * `SearchableSelect`, and is tested at the primitive in `SearchableSelect.test.jsx`. What is tested
 * HERE is the call site: that the grade reaches the payload, and that the near-miss prompt appears
 * without changing anything.
 */
describe('the job grade can be typed, not only chosen', () => {
  const typeGrade = async (user, text) => {
    const label = await screen.findByText(/^Job Grade/)
    const input = label.closest('div').querySelector('input')
    await user.click(input)
    await user.type(input, text)
    await user.tab()
    return input
  }

  it('🔴 a grade that is on no list survives, and is what gets posted', async () => {
    let posted
    server.use(http.post('*/api/members', async ({ request }) => {
      posted = await request.json()
      return HttpResponse.json({ id: 1, ...posted })
    }))

    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    const field = (text) => screen.getByLabelText(new RegExp(`^${text}`))

    // Personal Information first: the form renders ONE tab at a time, and `chooseBank` moves to
    // Employment & Bank -- so anything typed here has to be typed before that.
    await screen.findByLabelText(/^First Name\(s\)/)
    await user.type(field('First Name'), 'Synthetic')
    await user.type(field('Surname'), 'Testperson')
    await user.type(field('Personal Email'), 'synthetic@example.test')
    await user.selectOptions(field('Identity Type'), 'I.D. Number')
    await user.type(field('I.D. Number'), '9001010000080')

    await chooseBank(user)
    await user.selectOptions(field('Payment Type'), 'Payroll Deduction')

    // "Prokureur" is a real Old Mutual grade and is on no bank's seeded list here.
    await typeGrade(user, 'Prokureur')

    await user.click(screen.getByRole('button', { name: /save member/i }))
    const sendBtn = await screen.findByRole('button', { name: /send welcome letter/i })
    await user.click(sendBtn)
    await waitFor(() => expect(posted).toBeDefined())
    expect(posted.jobGrade).toBe('Prokureur')
  })

  it('🔑 offers the near miss as a question, and does NOT change what was typed', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseBank(user)
    await screen.findByText(/^Job Grade/)

    const input = await typeGrade(user, 'C01')

    // The prompt names the grade it suspects...
    expect(await screen.findByText(/C1/)).toBeInTheDocument()
    // ...and the field still holds exactly what the administrator typed.
    expect(input, 'a near miss that rewrites the field is the defect, not the fix').toHaveValue('C01')
  })

  it('the must-pass: a grade that IS on the list raises no near-miss prompt', async () => {
    // Without this, the prompt could be permanent markup no condition controls.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseBank(user)
    await screen.findByText(/^Job Grade/)

    await typeGrade(user, 'C1')

    expect(screen.queryByText(/Did you mean/i)).not.toBeInTheDocument()
  })

  it('and typing a grade that matches the list case-insensitively stores the LIST spelling', async () => {
    // Rule 2 of §6c: `ba2` becomes `BA2`, or the same grade exists twice depending on how it was
    // entered. The comparison ignores case; the STORED form is the list's.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseBank(user)
    await screen.findByText(/^Job Grade/)

    const input = await typeGrade(user, 'ba1')
    await waitFor(() => expect(input).toHaveValue('BA1'))
  })
})
