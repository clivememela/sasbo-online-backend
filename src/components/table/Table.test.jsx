// @vitest-environment jsdom -- renders a real table and expands a row with the keyboard
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'

import Table from './Table'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectErrorState, expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

const MEMBERS = [
  { id: 11, number: 'M-0011', name: 'Thandi Nkosi', bank: 'Absa', branch: 'Sandton City', status: 'Active' },
  { id: 12, number: 'M-0012', name: 'Sipho Dlamini', bank: 'Nedbank', branch: 'Braamfontein', status: 'Pending' },
]

const COLUMNS = [
  { key: 'number', header: 'Number', priority: 'essential' },
  { key: 'name', header: 'Name', priority: 'essential' },
  { key: 'bank', header: 'Bank', priority: 'secondary' },
  { key: 'branch', header: 'Branch', priority: 'detail' },
]

const EMPTY_COPY = 'No members match this search.'

describe('Table — the four data states', () => {
  it('loading: a live status, and NOT the empty copy', () => {
    renderWithProviders(
      <Table columns={COLUMNS} rows={[]} rowKey="id" loading caption="Members" emptyMessage={EMPTY_COPY} />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Loading…')
    expect(screen.queryByText(EMPTY_COPY)).toBeNull()
  })

  it('error: announces the failure with a retry, and NEVER renders as empty', async () => {
    const user = setupUser()
    const onRetry = vi.fn()
    renderWithProviders(
      <Table
        columns={COLUMNS}
        rows={[]}
        rowKey="id"
        error="We could not reach the server."
        onRetry={onRetry}
        caption="Members"
        emptyMessage={EMPTY_COPY}
      />,
    )

    // 🔴 A27, one layer down. The shared assertion checks BOTH halves — the error
    // affordance present AND the empty copy absent — because only that pair can tell
    // "the backend is down" from "there is nothing here".
    const { retry } = await expectErrorState({ emptyText: EMPTY_COPY })
    expect(retry).not.toBeNull()

    await user.click(retry)
    expect(onRetry).toHaveBeenCalled()
  })

  it('empty: the empty copy, announced politely and with no error affordance', () => {
    renderWithProviders(
      <Table columns={COLUMNS} rows={[]} rowKey="id" caption="Members" emptyMessage={EMPTY_COPY} />,
    )
    expect(screen.getByText(EMPTY_COPY)).toBeInTheDocument()

    // The half that carries the rule: an empty list is NEVER an alert. conventions §8 --
    // "no members match this search" is an answer, not a failure, and announcing it as one
    // is the same category error as A27 pointing the other way.
    expect(screen.queryByRole('alert')).toBeNull()

    /*
     * ⚠️ THIS USED TO REQUIRE `role="status"` TO BE ABSENT TOO, AND `06.1b` CHANGED IT
     * DELIBERATELY -- so it is worth saying why rather than letting it read as a test
     * loosened to fit an implementation.
     *
     * The empty state renders through `DataState` now, which announces it politely. The old
     * branch had no role at all, and the consequence was silence at the one moment it
     * mattered: a screen-reader user narrows a filter, forty rows become none, and NOTHING
     * is announced -- the table simply goes quiet and they have no way to know whether it
     * answered. EH-D6 draws the line at result versus refusal, and an empty result is still
     * a result.
     */
    expect(screen.getByRole('status')).toHaveTextContent(EMPTY_COPY)
  })

  it('loaded: renders every essential and secondary column as a real th/td', () => {
    renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" caption="Members" />)

    expect(screen.getByRole('columnheader', { name: 'Number' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Bank' })).toBeInTheDocument()
    // `detail` is never a column — it exists only in the expanded panel.
    expect(screen.queryByRole('columnheader', { name: 'Branch' })).toBeNull()
    expect(screen.getByRole('cell', { name: 'Thandi Nkosi' })).toBeInTheDocument()
  })

  it('a prefix of a longer list says so, instead of implying the list is complete', () => {
    // R-SCALE-D. This is what the two `slice(0, 50)` caps in Recruiters.jsx do silently.
    renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" total={412} caption="Members" />)
    expect(screen.getByText(/refine your search/i)).toBeInTheDocument()
    expect(screen.getByText('412')).toBeInTheDocument()
  })

  it('an exhaustive list says nothing about refining', () => {
    renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" total={2} caption="Members" />)
    expect(screen.queryByText(/refine your search/i)).toBeNull()
  })
})

describe('Table — the keyboard', () => {
  it('the row expander is a real button, Tab reaches it, and Enter opens the detail panel', async () => {
    const user = setupUser()
    renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" caption="Members" />)

    const expanders = screen.getAllByRole('button', { name: /show details/i })
    expect(expanders).toHaveLength(MEMBERS.length)

    expanders[0].focus()
    expect(expanders[0]).toHaveAttribute('aria-expanded', 'false')

    await user.keyboard('{Enter}')
    expect(screen.getByRole('button', { name: /hide details/i })).toHaveAttribute('aria-expanded', 'true')

    // The `detail` column's value is reachable only here, which is the whole point of
    // the priority model: nothing is lost, it moves.
    expect(screen.getByText('Sandton City')).toBeInTheDocument()
  })

  it('offers NO row-level click handler — a row action is a control inside the row', () => {
    // 🔴 Run 7 found six controls only a mouse can reach; click-only rows are four of
    // them. An `onRowClick` escape hatch easier than the correct thing is the thing that
    // gets used, so the primitive does not have one. Asserted on the rendered DOM: no
    // <tr> carries a click handler that a keyboard cannot trigger.
    const { container } = renderWithProviders(
      <Table
        columns={[
          { key: 'name', header: 'Name' },
          {
            key: 'actions',
            header: 'Actions',
            cell: (row) => <button type="button">Edit {row.name}</button>,
          },
        ]}
        rows={MEMBERS}
        rowKey="id"
        caption="Members"
      />,
    )

    for (const row of container.querySelectorAll('tbody tr')) {
      expect(row.getAttribute('onclick')).toBeNull()
      expect(row).not.toHaveAttribute('role', 'button')
      expect(row).not.toHaveAttribute('tabindex')
    }

    expect(screen.getByRole('button', { name: 'Edit Thandi Nkosi' })).toBeInTheDocument()
  })
})

describe('Table — accessibility, both themes', () => {
  it('has no violations loaded, empty, loading or failed, in light or dark', async () => {
    const states = [
      { rows: MEMBERS },
      { rows: [], emptyMessage: EMPTY_COPY },
      { rows: [], loading: true },
      { rows: [], error: 'Could not load', onRetry: () => {} },
    ]

    for (const state of states) {
      const { container, unmount } = renderWithProviders(
        <Table columns={COLUMNS} rowKey="id" caption="Members" {...state} />,
      )

      await expectNoA11yViolations(container)

      document.documentElement.classList.add('dark')
      try {
        await expectNoA11yViolations(container)
      } finally {
        document.documentElement.classList.remove('dark')
        unmount()
      }
    }
  })
})

describe('Table — rowKey, mutation-checked', () => {
  /**
   * 🔴 THE CONVENTION THIS PRIMITIVE BAKES IN (R-ROB-H). A table keyed by array index
   * looks perfect until the list reorders or a row is deleted — and then React reuses
   * the DOM node with the previous row's state in it: the ticked checkbox follows the
   * wrong record, the open detail panel describes someone else, an edit lands on the
   * wrong member. **Nothing errors.**
   *
   * So the primitive refuses to guess. This asserts the refusal, and the refusal is the
   * deliverable — a `rowKey` that merely defaults to the index is a convention nobody
   * has to keep.
   *
   * MUTATION-CHECKED: deleting the `if (import.meta.env?.DEV && !rowKey) throw` block in
   * `Table.jsx` turns this test, and only this test, red.
   */
  it('a table with no rowKey throws rather than silently keying by index', () => {
    // React logs the thrown render; the throw itself is the assertion.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(() =>
        renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} caption="Members" />),
      ).toThrow(/Table requires `rowKey`/)
    } finally {
      consoleError.mockRestore()
    }
  })

  it('rowKey accepts a field name and a function, and the key is the record id', () => {
    const byField = renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" caption="A" />)
    expect(byField.container.querySelectorAll('tbody tr')).toHaveLength(2)
    byField.unmount()

    const byFunction = renderWithProviders(
      <Table columns={COLUMNS} rows={MEMBERS} rowKey={(row) => `member-${row.id}`} caption="B" />,
    )
    expect(byFunction.container.querySelectorAll('tbody tr')).toHaveLength(2)
  })

  it('an unknown column priority throws rather than deciding silently what a phone shows', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(() =>
        renderWithProviders(
          <Table
            columns={[{ key: 'name', header: 'Name', priority: 'secondry' }]}
            rows={MEMBERS}
            rowKey="id"
            caption="Members"
          />,
        ),
      ).toThrow(/Known priorities/)
    } finally {
      consoleError.mockRestore()
    }
  })
})

