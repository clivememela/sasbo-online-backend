import { test, expect } from '@playwright/test'
import { _internals } from '../../support/env.js'

const { readTarget, DEFAULT_CLIENT_URL, DEFAULT_API_URL } = _internals

/**
 * The target resolver is the single invariant this whole suite rests on: no
 * path to a sibling repository, anywhere, ever. These tests exist because that
 * invariant is only as good as the parsing behind it, and a resolver that
 * silently accepts nonsense points a green suite at the wrong thing.
 */
test.describe('target resolution', () => {
  test('falls back to the local dev stack when the variable is unset', () => {
    expect(readTarget('SASBO_UNSET_FOR_TEST', DEFAULT_CLIENT_URL)).toBe(DEFAULT_CLIENT_URL)
  })

  test('treats an empty or whitespace-only variable as unset', () => {
    // A shell that exports SASBO_CLIENT_URL= produces an empty string, not an
    // absent variable. Treating that as a URL yields a parse failure blaming
    // the user for a value they never typed.
    process.env.SASBO_BLANK_FOR_TEST = '   '
    expect(readTarget('SASBO_BLANK_FOR_TEST', DEFAULT_API_URL)).toBe(DEFAULT_API_URL)
    delete process.env.SASBO_BLANK_FOR_TEST
  })

  test('uses the variable when one is set', () => {
    process.env.SASBO_SET_FOR_TEST = 'http://127.0.0.1:9999'
    expect(readTarget('SASBO_SET_FOR_TEST', DEFAULT_CLIENT_URL)).toBe('http://127.0.0.1:9999')
    delete process.env.SASBO_SET_FOR_TEST
  })

  test('strips trailing slashes so joined paths never double up', () => {
    process.env.SASBO_SLASH_FOR_TEST = 'http://localhost:8080///'
    expect(readTarget('SASBO_SLASH_FOR_TEST', DEFAULT_API_URL)).toBe('http://localhost:8080')
    delete process.env.SASBO_SLASH_FOR_TEST
  })

  test('rejects an unparseable value by name, at load time', () => {
    process.env.SASBO_BAD_FOR_TEST = 'not a url at all'
    expect(() => readTarget('SASBO_BAD_FOR_TEST', DEFAULT_API_URL)).toThrow(
      /SASBO_BAD_FOR_TEST is not a valid URL/,
    )
    delete process.env.SASBO_BAD_FOR_TEST
  })

  test('rejects a host and port with the scheme left off', () => {
    // `localhost:8080` is the most likely typo a person actually makes, and it
    // is NOT caught by URL parsing: Node parses it happily as the scheme
    // "localhost:" with path "8080". Only the protocol check catches it. This
    // test exists because the first version of it assumed the parser would,
    // and passed for the wrong reason.
    process.env.SASBO_NOSCHEME_FOR_TEST = 'localhost:8080'
    expect(() => readTarget('SASBO_NOSCHEME_FOR_TEST', DEFAULT_API_URL)).toThrow(
      /must be http or https/,
    )
    delete process.env.SASBO_NOSCHEME_FOR_TEST
  })

  test('rejects a non-http protocol', () => {
    process.env.SASBO_PROTO_FOR_TEST = 'file:///etc/passwd'
    expect(() => readTarget('SASBO_PROTO_FOR_TEST', DEFAULT_API_URL)).toThrow(
      /must be http or https/,
    )
    delete process.env.SASBO_PROTO_FOR_TEST
  })
})
