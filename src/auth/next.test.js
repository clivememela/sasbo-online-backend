import { describe, expect, it } from 'vitest'

import { loginPath } from './next'
import { safeNext } from './safeNext'

/**
 * `next` -- where a person goes after signing in -- arrives in an address anybody can write, so only a path on THIS
 * site is honoured (no open redirect). Each refused case below is a way a browser would leave the site.
 */
describe('safeNext', () => {
  it.each([
    ['/members', '/members'],
    ['/members?q=Synthetic&page=2', '/members?q=Synthetic&page=2'],
    ['/admin/maintenance/users#top', '/admin/maintenance/users#top'],
    ['/', '/'],
  ])('honours %s, a path on this site', (next, expected) => {
    expect(safeNext(next)).toBe(expected)
  })

  it.each([
    ['nothing at all', null],
    ['an empty string', ''],
    ['a protocol-relative address', '//evil.example/steal'],
    ['a slash then a backslash', '/\\evil.example'],
    ['a backslash anywhere', '/members\\..\\..\\evil'],
    ['an absolute address', 'https://evil.example/steal'],
    ['a script address', 'javascript:alert(1)'],
    ['a path with no leading slash', 'members'],
    ['a tab hidden in the address', '/\t/evil.example'],
    ['a newline hidden in the address', '/\n/evil.example'],
    ['the sign-in page itself', '/login?next=/members'],
    ['the change-password page', '/change-password'],
  ])('refuses %s, and sends the person Home instead', (_case, next) => {
    expect(safeNext(next)).toBe('/home')
  })

  it('a refused `next` goes to the fallback the caller names', () => {
    expect(safeNext('//evil.example', '/members')).toBe('/members')
  })
})

describe('loginPath', () => {
  it('asks to sign in and come back to exactly where the person was', () => {
    expect(loginPath({ pathname: '/admin/payroll', search: '?month=2026-09' })).toBe('/login?next=%2Fadmin%2Fpayroll%3Fmonth%3D2026-09')
    expect(safeNext(decodeURIComponent(loginPath({ pathname: '/members', search: '' }).split('next=')[1]))).toBe('/members')
  })
})
