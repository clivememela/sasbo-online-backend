import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DEFECTS, disabled, pin } from '../test/knownDefects'

/**
 * `apiUrl` is a SECURITY GUARD, and it is tested adversarially.
 *
 * Its own comments say so: "prevents SSRF from a misconfigured env var" and
 * "prevents open redirect / SSRF". A security guard that is never tested is a
 * security guard that quietly stops working -- and this one is the single
 * chokepoint all nine services go through, so if it lets something past, it
 * lets it past everywhere at once.
 *
 * The question each case asks is not "what string comes back" but WHERE A
 * BROWSER WOULD SEND THIS. Those are different questions, and the gap between
 * them is exactly where the function is broken: a string that looks
 * host-relative can still resolve to another origin.
 *
 * THE BASE URL IS STUBBED, NEVER INHERITED.
 * `api.js` reads `import.meta.env.VITE_API_BASE_URL` at module load, and
 * `client/.env` is COMMITTED and sets it: to the deployed backend until 2026-09-22
 * (register C7), to `http://localhost:8080` since.
 * A first draft of this file inherited that value and asserted against
 * `sasbo.example` -- sixteen failures whose message was about SSRF and whose
 * cause was ambient configuration. Every case below therefore stubs the
 * variable and re-imports the module, so each states the configuration it is
 * about. Both configurations are covered, because they are different
 * functions: with no base the result is a same-origin path, with a base it is
 * an absolute URL, and "did it leave the origin" means something different in
 * each.
 */
const loadApiUrl = async (base) => {
  vi.resetModules()
  if (base === undefined) vi.stubEnv('VITE_API_BASE_URL', '')
  else vi.stubEnv('VITE_API_BASE_URL', base)
  const mod = await import('./api')
  return mod.apiUrl
}

const APP_ORIGIN = 'https://sasbo.example'

afterEach(() => vi.unstubAllEnvs())

describe('apiUrl with NO base url configured -- paths stay same-origin', () => {
  let apiUrl
  beforeEach(async () => {
    apiUrl = await loadApiUrl(undefined)
  })

  const resolves = (path) => new URL(apiUrl(path), APP_ORIGIN).href
  const leavesOrigin = (path) => !resolves(path).startsWith(`${APP_ORIGIN}/`)

  describe('the ordinary job', () => {
    it.each([
      ['a rooted path', '/api/members', '/api/members'],
      ['an unrooted path', 'api/members', '/api/members'],
      ['a path with a query', '/api/branches?bankName=x', '/api/branches?bankName=x'],
    ])('%s -> %s', (_name, input, expected) => {
      expect(apiUrl(input)).toBe(expected)
    })

    it('always returns a path beginning with exactly one slash', () => {
      for (const input of ['api/x', '/api/x', '//api/x', '////api/x']) {
        expect(apiUrl(input).startsWith('/'), input).toBe(true)
        expect(apiUrl(input).startsWith('//'), input).toBe(false)
      }
    })
  })

  describe('the guard it exists to be', () => {
    it.each([
      ['absoluteHttps', 'https://evil.example/steal'],
      ['absoluteHttp', 'http://evil.example/steal'],
      ['uppercaseScheme', 'HTTPS://evil.example/steal'],
      ['mixedCaseScheme', 'HtTpS://evil.example/steal'],
      ['protocolRelative', '//evil.example/steal'],
      ['manySlashes', '///////evil.example/steal'],
      ['schemeWithPlus', 'view-source+https://evil.example/steal'],
      ['userinfoTrick', 'https://sasbo.example@evil.example/steal'],
      ['tabBeforeScheme', '\thttps://evil.example/steal'],
      ['newlineBeforeScheme', '\nhttps://evil.example/steal'],
    ])('%s cannot leave this origin', (name, input) => {
      expect(
        leavesOrigin(input),
        `The vector named "${name}" was handed to apiUrl and a browser would send that ` +
          `request to ${resolves(input)} -- OFF THIS ORIGIN. Every service in the client ` +
          `builds its URL through this one function, and every call sets ` +
          `credentials: 'include', so a bypass here sends session cookies and member ` +
          `data to whoever controls that host.`,
      ).toBe(false)
    })

    it('a scheme is only stripped when it really is a scheme', () => {
      // "https:/evil.example" has one slash -- not a scheme, just an odd path
      // segment. It must be left alone rather than half-stripped, or the
      // stripping itself becomes a way to construct paths.
      expect(apiUrl('https:/evil.example')).toBe('/https:/evil.example')
    })
  })

  describe('the backslash bypass', () => {
    const VECTORS = [
      ['leadingBackslashes', '\\\\evil.example/steal'],
      ['slashThenBackslash', '/\\evil.example/steal'],
      ['slashBackslashSlash', '/\\/evil.example/steal'],
    ]

    it.skip(disabled(DEFECTS.B6, 'a backslash must not carry a request off this origin'), () => {
      for (const [name, input] of VECTORS) {
        expect(leavesOrigin(input), `vector ${name} resolved to ${resolves(input)}`).toBe(false)
      }
    })

    it(pin(DEFECTS.B6, 'a backslash still carries the request off this origin'), () => {
      expect(
        VECTORS.filter(([, input]) => leavesOrigin(input)).map(([name]) => name),
        'B6 has been fixed -- backslashes no longer escape the origin. Delete this pin, ' +
          'un-skip the test above, and close the row.',
      ).toEqual(['leadingBackslashes', 'slashThenBackslash', 'slashBackslashSlash'])
    })

    it('records exactly where each one lands, so a fix can be verified', () => {
      // Not decoration: these three strings are the evidence in the register
      // row. If the URL parser's normalisation ever changes, this says so
      // rather than the row quietly going stale.
      expect(resolves('\\\\evil.example/steal')).toBe('https://evil.example/steal')
      expect(resolves('/\\evil.example/steal')).toBe('https://evil.example/steal')
      expect(resolves('/\\/evil.example/steal')).toBe('https://evil.example/steal')
    })
  })
})

