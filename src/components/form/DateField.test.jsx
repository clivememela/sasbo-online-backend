// @vitest-environment jsdom -- opens a real calendar dialog and drives it with a keyboard
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'

import DateField from './DateField'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

/**
 * ⚠️ **`user.tab()` DOES NOT LEAVE AN OPEN PICKER, AND THAT IS THE CONTRACT.** The mock's
 * tab order is *"the field's input → the calendar's controls → the next field after this
 * one"*, so Tab from the box lands on **Previous month**. The first draft of this file
 * assumed Tab would blur the field and normalise it; nine tests went red and **the
 * component was right**. Where a test needs the field to be LEFT, it presses Escape first
 * — which is what a user does — and the normalisation is asserted there.
 *
 * 🔒 THIS FILE IS THE MOCK'S CLAUSES, ONE ASSERTION EACH.
 * `mockups/design/datepicker.html` is the owner-approved behaviour contract, and `03.3`'s
 * DoD is that `DateField` matches it **clause by clause, checked side by side, not from
 * memory**. Each `describe` below names the clause it is checking.
 *
 * ⚠️ `src/test/setup.js` stubs `window.matchMedia` to always return `matches: false`, so
 * every test here renders the DESKTOP POPOVER unless it overrides the stub. The sheet
 * block does exactly that, locally, and restores it — the harness file belongs to the
 * automation arc and this arc does not edit it.
 */

const restoreMatchMedia = []

afterEach(() => {
  while (restoreMatchMedia.length) restoreMatchMedia.pop()()
})

/** Force the sheet branch on, for the ≤760px clauses. */
function useSheetViewport() {
  const original = window.matchMedia
  window.matchMedia = (query) => ({
    matches: true,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
  restoreMatchMedia.push(() => {
    window.matchMedia = original
  })
}

describe('DateField — "the whole field is the trigger" (the owner\'s original complaint)', () => {
  it('clicking the TEXT BOX opens the calendar, not only the icon', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="" onChange={() => {}} hint="age" />)

    const field = screen.getByRole('combobox', { name: /date of birth/i })
    expect(field).toHaveAttribute('aria-expanded', 'false')

    // 🔴 THE CLAUSE. The control this replaces opened only from its icon, and that is the
    // complaint that produced the approved mock in the first place.
    await user.click(field)
    expect(field).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('dialog', { name: /choose date of birth/i })).toBeInTheDocument()
  })

  it('clicking the field AGAIN closes it: the whole field is a toggle', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="" onChange={() => {}} />)

    const field = screen.getByRole('combobox', { name: /date of birth/i })
    await user.click(field)
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.click(field)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(field).toHaveAttribute('aria-expanded', 'false')
  })

  it('nothing can be typed into it: the date is chosen, never keyed in', async () => {
    // 🗣️ The owner, 2026-09-10: "I don't really want to allow dates to be typed in
    // manually … You have to choose the date from the picker and not type it in."
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<DateField label="Date of Birth" value="" onChange={onChange} />)

    const field = screen.getByRole('combobox', { name: /date of birth/i })
    expect(field).toHaveAttribute('readonly')

    await user.click(field)
    await user.keyboard('1988/06/15')

    expect(field).toHaveValue('')
    expect(onChange).not.toHaveBeenCalled()
  })
})

describe('DateField — "it does not change what is submitted"', () => {
  it('emits yyyy-mm-dd in a NATIVE-SHAPED event when a day is chosen, while showing yyyy/mm/dd', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <DateField label="Date Joined" name="joinDate" value="1988-06-01" onChange={onChange} />,
    )

    const field = screen.getByRole('combobox', { name: /date joined/i })
    await user.click(field)
    await user.click(screen.getByRole('gridcell', { name: '15 Jun 1988' }))

    // 🔴 The ten native `<input type="date">` this replaces hand their callers
    // `yyyy-mm-dd` through `e.target.value`. A call site written as
    // `onChange={handleChange("joinDate")}` must need NO edit — so the event is
    // native-shaped and the wire value is unchanged.
    const last = onChange.mock.calls.at(-1)[0]
    expect(last.target.value).toBe('1988-06-15')
    expect(last.target.name).toBe('joinDate')

    // And what the user reads is the app's own form; choosing closes the calendar.
    expect(field).toHaveValue('1988/06/15')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('clearing emits an empty string, exactly as a native date input does', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<DateField label="Date Joined" value="1988-06-15" onChange={onChange} />)

    await user.click(screen.getByRole('combobox', { name: /date joined/i }))
    await user.click(screen.getByRole('button', { name: 'Clear' }))

    expect(onChange.mock.calls.at(-1)[0].target.value).toBe('')
  })
})

