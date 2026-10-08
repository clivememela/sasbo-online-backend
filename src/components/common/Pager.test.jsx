// @vitest-environment jsdom -- renders the sheet's foot and reads its sentence back
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'

import Pager from './Pager'

/**
 * THE FOOT OF A SHEET NEVER CONTRADICTS THE STATE ABOVE IT. `06.3`, K1.
 *
 * 🔴 The register's error state said "The register could not be reached" -- and the pager under
 * it, fed a count of 0 because the list had stayed `[]`, said "No members match". One sheet, two
 * answers, and the second is A27's confident wrong answer: an unreachable register presented as
 * an empty union. The same foot sits under Recruiters, Bank/Branch and Bank Listing. The page's
 * own states test looked for the EMPTY STATE's sentence, "No members match these criteria", which
 * the foot never prints -- so it passed while the page said the thing it exists to forbid (T37).
 */
const props = { page: 1, totalPages: 1, onPageChange: () => {}, start: 1, end: 0, noun: 'members' }

describe('Pager', () => {
  it('a list that failed to load is not a list in which nothing matched', () => {
    render(<Pager {...props} count={0} error />)

    expect(screen.queryByText(/no members match/i)).not.toBeInTheDocument()
    expect(screen.getByText('Could not load members')).toBeInTheDocument()
  })

  it('a list that genuinely came back empty still says so', () => {
    // The pair. Without it the case above would pass against a pager that never says "No members
    // match" at all -- a locator has to be seen to MATCH when it should before its absence means
    // anything.
    render(<Pager {...props} count={0} />)

    expect(screen.getByText('No members match')).toBeInTheDocument()
  })

  it('offers no paging over a list that is not there', () => {
    render(<Pager {...props} count={0} totalPages={3} error />)

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })
})