describe('apiUrl WITH a base url configured -- requests stay on that host', () => {
  const BASE = 'https://api.sasbo.example'
  let apiUrl
  beforeEach(async () => {
    apiUrl = await loadApiUrl(BASE)
  })

  it('prefixes the configured origin', () => {
    expect(apiUrl('/api/members')).toBe('https://api.sasbo.example/api/members')
  })

  it('keeps only the ORIGIN of the base, discarding any path on it', () => {
    // `new URL(RAW_BASE).origin` throws away a path, which is the safe choice
    // and worth pinning: a base of ".../v1" silently losing its "/v1" is the
    // kind of thing that is discovered in production.
    expect(apiUrl('/api/members')).not.toContain('/v1')
  })

  it.each([
    ['absoluteHttps', 'https://evil.example/steal'],
    ['protocolRelative', '//evil.example/steal'],
    ['uppercaseScheme', 'HTTPS://evil.example/steal'],
  ])('%s cannot redirect the call away from the configured host', (name, input) => {
    expect(
      new URL(apiUrl(input)).origin,
      `Vector "${name}" moved the request to a host the configuration never named.`,
    ).toBe(BASE)
  })

  describe('a hostile VITE_API_BASE_URL is refused, not obeyed', () => {
    it.each([
      ['javascriptScheme', 'javascript:alert(1)'],
      ['dataScheme', 'data:text/html,<script>1</script>'],
      ['fileScheme', 'file:///etc/passwd'],
      ['notAUrlAtAll', 'nonsense'],
    ])('%s falls back to same-origin paths', async (name, hostileBase) => {
      const guarded = await loadApiUrl(hostileBase)
      expect(
        guarded('/api/members'),
        `A base url of "${hostileBase}" (vector ${name}) was accepted. The env var is the ` +
          `SSRF vector this guard names in its own comment: a misconfigured or injected ` +
          `base sends every API call in the application somewhere else.`,
      ).toBe('/api/members')
    })
  })
})
