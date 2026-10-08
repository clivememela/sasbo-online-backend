// @vitest-environment jsdom -- renders a real input and types into it
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'

import TextInput from './TextInput'
import { FIELD_KINDS, attributesForKind } from './fieldKinds'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

describe('TextInput — the states', () => {
  it('default: a real label, associated, and no competing aria-label', () => {
    renderWithProviders(<TextInput label="Membership Number" value="" onChange={() => {}} />)

    // 🔑 Found by its LABEL, which is the point. The old primitive put an `aria-label`
    // on the control beside the visible label; when the two disagree the accessible
    // name silently wins over the text a sighted user reads.
    const input = screen.getByLabelText(/membership number/i)
    expect(input.tagName).toBe('INPUT')
    expect(
      input,
      'The control carries its own aria-label beside a visible label. Two names for ' +
        'one field is one name too many, and the invisible one wins.',
    ).not.toHaveAttribute('aria-label')
  })

  it('invalid: aria-invalid, a described-by that resolves, and an announced message', () => {
    renderWithProviders(
      <TextInput label="Email" kind="email" value="nope" onChange={() => {}} error="Enter a valid email address" />,
    )

    const input = screen.getByLabelText(/email/i)
    expect(input).toHaveAttribute('aria-invalid', 'true')

    const describedBy = input.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    // 🔴 The id must RESOLVE. An aria-describedby naming an element that is not in the
    // document makes the whole description silently empty in several screen readers —
    // which looks identical, in every automated check, to a description that works.
    expect(document.getElementById(describedBy)).not.toBeNull()
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid email address')
  })

  it('the hint is suppressed while an error shows, and is not left dangling in describedby', () => {
    const { rerender } = renderWithProviders(
      <TextInput label="Salary" kind="money" value="" onChange={() => {}} hint="Monthly, before deductions" />,
    )
    let input = screen.getByLabelText(/salary/i)
    expect(document.getElementById(input.getAttribute('aria-describedby'))).toHaveTextContent(
      'Monthly, before deductions',
    )

    rerender(
      <TextInput label="Salary" kind="money" value="" onChange={() => {}} hint="Monthly, before deductions" error="Required" />,
    )
    input = screen.getByLabelText(/salary/i)
    expect(screen.queryByText('Monthly, before deductions')).toBeNull()
    expect(document.getElementById(input.getAttribute('aria-describedby'))).toHaveTextContent('Required')
  })

  it('disabled: refuses input', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<TextInput label="Locked" value="" onChange={onChange} disabled />)

    const input = screen.getByLabelText(/locked/i)
    expect(input).toBeDisabled()
    await user.type(input, 'abc')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('required: marks the control and announces the requirement in words', () => {
    renderWithProviders(<TextInput label="Surname" kind="name" value="" onChange={() => {}} required />)
    expect(screen.getByLabelText(/surname/i)).toBeRequired()
    // The asterisk is aria-hidden; "(required)" is what is actually announced, because
    // a screen reader reads "star" or nothing at all for a bare `*`.
    expect(screen.getByText('(required)')).toBeInTheDocument()
  })

  it('multiline renders a textarea and drops the kind attributes rather than ignoring them', () => {
    renderWithProviders(<TextInput label="Remarks" value="" onChange={() => {}} multiline />)
    const control = screen.getByLabelText(/remarks/i)
    expect(control.tagName).toBe('TEXTAREA')
    expect(control).not.toHaveAttribute('inputmode')
  })
})

describe('TextInput — the keyboard', () => {
  it('Tab reaches it and every keystroke reaches onChange', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(<TextInput label="Initials" value="" onChange={onChange} />)

    await user.tab()
    expect(screen.getByLabelText(/initials/i)).toHaveFocus()

    await user.keyboard('abc')
    expect(onChange).toHaveBeenCalledTimes(3)
  })
})

describe('TextInput — accessibility, both themes', () => {
  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <div>
        <TextInput label="Name" kind="name" value="" onChange={() => {}} required />
        <TextInput label="Identity Number" kind="identifier" value="" onChange={() => {}} hint="13 digits" />
        <TextInput label="Email" kind="email" value="x" onChange={() => {}} error="Not an email address" />
        <TextInput label="Disabled" value="" onChange={() => {}} disabled />
      </div>,
    )

    await expectNoA11yViolations(container)

    document.documentElement.classList.add('dark')
    try {
      await expectNoA11yViolations(container)
    } finally {
      document.documentElement.classList.remove('dark')
    }
  })
})