describe('DateField — the bounds are enforced in the calendar itself', () => {
  // The four typed forms the mock names are `parseDate`'s, and `format.test.js` holds
  // them; since the field became choose-only (2026-09-10) nothing typed ever reaches it,
  // so a refusal sentence under the field has nothing to refuse. What remains of the
  // clause is the bound: a day outside `min`/`max` is a day that cannot be chosen.
  it('a day past the bound is disabled, and clicking it chooses nothing', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <DateField label="Date of Birth" value="1988-06-01" onChange={onChange} max="1988-06-10" hint="age" />,
    )

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    const late = screen.getByRole('gridcell', { name: '15 Jun 1988' })
    expect(late).toHaveAttribute('aria-disabled', 'true')

    await user.click(late)
    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    // ...and a day inside it still can be. (The cell's name is the long form, day padded.)
    await user.click(screen.getByRole('gridcell', { name: '08 Jun 1988' }))
    expect(onChange.mock.calls.at(-1)[0].target.value).toBe('1988-06-08')
  })
})

describe('DateField — the month grid and the 20-year grid, one tap each', () => {
  it('the month name opens a GRID of months, not a dropdown', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))

    const monthButton = screen.getByRole('button', { name: /^June$/ })
    expect(monthButton).toHaveAttribute('aria-expanded', 'false')
    await user.click(monthButton)

    // 🔒 The mock is explicit: "month grid and 20-year grid, one tap each". A native
    // <select> would be a deviation from an owner-approved artifact — and on a phone a
    // birth year is 40 scrolls down a select against one tap in a grid.
    const monthList = screen.getByRole('listbox', { name: 'Month' })
    expect(within(monthList).getAllByRole('option')).toHaveLength(12)

    await user.click(within(monthList).getByRole('option', { name: 'Mar' }))
    expect(screen.getByRole('button', { name: /^March$/ })).toBeInTheDocument()
  })

  it('the year opens a grid of exactly 20 years', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    await user.click(screen.getByRole('button', { name: '1988' }))

    const yearList = screen.getByRole('listbox', { name: 'Year' })
    const years = within(yearList).getAllByRole('option')
    expect(years).toHaveLength(20)
    expect(years[0]).toHaveTextContent('1980')
    expect(years[19]).toHaveTextContent('1999')

    // Choosing a year drops to the MONTH grid, so the cascade is year → month → day.
    await user.click(within(yearList).getByRole('option', { name: '1985' }))
    expect(screen.getByRole('listbox', { name: 'Month' })).toBeInTheDocument()
  })
})

