// @vitest-environment jsdom -- presses a link in a document and watches what the browser is handed
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import saveFile, { REVOKE_AFTER_MS } from './saveFile'

/**
 * A fetched file handed to the browser to save: the bytes under the server's name (or the caller's), from a link
 * pressed while it is in the document, which then leaves it -- and the object URL let go only once the browser has had
 * time to take the bytes. jsdom has no object URLs, so the two calls are stood in for and recorded.
 */
describe('saveFile', () => {
  const originals = { create: URL.createObjectURL, revoke: URL.revokeObjectURL }
  let created
  let revoked
  let presses

  beforeEach(() => {
    vi.useFakeTimers()
    created = []
    revoked = []
    presses = []
    URL.createObjectURL = (blob) => {
      created.push(blob)
      return `blob:synthetic/${created.length}`
    }
    URL.revokeObjectURL = (url) => revoked.push(url)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function press() {
      presses.push({ href: this.getAttribute('href'), download: this.download, inDocument: document.body.contains(this) })
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    URL.createObjectURL = originals.create
    URL.revokeObjectURL = originals.revoke
  })

  it('hands the browser the bytes under the server\'s name, from a link pressed while it is in the document', () => {
    const blob = new Blob(['synthetic bytes'])
    saveFile({ blob, filename: 'Payroll discrepancies - Invented - September 2026.xlsx' }, 'fallback.xlsx')

    expect(created).toEqual([blob])
    expect(presses).toEqual([{
      href: 'blob:synthetic/1',
      download: 'Payroll discrepancies - Invented - September 2026.xlsx',
      inDocument: true,
    }])
    // ...and the link does not stay behind in the page.
    expect(document.querySelectorAll('a[download]')).toHaveLength(0)
  })

  it('names the file itself when the server gave no name', () => {
    saveFile({ blob: new Blob(['x']), filename: null }, 'Payroll queries - Invented - September 2026.pdf')
    expect(presses[0].download).toBe('Payroll queries - Invented - September 2026.pdf')
  })

  it('lets the URL go only after the browser has had time to take the bytes -- never before', () => {
    saveFile({ blob: new Blob(['x']), filename: 'a.xlsx' }, 'fallback.xlsx')

    vi.advanceTimersByTime(REVOKE_AFTER_MS - 1)
    expect(revoked).toEqual([])

    vi.advanceTimersByTime(1)
    expect(revoked).toEqual(['blob:synthetic/1'])
  })
})
