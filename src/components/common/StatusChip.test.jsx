// @vitest-environment jsdom -- the member status chip's colours, per status
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import StatusChip, { CANCELLED_STATUSES } from './StatusChip'
import { STATUS_OPTIONS } from '../../config/memberConstants'

const classesOf = (status) => {
  const { container, unmount } = render(<StatusChip status={status} />)
  const classes = container.firstChild.className
  unmount()
  return classes
}

describe('StatusChip', () => {
  it('03.5 draws every member status the vocabulary offers -- Terminated among them -- and none as Unknown', () => {
    const unknown = classesOf('Not a status')
    for (const status of STATUS_OPTIONS) {
      expect(classesOf(status), status).not.toBe(unknown)
    }
  })

  it('03.5 draws Terminated as the other cancelled memberships -- the same tokens, so the same measured contrast', () => {
    expect(classesOf('Terminated')).toBe(classesOf('Retired'))
    expect(CANCELLED_STATUSES).toContain('Terminated')
  })
})
