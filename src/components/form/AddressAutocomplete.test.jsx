// @vitest-environment jsdom -- two instances on one page is the whole point
import { describe, expect, it } from 'vitest'
import { useState } from 'react'
import { screen } from '@testing-library/react'

import AddressAutocomplete from './AddressAutocomplete'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

/**
 * ⚠️ `useMapsLibrary` is mocked to `null` in `src/test/setup.js`, so no predictions ever
 * arrive here and the listbox states are NOT covered by this file. Saying that is
 * contract ⑤: a state nothing drives is a state nothing covers, and implying otherwise is
 * worse than the gap. The suggestion list is driven live in `07.2`.
 */

describe('AddressAutocomplete — the id collision, mutation-checked', () => {
  /**
   * 🔴 **A43.** The input's id was the literal string `"address-autocomplete-input"`, and
   * **all three consumers render two of these on one page** — `Members` (Residential
   * Address, Bank Work Address), `CaptureMember` (Residential, Bank) and
   * `BankBranchModal` (Physical, Postal). So every one of them shipped two elements with
   * the same id, and `<label htmlFor>` resolves to the FIRST match in the document:
   * clicking the second field's label focused the first field's box, and a screen reader
   * announced the second field with the first field's name.
   *
   * MUTATION-CHECKED: putting a literal `id="address-autocomplete-input"` back on the
   * input in `AddressAutocomplete.jsx` turns this red, and it is the only thing that does.
   */
  it('two on one page get distinct ids, and each label focuses its OWN input', async () => {
    const user = setupUser()
    renderWithProviders(
      <div>
        <AddressAutocomplete label="Residential Address" value="" onChange={() => {}} />
        <AddressAutocomplete label="Bank Work Address" value="" onChange={() => {}} />
      </div>,
    )

    const residential = screen.getByLabelText('Residential Address')
    const bank = screen.getByLabelText('Bank Work Address')

    expect(
      residential.id,
      'Both address fields carry the same id. `label htmlFor` then resolves to whichever ' +
        'comes first in the document, so the second field is announced with the first ' +
        "field's name and its label focuses the wrong box. Every page that renders two " +
        'of these — and all three of them do — shipped this.',
    ).not.toBe(bank.id)

    await user.click(screen.getByText('Bank Work Address'))
    expect(bank).toHaveFocus()
  })
})

describe('AddressAutocomplete — the semantics the don\'t-regress row asked for', () => {
  it('is a combobox that does not claim to control a listbox that is not there', () => {
    renderWithProviders(<AddressAutocomplete label="Physical Address" value="" onChange={() => {}} />)

    const combobox = screen.getByRole('combobox', { name: 'Physical Address' })
    expect(combobox).toHaveAttribute('aria-expanded', 'false')
    expect(combobox).toHaveAttribute('aria-autocomplete', 'list')
    // 🔑 Closed, there is no listbox — so `aria-controls` must be ABSENT rather than
    // naming an element that is not in the document, which reads as broken to a screen
    // reader and identical to correct in every automated check that does not look.
    expect(combobox).not.toHaveAttribute('aria-controls')
    expect(combobox).not.toHaveAttribute('aria-activedescendant')
  })

  it('keeps the behaviour: the value flows out on every keystroke, unchanged', async () => {
    const user = setupUser()
    const seen = []
    renderWithProviders(
      <AddressAutocomplete label="Postal Address" value="" onChange={(e) => seen.push(e.target.value)} />,
    )

    await user.type(screen.getByRole('combobox', { name: 'Postal Address' }), 'ab')
    // The old component propagated the FULL field value on every keystroke — not the
    // character typed — and three consumers pass `handleChange("bankAddress")` straight
    // into form state on that basis. This restyle must not have changed it.
    expect(seen).toEqual(['a', 'ab'])
  })

  it('invalid and disabled are wired the same way as every other control', () => {
    const { unmount } = renderWithProviders(
      <AddressAutocomplete label="Bank Address" value="" onChange={() => {}} error="Required" />,
    )
    const combobox = screen.getByRole('combobox', { name: 'Bank Address' })
    expect(combobox).toHaveAttribute('aria-invalid', 'true')
    expect(document.getElementById(combobox.getAttribute('aria-describedby'))).toHaveTextContent('Required')
    unmount()

    renderWithProviders(<AddressAutocomplete label="Bank Address" value="" onChange={() => {}} disabled />)
    expect(screen.getByRole('combobox', { name: 'Bank Address' })).toBeDisabled()
  })
})

describe('AddressAutocomplete — accessibility and the 16px rule', () => {
  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <div>
        <AddressAutocomplete label="Residential Address" value="12 Long Street" onChange={() => {}} />
        <AddressAutocomplete label="Postal Address" value="" onChange={() => {}} error="Required" />
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

  it('wears field-text — this was one of the three wrappers F-D01 measured at 14px', () => {
    renderWithProviders(<AddressAutocomplete label="Bank Address" value="" onChange={() => {}} />)
    const combobox = screen.getByRole('combobox', { name: 'Bank Address' })
    expect(combobox.className).toMatch(/\bfield-text\b/)
    expect(combobox.className).not.toMatch(/\btext-(xs|sm|2xs)\b/)
  })
})

describe('AddressAutocomplete — a value the PARENT changes reaches the field', () => {
  /*
   * 🔴 THE INBOUND HALF, NAKED UNTIL `10.4`. The outbound half has a test above ("the value flows
   * out on every keystroke"); nothing asserted the other direction -- that when the FORM changes
   * the value (a reset, a record loaded into it), the field shows it. It is done by a sync from
   * `value` into the component's own `query`, which was an effect carrying an
   * `eslint-disable-next-line react-hooks/exhaustive-deps` and which `set-state-in-effect` makes
   * an error this phase. These three hold the behaviour so it can be moved; the deletion proof is
   * that removing the sync outright turns the first two red.
   */
  function Controlled({ external }) {
    const [value, setValue] = useState('')
    return (
      <div>
        <AddressAutocomplete label="Residential Address" value={value} onChange={(event) => setValue(event.target.value)} />
        <button type="button" onClick={() => setValue(external)}>
          Load from elsewhere
        </button>
      </div>
    )
  }

  it('a reset from the form empties what was typed', async () => {
    const user = setupUser()
    renderWithProviders(<Controlled external="" />)
    const field = screen.getByRole('combobox', { name: 'Residential Address' })

    await user.type(field, '12 Main')
    await user.click(screen.getByRole('button', { name: 'Load from elsewhere' }))

    expect(field).toHaveValue('')
  })

  it('a value loaded from elsewhere replaces what was typed', async () => {
    const user = setupUser()
    renderWithProviders(<Controlled external="7 Imported Road, Durban" />)
    const field = screen.getByRole('combobox', { name: 'Residential Address' })

    await user.type(field, '12 Main')
    await user.click(screen.getByRole('button', { name: 'Load from elsewhere' }))

    expect(field).toHaveValue('7 Imported Road, Durban')
  })

  it('the must-pass: the sync never fights the keyboard -- typing arrives whole', async () => {
    // 🔑 THE FAILURE MODE OF A CARELESS SYNC. The parent echoes every keystroke back as `value`,
    // so a sync that re-applied `value` without checking it against what is already typed would
    // race the keyboard and drop or reorder characters. Every character must survive.
    const user = setupUser()
    renderWithProviders(<Controlled external="" />)
    const field = screen.getByRole('combobox', { name: 'Residential Address' })

    await user.type(field, '14 Synthetic Avenue')

    expect(field).toHaveValue('14 Synthetic Avenue')
  })
})