describe('DateField — the readout carries the age, or how long ago', () => {
  it('a birth date reads as an age', async () => {
    const user = setupUser()
    const thisYear = new Date().getFullYear()
    renderWithProviders(
      <DateField label="Date of Birth" value={`${thisYear - 38}-01-01`} onChange={() => {}} hint="age" />,
    )

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent(/\d+ years old/)
  })

  it('everything else reads as how long ago', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Visit" value="2020-01-01" onChange={() => {}} hint="ago" />)

    await user.click(screen.getByRole('combobox', { name: /date of visit/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent(/years ago/)
  })

  it('with no date it says so, rather than showing an empty line', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Visit" value="" onChange={() => {}} />)

    await user.click(screen.getByRole('combobox', { name: /date of visit/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('No date yet')
  })
})

describe('DateField — Today and Clear', () => {
  it('Today picks today and closes', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<DateField label="Date Captured" value="" onChange={onChange} />)

    await user.click(screen.getByRole('combobox', { name: /date captured/i }))
    await user.click(screen.getByRole('button', { name: 'Today' }))

    const now = new Date()
    const expected = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(onChange.mock.calls.at(-1)[0].target.value).toBe(expected)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('Today on a field where today is out of range NAVIGATES rather than choosing', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <DateField label="Future date" value="" onChange={onChange} min="2099-01-01" />,
    )

    await user.click(screen.getByRole('combobox', { name: /future date/i }))
    await user.click(screen.getByRole('button', { name: 'Today' }))

    // It still orients you, which is what the button is for — but it cannot choose an
    // answer the field would then refuse.
    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('DateField — the keyboard', () => {
  it('ArrowDown opens it, Escape closes it and returns focus to the field', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)

    await user.keyboard('{Escape}')
    await user.tab()
    const field = screen.getByRole('combobox', { name: /date of birth/i })
    expect(field).toHaveFocus()

    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(
      field,
      'Escape closed the calendar and dropped focus. A keyboard user is now at the top of ' +
        'the document with no idea where they were.',
    ).toHaveFocus()
  })

  it('Enter toggles the calendar and does NOT submit the form', async () => {
    const user = setupUser()
    const onSubmit = vi.fn((event) => event.preventDefault())
    const onChange = vi.fn()

    renderWithProviders(
      <form onSubmit={onSubmit}>
        <DateField label="Date of Birth" value="" onChange={onChange} />
      </form>,
    )

    await user.keyboard('{Escape}')
    await user.tab()
    const field = screen.getByRole('combobox', { name: /date of birth/i })
    expect(field).toHaveFocus()

    await user.keyboard('{Enter}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.keyboard('{Enter}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(onSubmit).not.toHaveBeenCalled()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('is an ARIA grid with exactly ONE tab stop, not 42', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    const grid = screen.getByRole('grid', { name: 'June 1988' })
    const cells = within(grid).getAllByRole('gridcell')

    // Six weeks of seven, always — the panel must not change height as you page months.
    expect(cells).toHaveLength(42)

    // 🔑 Roving focus. A grid where every day is a tab stop puts 42 tab stops between a
    // keyboard user and the next field on the form.
    const tabbable = cells.filter((cell) => cell.getAttribute('tabindex') === '0')
    expect(tabbable).toHaveLength(1)
    expect(tabbable[0]).toHaveAccessibleName(/15 Jun 1988/)
  })
})

/**
 * 🔴 THE KEYBOARD MODEL IS OURS NOW, so it is tested hardest.
 *
 * Until the measurement in F-P16 removed `react-day-picker`, the arrow/Home/End/PageUp
 * model came from the library and these bindings were somebody else's problem. They are
 * this file's problem now, and every binding below is the approved mock's, taken from
 * `datepicker.js` → `onPopKey` rather than from memory.
 */
describe('DateField — the full keyboard set the mock specifies', () => {
  /** Open at 15 June 1988 with focus on the 15th, and hand back the focused cell reader. */
  async function openAt15June() {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    await user.keyboard('{ArrowDown}') // into the grid, onto the focused day
    return { user, focused: () => document.activeElement?.getAttribute('aria-label') }
  }

  it('ArrowDown from the field lands ON the selected day, not on the first of the month', async () => {
    const { focused } = await openAt15June()
    expect(focused()).toMatch(/15 Jun 1988/)
  })

  it.each([
    ['{ArrowLeft}', /14 Jun 1988/],
    ['{ArrowRight}', /16 Jun 1988/],
    ['{ArrowUp}', /08 Jun 1988/],
    ['{ArrowDown}', /22 Jun 1988/],
    ['{Home}', /12 Jun 1988/],
    ['{End}', /18 Jun 1988/],
    ['{PageUp}', /15 May 1988/],
    ['{PageDown}', /15 Jul 1988/],
  ])('%s moves focus to %s', async (key, expected) => {
    const { user, focused } = await openAt15June()
    await user.keyboard(key)
    expect(focused()).toMatch(expected)
  })

  it('Shift+PageUp and Shift+PageDown move a YEAR', async () => {
    const { user, focused } = await openAt15June()
    await user.keyboard('{Shift>}{PageUp}{/Shift}')
    expect(focused()).toMatch(/15 Jun 1987/)
    await user.keyboard('{Shift>}{PageDown}{/Shift}')
    expect(focused()).toMatch(/15 Jun 1988/)
  })

  it('moving past the end of the month brings the month with it', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-30" onChange={() => {}} />)
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    await user.keyboard('{ArrowDown}{ArrowRight}')

    // The focused day must always be one you can SEE. A grid that scrolls focus off the
    // visible month is a grid the arrows appear to have broken.
    expect(document.activeElement?.getAttribute('aria-label')).toMatch(/01 Jul 1988/)
    expect(screen.getByRole('grid', { name: 'July 1988' })).toBeInTheDocument()
  })

  it('moving around does NOT change the answer — only Enter does', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={onChange} />)
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))

    await user.keyboard('{ArrowDown}{ArrowRight}{ArrowRight}{ArrowDown}')
    // 🔴 Browsing a calendar must not edit a member's date of birth. Focus and selection
    // are separate pieces of state precisely so that this cannot happen.
    expect(onChange).not.toHaveBeenCalled()

    await user.keyboard('{Enter}')
    expect(onChange.mock.calls.at(-1)[0].target.value).toBe('1988-06-24')
  })

  it('Space selects, as well as Enter', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={onChange} />)
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    await user.keyboard('{ArrowDown}{ArrowLeft} ')
    expect(onChange.mock.calls.at(-1)[0].target.value).toBe('1988-06-14')
  })

  it('a day outside the allowed range is announced disabled and refuses Enter', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <DateField label="Date of Birth" value="1988-06-15" onChange={onChange} min="1988-06-15" />,
    )
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    await user.keyboard('{ArrowDown}{ArrowLeft}')

    expect(document.activeElement).toHaveAttribute('aria-disabled', 'true')
    await user.keyboard('{Enter}')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('today is RINGED and the selection is FILLED — two different marks', async () => {
    const user = setupUser()
    const now = new Date()
    const iso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    renderWithProviders(<DateField label="Date of Visit" value={iso} onChange={() => {}} />)
    await user.click(screen.getByRole('combobox', { name: /date of visit/i }))

    // The mock is explicit that they are different marks, so that today still reads as
    // today on the day it is also the selection.
    const cell = screen.getByRole('gridcell', { name: new RegExp(', today') })
    // The class, because jsdom cannot see a colour. That it resolves to Card's gold rather than the
    // surface wash `ring-accent` became is `tools/review.mjs`'s TODAY_RING_GOLD, read in a browser (`06.3`).
    expect(cell.className).toMatch(/\bring-accent-line\b/)
    expect(cell.className).toMatch(/bg-brand/)
  })
})

