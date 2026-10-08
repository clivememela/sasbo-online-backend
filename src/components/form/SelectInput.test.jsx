// @vitest-environment jsdom -- renders a real select and drives it with a keyboard
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'

import SelectInput from './SelectInput'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

const TITLES = [
  { value: 'mr', label: 'Mr' },
  { value: 'ms', label: 'Ms' },
  { value: 'dr', label: 'Dr' },
]

/** Real records rather than {value,label} — the shape the Job Grade field actually has. */
const GRADES = [
  { code: 'A1', description: 'Clerk', bank: 'Absa' },
  { code: 'B2', description: 'Supervisor', bank: 'Absa' },
]

describe('SelectInput — the states', () => {
  it('default: a real label, associated, with no aria-label competing with it', () => {
    renderWithProviders(<SelectInput label="Title" value="" onChange={() => {}} options={TITLES} />)
    const select = screen.getByLabelText('Title')
    expect(select.tagName).toBe('SELECT')
    expect(select).not.toHaveAttribute('aria-label')
  })

  it('loading: says so, disables itself, and does NOT render as an empty list', () => {
    renderWithProviders(<SelectInput label="Bank" value="" onChange={() => {}} options={[]} loading />)
    const select = screen.getByLabelText('Bank')

    expect(select).toBeDisabled()
    expect(select).toHaveAttribute('aria-busy', 'true')
    // 🔴 conventions §8 one control down: "we do not know yet" and "there is nothing"
    // are different facts, and a select that renders empty while fetching reports the
    // wrong one — A27's exact shape.
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByText('No options available')).toBeNull()
  })

  it('empty: says there are none, rather than opening onto nothing', () => {
    renderWithProviders(<SelectInput label="Bank" value="" onChange={() => {}} options={[]} />)
    expect(screen.getByLabelText('Bank')).toBeDisabled()
    expect(screen.getByText('No options available')).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).toBeNull()
  })

  it('failed: the CHOICES are unavailable -- a retry is offered, and it does NOT read as an empty list (F-P54)', async () => {
    // 🔴 THE SHAPE `06.4` EXISTS FOR. Before it, a list that failed reached this control as an empty
    // one, and "No options available" told an administrator the bank had none -- so they picked the
    // closest one they could see. The "empty" case above is this test's must-pass: same `options={[]}`,
    // and only `loadError` tells the two apart.
    const user = setupUser()
    const retry = vi.fn()
    renderWithProviders(
      <SelectInput label="Bank" value="" onChange={() => {}} options={[]} loadError="The banks list could not be loaded." onRetryLoad={retry} />,
    )

    expect(screen.getByLabelText('Bank')).toBeDisabled()
    expect(screen.getByText('Unavailable')).toBeInTheDocument()
    expect(screen.getByText('Bank could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText('No options available')).toBeNull()

    await user.click(screen.getByRole('button', { name: /try again/i }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('invalid: aria-invalid plus an announced message whose id resolves', () => {
    renderWithProviders(
      <SelectInput label="Title" value="" onChange={() => {}} options={TITLES} error="Choose a title" />,
    )
    const select = screen.getByLabelText('Title')
    expect(select).toHaveAttribute('aria-invalid', 'true')
    expect(document.getElementById(select.getAttribute('aria-describedby'))).not.toBeNull()
    expect(screen.getByRole('alert')).toHaveTextContent('Choose a title')
  })

  it('disabled: refuses selection', () => {
    renderWithProviders(<SelectInput label="Title" value="" onChange={() => {}} options={TITLES} disabled />)
    expect(screen.getByLabelText('Title')).toBeDisabled()
  })
})

describe('SelectInput — the keyboard', () => {
  it('Tab reaches it and a selection reaches onChange with the value AND the record', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <SelectInput
        label="Job Grade"
        value=""
        onChange={onChange}
        options={GRADES}
        optionValue="code"
        optionLabel={(grade) => `${grade.code} — ${grade.description}`}
      />,
    )

    await user.tab()
    const select = screen.getByLabelText('Job Grade')
    expect(select).toHaveFocus()

    await user.selectOptions(select, 'B2')

    // 🔴 THE RECORD COMES BACK, not just the string. A shared select that can only hand
    // back a value forces every caller holding real records to search its own list
    // again — which is what made the Job Grade field unable to consume the shared
    // primitive in the first place (BR-4).
    expect(onChange).toHaveBeenCalledWith('B2', GRADES[1])
  })

  it('a computed label renders, so a record with no `label` field is still usable', () => {
    renderWithProviders(
      <SelectInput
        label="Job Grade"
        value="A1"
        onChange={() => {}}
        options={GRADES}
        optionValue="code"
        optionLabel={(grade) => `${grade.code} — ${grade.description}`}
      />,
    )
    expect(screen.getByRole('option', { name: 'A1 — Clerk' })).toBeInTheDocument()
  })

  it('a numeric id round-trips instead of silently showing the placeholder', () => {
    // 🔑 A native <option value> is a string, so `7 === "7"` is false and a select given
    // a numeric id shows the placeholder while holding a perfectly good selection. One
    // coercion in `options.js`, rather than a Number() each call site remembers.
    renderWithProviders(
      <SelectInput
        label="Area"
        value={7}
        onChange={() => {}}
        options={[{ id: 7, name: 'Gauteng North' }]}
        optionValue="id"
        optionLabel="name"
      />,
    )
    expect(screen.getByLabelText('Area')).toHaveValue('7')
  })
})

describe('SelectInput — accessibility, both themes', () => {
  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <div>
        <SelectInput label="Title" value="mr" onChange={() => {}} options={TITLES} required />
        <SelectInput label="Bank" value="" onChange={() => {}} options={[]} loading />
        <SelectInput label="Race" value="" onChange={() => {}} options={TITLES} error="Required" />
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

describe('SelectInput — the 16px rule, mutation-checked', () => {
  // The same guard as TextInput's, on the control the F-D01 measurement actually found
  // broken: the 74 sub-16px controls came from `text-sm` on a WRAPPER in three shared
  // primitives, not from the tag. See TextInput.test.jsx for why this asserts the
  // mechanism and the browser proves the pixels.
  // `07.2`: a <select> is a replaced element and draws no ::after, so `touch-target` gives it nothing. See
  // TextInput.test.jsx for the rule; the browser half is tools/surfaces.mjs.
  it('the select wears touch-size, so a coarse pointer gets a 44px target', () => {
    renderWithProviders(<SelectInput label="Title" value="" onChange={() => {}} options={TITLES} />)
    expect(screen.getByLabelText('Title').className).toMatch(/\btouch-size\b/)
  })

  it('the select wears field-text, and no smaller type overrides it', () => {
    renderWithProviders(<SelectInput label="Title" value="" onChange={() => {}} options={TITLES} />)
    const select = screen.getByLabelText('Title')
    expect(select.className).toMatch(/\bfield-text\b/)
    expect(select.className).not.toMatch(/\btext-(xs|sm|2xs)\b/)
  })
})
