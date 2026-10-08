// @vitest-environment jsdom -- drives a cascade of real selects
import { describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { screen } from '@testing-library/react'

import PositionTreeSelect from './PositionTreeSelect'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

const TREE = [
  {
    id: 1,
    name: 'Gauteng Region',
    children: [
      { id: 11, name: 'Johannesburg Branch', children: [{ id: 111, name: 'Chairperson' }] },
      { id: 12, name: 'Pretoria Branch' },
    ],
  },
  { id: 2, name: 'Western Cape Region' },
]

describe('PositionTreeSelect — the states', () => {
  it('every level has an accessible name — the defect this restyle actually fixed', () => {
    renderWithProviders(<PositionTreeSelect structures={TREE} selectedIds={[]} onChange={() => {}} label="Position" />)

    // 🔴 The old implementation put a bare <label> with no `htmlFor` above each <select>.
    // A sighted user saw "Level 2"; a screen-reader user reached an unnamed combo box —
    // the same defect as A28 one file over, which axe rates CRITICAL.
    expect(screen.getByLabelText('Position')).toBeInTheDocument()
  })

  it('renders nothing when there is no tree, and a loading level while one is on its way', () => {
    const { container, unmount } = renderWithProviders(
      <PositionTreeSelect structures={[]} selectedIds={[]} onChange={() => {}} label="Position" />,
    )
    expect(container).toBeEmptyDOMElement()
    unmount()

    renderWithProviders(
      <PositionTreeSelect structures={[]} selectedIds={[]} onChange={() => {}} label="Position" loading />,
    )
    expect(screen.getByLabelText('Position')).toHaveAttribute('aria-busy', 'true')
  })

  it('a tree that FAILED to load says so, with a retry -- it does not vanish as an empty one does (06.4)', async () => {
    // 🔴 `structures.length === 0 && !loading` returned null for a failure too, so the field disappeared
    // from the recruiter dialogs and nothing said a list was missing. The case above is the must-pass: a
    // tree that is genuinely empty still renders nothing.
    const user = setupUser()
    const onRetryLoad = vi.fn()
    renderWithProviders(
      <PositionTreeSelect
        structures={[]}
        selectedIds={[]}
        onChange={() => {}}
        label="Position"
        loadError="The structures list could not be loaded."
        onRetryLoad={onRetryLoad}
      />,
    )

    expect(screen.getByText('Position could not be loaded.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /try again/i }))
    expect(onRetryLoad).toHaveBeenCalled()
  })

  it('a pre-selection reconstructs the whole cascade', () => {
    renderWithProviders(
      <PositionTreeSelect structures={TREE} selectedIds={[111]} onChange={() => {}} label="Position" />,
    )
    expect(screen.getByLabelText('Position')).toHaveValue('1')
    expect(screen.getByLabelText('Within Gauteng Region')).toHaveValue('11')
    expect(screen.getByLabelText('Within Johannesburg Branch')).toHaveValue('111')
  })
})

describe('PositionTreeSelect — the keyboard', () => {
  it('choosing a parent reveals its children, and re-choosing clears everything below', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    renderWithProviders(
      <PositionTreeSelect structures={TREE} selectedIds={[]} onChange={onChange} label="Position" />,
    )

    await user.selectOptions(screen.getByLabelText('Position'), '1')
    expect(onChange).toHaveBeenLastCalledWith([1])

    // The caption comes from the parent's own name, not "Level 2" — a cascade that
    // describes the widget instead of the choice is a cascade nobody can follow.
    const level2 = screen.getByLabelText('Within Gauteng Region')
    await user.selectOptions(level2, '11')
    expect(onChange).toHaveBeenLastCalledWith([1, 11])

    // 🔑 Going back up must not leave a stale grandchild in the value.
    await user.selectOptions(screen.getByLabelText('Position'), '2')
    expect(onChange).toHaveBeenLastCalledWith([2])
    expect(screen.queryByLabelText('Within Gauteng Region')).toBeNull()
  })
})

describe('PositionTreeSelect — accessibility, both themes', () => {
  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <PositionTreeSelect structures={TREE} selectedIds={[111]} onChange={() => {}} label="Position" />,
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

describe('PositionTreeSelect — the 16px rule, mutation-checked', () => {
  it('every level wears field-text, because it routes through SelectInput', () => {
    // The structural point: this component does not restate the rule, it inherits it.
    // A level that stopped going through `SelectInput` would lose the rule silently, and
    // this is what catches that.
    renderWithProviders(
      <PositionTreeSelect structures={TREE} selectedIds={[111]} onChange={() => {}} label="Position" />,
    )

    for (const name of ['Position', 'Within Gauteng Region', 'Within Johannesburg Branch']) {
      expect(screen.getByLabelText(name).className).toMatch(/\bfield-text\b/)
    }
  })
})

describe('PositionTreeSelect — when the CALLER clears the selection', () => {
  /*
   * 🔴 WRITTEN BECAUSE A DELETION PROOF FOUND NOTHING WATCHING. `10.4`, batch 7.
   *
   * When the caller empties `selectedIds` -- Recruiters does it when "elected rep" is set back to No -- the cascade
   * forgets every level. That was an effect; `10.4` moved it into render. Deleted outright, the whole suite stayed
   * GREEN: the cascade test above passes one `[]` and never changes it, so it only ever proved the OTHER half
   * (a caller that does not clear must not be overridden). This is the half that does clear.
   */
  function Caller() {
    const [ids, setIds] = useState([])
    return (
      <>
        <button type="button" onClick={() => setIds([])}>
          the caller clears it
        </button>
        <PositionTreeSelect structures={TREE} selectedIds={ids} onChange={setIds} label="Position" />
      </>
    )
  }

  it('a caller that empties the selection empties every level', async () => {
    const user = setupUser()
    renderWithProviders(<Caller />)

    await user.selectOptions(screen.getByLabelText('Position'), '1')
    await user.selectOptions(screen.getByLabelText('Within Gauteng Region'), '11')

    await user.click(screen.getByRole('button', { name: 'the caller clears it' }))

    expect(screen.getByLabelText('Position')).toHaveValue('')
    expect(screen.queryByLabelText('Within Gauteng Region')).toBeNull()
  })

  it('the must-pass: a caller that feeds each choice back keeps it -- the clear does not fight the keyboard', async () => {
    const user = setupUser()
    renderWithProviders(<Caller />)

    await user.selectOptions(screen.getByLabelText('Position'), '1')
    await user.selectOptions(screen.getByLabelText('Within Gauteng Region'), '11')

    expect(screen.getByLabelText('Position')).toHaveValue('1')
    expect(screen.getByLabelText('Within Gauteng Region')).toHaveValue('11')
  })
})