describe('DateField — the bottom sheet at ≤760px', () => {
  it('becomes a modal sheet with a Done button, and locks the page behind it', async () => {
    useSheetViewport()
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)

    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))

    const dialog = screen.getByRole('dialog')
    // 🔴 `aria-modal` is TRUE only as a sheet. As a popover it is false, because the field
    // behind it is still usable — you can keep typing. Claiming modality that does not
    // exist tells a screen reader the rest of the page is gone when it is not.
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(within(dialog).getByRole('button', { name: 'Done' })).toBeInTheDocument()
    expect(document.body.style.overflow).toBe('hidden')

    await user.click(within(dialog).getByRole('button', { name: 'Done' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.body.style.overflow).not.toBe('hidden')
  })

  it('as a popover it does NOT claim to be modal', async () => {
    const user = setupUser()
    renderWithProviders(<DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} />)
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'false')
  })
})

describe('DateField — accessibility, both themes', () => {
  it('has no violations closed OR open, in light and dark', async () => {
    const user = setupUser()
    const { container } = renderWithProviders(
      <DateField label="Date of Birth" value="1988-06-15" onChange={() => {}} hint="age" max="today" />,
    )

    await expectNoA11yViolations(container)

    // ⚠️ Contract ⑤: the calendar is a state-gated surface. Scanning only the closed field
    // would never scan the dialog, the grid, or a single day cell.
    await user.click(screen.getByRole('combobox', { name: /date of birth/i }))
    const dialog = screen.getByRole('dialog')

    // 🔴 SCOPED TO THE DIALOG, NOT `document.body` -- and that is an anti-flake fix, not a
    // tidy-up. The first draft scanned the whole body, because the calendar is portalled
    // out of `container`. Run alone it took 626ms; run inside the shuffled suite it took
    // **20.4 SECONDS and hit the per-test deadline** -- a test whose cost depends on what
    // else the document happens to hold, which is the defining shape of a flake. Scanning
    // the dialog subtree measures the same surface and cannot be affected by anything
    // outside it.
    //
    // ⚠️ `region` is off, and only here, with the reason stated rather than the rule
    // quietly dropped: a primitive rendered on its own is not a page, so axe correctly
    // reports content outside a landmark. The landmarks are the shell's, and
    // `src/test/a11y.test.jsx` asserts them at page level where the claim means something.
    const openScan = { region: { enabled: false } }
    await expectNoA11yViolations(dialog, { rules: openScan })

    document.documentElement.classList.add('dark')
    try {
      await expectNoA11yViolations(dialog, { rules: openScan })
    } finally {
      document.documentElement.classList.remove('dark')
    }
  })
})

describe('DateField — the 16px rule, mutation-checked', () => {
  it('the field wears field-text, and no smaller type overrides it', () => {
    renderWithProviders(<DateField label="Date of Birth" value="" onChange={() => {}} />)
    const field = screen.getByRole('combobox', { name: /date of birth/i })
    expect(field.className).toMatch(/\bfield-text\b/)
    expect(field.className).not.toMatch(/\btext-(xs|sm|2xs)\b/)
  })

  it('is not a native date input, which is the thing this unit removes', () => {
    renderWithProviders(<DateField label="Date of Birth" value="" onChange={() => {}} />)
    const field = screen.getByRole('combobox', { name: /date of birth/i })
    // 🔴 `type="date"` renders the platform's own picker, which is the control the owner
    // complained about and which cannot carry the mock's readout or grids. Since the field
    // became choose-only it is a read-only text box: no keyboard hint, because nothing is
    // ever typed into it.
    expect(field).toHaveAttribute('type', 'text')
    expect(field).toHaveAttribute('readonly')
    expect(field).not.toHaveAttribute('inputmode')
  })
})
