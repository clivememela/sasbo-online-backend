// @vitest-environment jsdom -- asserts the wrapper the DOM actually gets
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import TableScroller from './TableScroller'
import Table from './Table'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'

/**
 * 🔴 THE STRUCTURAL GUARD (contract ⑮). The rule is *"every table scrolls"*, and the
 * failure mode is not that somebody writes it wrongly — it is that somebody forgets. That
 * is a matter of record on both sides: MobiZone honoured their equivalent rule in seven
 * files and forgot it in the eighth, where it clipped the Actions column off the screen;
 * and in THIS repository `Members.jsx` and `LocationListing.jsx` wrap their tables while
 * `Subscriptions.jsx:122` does not.
 *
 * So the assertion that matters is not "this scroller scrolls" — it is that the `Table`
 * primitive cannot render without it. `05.2` routes the other nine surfaces through here.
 */

describe('TableScroller — the structural guarantee', () => {
  it('the Table primitive cannot render its table outside a scrolling container', () => {
    const { container } = renderWithProviders(
      <Table
        columns={[{ key: 'name', header: 'Name' }]}
        rows={[{ id: 1, name: 'Thandi Nkosi' }]}
        rowKey="id"
        caption="Members"
      />,
    )

    const table = container.querySelector('table')
    expect(table).not.toBeNull()

    // MUTATION-CHECKED: removing `overflow-x-auto` from `TableScroller.jsx`, or unwrapping
    // the table in `Table.jsx`, turns this red. Both are the ways the rule gets lost.
    const scroller = table.closest('.overflow-x-auto')
    expect(
      scroller,
      'This table is not inside a horizontally scrolling container. At 390px a nine-column ' +
        'members table then clips its last columns off the screen with no way to reach ' +
        'them — and nothing errors, which is exactly why this is enforced by the ' +
        'primitive rather than remembered per file.',
    ).not.toBeNull()
  })

  it('is not a keyboard tab stop while there is nothing to scroll', () => {
    renderWithProviders(
      <TableScroller label="Members">
        <div>short</div>
      </TableScroller>,
    )

    // jsdom reports every width as 0, so nothing overflows here — which is the case worth
    // asserting: an always-on tab stop over a table that fits is a keyboard user tabbing
    // through an empty region on every screen in the app.
    expect(screen.queryByRole('region')).toBeNull()
  })

  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <TableScroller label="Members">
        <table>
          <caption>Members</caption>
          <tbody>
            <tr>
              <td>Thandi Nkosi</td>
            </tr>
          </tbody>
        </table>
      </TableScroller>,
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