describe('Table — truncation stays inside the cell', () => {
  it('a truncated cell keeps the full value in the title and in the detail panel', async () => {
    const user = setupUser()
    const long = 'A member with an extremely long name that will not fit in one column'
    renderWithProviders(
      <Table
        columns={[
          { key: 'name', header: 'Name', truncate: true },
          { key: 'branch', header: 'Branch', priority: 'detail' },
        ]}
        rows={[{ id: 1, name: long, branch: 'Braamfontein' }]}
        rowKey="id"
        caption="Members"
      />,
    )

    // R-RESP-I: truncation is allowed ONLY where the full value is still reachable.
    const cell = screen.getByRole('cell', { name: long })
    expect(cell.className).toMatch(/\btruncate\b/)
    expect(cell).toHaveAttribute('title', long)

    await user.click(screen.getByRole('button', { name: /show details/i }))
    expect(screen.getByText('Braamfontein')).toBeInTheDocument()
  })
})

describe('Table — a footer, and a column that ends a phone card (MO24)', () => {
  it('the footer is a tfoot row of the same columns, shown with the rows, whose expander cell is empty', () => {
    const footer = { number: 'Total', name: '2 members', bank: '2 banks' }
    renderWithProviders(<Table columns={COLUMNS} rows={MEMBERS} rowKey="id" caption="Members" footer={footer} />)

    const total = screen.getByRole('cell', { name: 'Total' }).closest('tr')
    expect(total.parentElement.tagName).toBe('TFOOT')
    expect([...total.querySelectorAll('td[data-col]')].map((cell) => cell.textContent)).toEqual(['Total', '2 members', '2 banks'])
    expect(total.querySelector('td[data-expander]')).toBeEmptyDOMElement()
    // The rows above it still open: one expander each, and none in the footer.
    expect(screen.getAllByRole('button', { name: /show details/i })).toHaveLength(MEMBERS.length)
  })

  it('the must-pass: a footer is not shown over a table with nothing in it', () => {
    renderWithProviders(<Table columns={COLUMNS} rows={[]} rowKey="id" caption="Members" footer={{ number: 'Total' }} />)
    expect(document.querySelector('tfoot')).toBeNull()
  })

  it('a column marked `end` marks its cells, and only its own', () => {
    const columns = [{ key: 'name', header: 'Name' }, { key: 'number', header: 'Number', end: true }]
    renderWithProviders(<Table columns={columns} rows={MEMBERS} rowKey="id" caption="Members" />)

    expect([...document.querySelectorAll('td[data-end]')].map((cell) => cell.dataset.col)).toEqual(['number', 'number'])
  })
})

describe('Table — what a row opens onto (month end A.1)', () => {
  it('`below` puts what it answers under that row alone, across every column, and nothing under the others', () => {
    renderWithProviders(
      <Table
        columns={COLUMNS}
        rows={MEMBERS}
        rowKey="id"
        caption="Members"
        below={(member) => member.id === 12 && <p>Opened under Sipho Dlamini</p>}
      />,
    )

    const opened = screen.getByText('Opened under Sipho Dlamini').closest('tr')
    expect(opened).toHaveAttribute('data-below')
    expect(opened.previousElementSibling).toHaveTextContent('Sipho Dlamini')
    // Every column, the phone's expander among them: the opened row spans the table.
    expect(opened.querySelector('td')).toHaveAttribute('colspan', '4')
    expect(document.querySelectorAll('tr[data-below]')).toHaveLength(1)
    // It is not a row a person acts on: arrows and the row count pass it by.
    expect(opened).not.toHaveAttribute('data-row')
  })
})
