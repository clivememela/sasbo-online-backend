import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../test/apiContractScan'
import { AUTH_REFUSAL, PASSWORD_CHANGE_REQUIRED, SCOPE_TYPES, SESSION_ENDING, SIGN_IN_MODES, refusalOf } from './authVocabulary'
import { PERMISSIONS, PERMISSION_LABELS } from './permissions'

/**
 * Sign-in's words held to the SERVER'S (`api-vocabulary.json`, `auth` -- contracts/signin.md): the refusals the screens
 * branch on, the two modes, the three kinds of scope and the permission catalogue. A name the server renames reddens
 * this file, not a screen that waits for a code that never comes or a role editor offering a permission that is gone.
 *
 * Compared as SETS: the order is the generator's, and nothing here depends on it.
 */
const auth = committedVocabulary().auth
const sorted = (values) => [...values].sort()

describe('the sign-in vocabulary is the server\'s', () => {
  it('publishes its own section, beside month end\'s', () => {
    expect(auth, 'api-vocabulary.json has no auth section -- copy the server\'s generated artifact (src/test/README.md)').toBeDefined()
    expect(committedVocabulary().monthEnd.refusalCodes.map((c) => c.code)).not.toContain(AUTH_REFUSAL.SIGN_IN_FAILED)
  })

  it('every refusal a screen branches on is a code the server gives, at the status the contract says', () => {
    const published = new Map(auth.refusalCodes.map(({ code, status }) => [code, status]))
    expect([...published.keys()]).toEqual(expect.arrayContaining(Object.values(AUTH_REFUSAL)))
    // The statuses the contract fixes, so a 401 that turned into a 403 is seen here rather than in a lost redirect.
    expect(published.get(AUTH_REFUSAL.SIGN_IN_FAILED)).toBe(401)
    expect(published.get(AUTH_REFUSAL.ACCOUNT_LOCKED)).toBe(423)
    expect(published.get(AUTH_REFUSAL.SESSION_EXPIRED)).toBe(401)
    expect(published.get(AUTH_REFUSAL.SIGN_IN_REQUIRED)).toBe(401)
  })

  it('the refusals that end a session are the server\'s 401s, and among the codes the screens know', () => {
    const published = new Map(auth.refusalCodes.map(({ code, status }) => [code, status]))
    for (const code of SESSION_ENDING) {
      expect(published.get(code), `${code} ends a session in api.js but the server does not give it as a 401`).toBe(401)
      expect(Object.values(AUTH_REFUSAL)).toContain(code)
    }
    expect(SESSION_ENDING).not.toContain(AUTH_REFUSAL.SIGN_IN_FAILED)
  })

  it('a temporary password is the server\'s 403 PASSWORD_CHANGE_REQUIRED -- not a missing permission', () => {
    const published = new Map(auth.refusalCodes.map(({ code, status }) => [code, status]))
    expect(published.get(PASSWORD_CHANGE_REQUIRED)).toBe(403)
    expect(AUTH_REFUSAL.PASSWORD_CHANGE_REQUIRED).toBe(PASSWORD_CHANGE_REQUIRED)
  })

  it('the two modes and the three kinds of scope are exactly the server\'s', () => {
    expect(sorted(SIGN_IN_MODES)).toEqual(sorted(auth.modes))
    expect(sorted(SCOPE_TYPES)).toEqual(sorted(auth.scopeTypes))
  })

  it('PERMISSIONS is the whole of security.Permission, and every one has its words', () => {
    expect(sorted(Object.values(PERMISSIONS))).toEqual(sorted(auth.permissions))
    expect(sorted(Object.keys(PERMISSION_LABELS))).toEqual(sorted(auth.permissions))
  })

  it('reads a refusal code off a rejection, and nothing off anything else', () => {
    expect(refusalOf({ body: { code: 'STALE_VERSION' } })).toBe('STALE_VERSION')
    expect(refusalOf(new Error('offline'))).toBeNull()
    expect(refusalOf(undefined)).toBeNull()
  })
})