describe('TextInput — the 16px rule, mutation-checked', () => {
  /**
   * 🔴 THE CONVENTION THIS PRIMITIVE EXISTS TO MAKE UNFORGETTABLE (conventions §5,
   * F-D01). iOS Safari zooms the page when a focused control is under 16px, and **74 of
   * this application's 89 form controls render under 16px today**. Nothing errors when
   * it happens — the page just lurches and the rest of the form goes off-screen — so
   * this is the assertion standing between the rule and a bug that only appears on a
   * real iPhone.
   *
   * ⚠️ **THIS TEST CANNOT MEASURE PIXELS AND DOES NOT PRETEND TO.** jsdom has no layout
   * engine; `getComputedStyle(input).fontSize` here returns the empty string no matter
   * what the stylesheet says. What it asserts is the *mechanism*: the control wears
   * `field-text`, the utility that resolves to 16px under `@media (max-width: 760px),
   * (pointer: coarse)`, and does NOT wear a `text-sm`/`text-xs` that would override it.
   * The pixels are measured in a real browser by
   * `mockups/design/tools/prove_field_text.mjs`. Two halves of one guard, and saying
   * which half is which is the difference between coverage and the appearance of it.
   *
   * MUTATION-CHECKED: replacing `field-text` with `text-sm` in `Field.jsx`'s
   * `controlClasses` turns this red and nothing else.
   */
  it('every control wears field-text, and no smaller type overrides it', () => {
    renderWithProviders(
      <div>
        <TextInput label="One" value="" onChange={() => {}} />
        <TextInput label="Two" value="" onChange={() => {}} multiline />
      </div>,
    )

    for (const label of ['One', 'Two']) {
      const control = screen.getByLabelText(label)
      expect(
        control.className,
        `"${label}" does not carry \`field-text\`. Under this design's scale \`text-sm\` ` +
          `is 13px, and iOS zooms the whole page when a control under 16px takes focus.`,
      ).toMatch(/\bfield-text\b/)

      expect(
        control.className,
        `"${label}" carries a type utility smaller than \`field-text\`, which overrides ` +
          `it and reintroduces the zoom.`,
      ).not.toMatch(/\btext-(xs|sm|2xs)\b/)
    }
  })

  /**
   * 🔴 THE 44px RULE, FOR A CONTROL THAT CANNOT CARRY `touch-target` (`07.2`). That utility grows a hit area with an
   * `::after` overlay, and an input or a textarea is a REPLACED element: it draws no `::after`. Measured under real
   * touch emulation, every text field in the application offered a 38px target while wearing `touch-target`.
   * `touch-size` grows the control itself to 44px on a coarse pointer, and only there.
   *
   * The same two halves as the 16px rule above: this asserts the mechanism; `tools/surfaces.mjs` measures the target
   * a finger gets, in a browser, under `Emulation.setTouchEmulationEnabled`.
   */
  it('every control wears touch-size, so a coarse pointer gets a 44px target', () => {
    renderWithProviders(
      <div>
        <TextInput label="One" value="" onChange={() => {}} />
        <TextInput label="Two" value="" onChange={() => {}} multiline />
      </div>,
    )

    for (const label of ['One', 'Two']) {
      expect(
        screen.getByLabelText(label).className,
        `"${label}" does not carry \`touch-size\`. An input draws no ::after, so \`touch-target\` cannot give it a ` +
          `44px target on a phone.`,
      ).toMatch(/\btouch-size\b/)
    }
  })

  it('a kind sets the keyboard, the autofill and the return key together — or throws', () => {
    // The other half of the same convention: `fieldKinds` refuses an unknown kind rather
    // than degrading to plain text, because a typo'd kind that silently behaves like
    // `text` is exactly the failure the module exists to stop and would never be noticed.
    expect(() => attributesForKind('idnetifier')).toThrow(/Unknown field kind/)
    expect(FIELD_KINDS.length).toBeGreaterThan(5)

    renderWithProviders(<TextInput label="ID" kind="identifier" value="" onChange={() => {}} />)
    const input = screen.getByLabelText('ID')
    expect(input).toHaveAttribute('inputmode', 'numeric')
    expect(input).toHaveAttribute('autocomplete', 'off')
    expect(input).toHaveAttribute('enterkeyhint', 'next')
    // 🔑 NOT type="number": spinners, silent scroll-wheel edits, and '' for anything
    // unparseable. See fieldKinds.js.
    expect(input).toHaveAttribute('type', 'text')
  })
})
