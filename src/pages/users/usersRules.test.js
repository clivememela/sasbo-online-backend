import { describe, expect, it } from 'vitest'

import {
  NEW_USER, actionLabel, byOrdinal, editorFailure, formOf, positionBody, positionForm, positionProblems, roleProblems, scopeLabel,
  standingLabel, userBody, userProblems, userRefusal, whenLabel, whoLabel,
} from './usersRules'

/**
 * Maintenance's users, positions and roles: what the screens say and send, each rule named. The 5 Oct Show & Tell's ask
 * is the first describe -- "an administrator creating users with a position and an area".
 */

/** A rejection as `requestJson` builds one: the server's envelope as `body`, its status, `kind: 'http'`. */
const refused = (status, code, message = 'The server refused.', validationErrors = {}) =>
  Object.assign(new Error(message), { kind: 'http', status, body: { code, message }, validationErrors })

const filled = (overrides = {}) => ({ ...NEW_USER, displayName: 'Mo Synthetic', username: 'mo.synthetic', positionCode: 'ORGANISER', ...overrides })

describe('a user is created with a position and an area (5 Oct)', () => {
  it('needs a name, a username without spaces and a position', () => {
    expect(userProblems(NEW_USER, { creating: true })).toEqual({
      displayName: expect.any(String), username: expect.any(String), positionCode: expect.any(String),
    })
    expect(userProblems(filled({ username: 'mo synthetic' }), { creating: true }).username).toMatch(/no spaces/)
    expect(userProblems(filled(), { creating: true })).toEqual({})
  })

  it('a regional or area user with nothing chosen is refused -- they would see nothing at all', () => {
    expect(userProblems(filled({ scopeType: 'REGION' }), { creating: true }).scope).toMatch(/at least one region/)
    expect(userProblems(filled({ scopeType: 'AREA' }), { creating: true }).scope).toMatch(/at least one area/)
    expect(userProblems(filled({ scopeType: 'AREA', codes: ['A'] }), { creating: true })).toEqual({})
  })

  it('an e-mail is optional, and checked only when given', () => {
    expect(userProblems(filled({ email: 'not-an-address' }), { creating: true }).email).toBeDefined()
    expect(userProblems(filled({ email: 'mo.synthetic@example.invalid' }), { creating: true }).email).toBeUndefined()
  })

  it('sends the contract\'s body: trimmed, a blank role left to the position, ALL with no codes', () => {
    expect(userBody(filled({ displayName: ' Mo Synthetic ', username: ' mo.synthetic ', scopeType: 'AREA', codes: ['A', 'B'] }), { creating: true }))
      .toEqual({ username: 'mo.synthetic', displayName: 'Mo Synthetic', email: null, positionCode: 'ORGANISER', roleCode: null, scope: { type: 'AREA', codes: ['A', 'B'] } })
    expect(userBody(filled({ scopeType: 'ALL', codes: ['A'] }), { creating: true }).scope).toEqual({ type: 'ALL', codes: [] })
  })

  it('a change sends `active` and the `version` it was read at, and never a username', () => {
    const body = userBody(filled({ active: false }), { creating: false, version: 4 })
    expect(body).toMatchObject({ active: false, version: 4 })
    expect(body).not.toHaveProperty('username')
  })

  it('reads a user into the form, codes cleared for ALL', () => {
    expect(formOf({ displayName: 'Mo', username: 'mo', position: { code: 'ORGANISER' }, scope: { type: 'AREA', codes: ['Y'] }, active: false }))
      .toMatchObject({ positionCode: 'ORGANISER', roleCode: '', scopeType: 'AREA', codes: ['Y'], active: false })
    expect(formOf({ scope: { type: 'ALL', codes: ['X'] } }).codes).toEqual([])
  })
})

describe('each refusal says what to do next', () => {
  it('USERNAME_TAKEN goes on the username field', () => {
    expect(userRefusal(refused(409, 'USERNAME_TAKEN')).fields.username).toMatch(/taken/)
  })

  it('SCOPE_INVALID goes on the scope, in the server\'s words', () => {
    expect(userRefusal(refused(400, 'SCOPE_INVALID', 'Area Q is not on SASBO’s list.')).fields.scope).toBe('Area Q is not on SASBO’s list.')
  })

  it('STALE_VERSION offers to read the user again', () => {
    const refusal = userRefusal(refused(409, 'STALE_VERSION'))
    expect(refusal.stale).toBe(true)
    expect(refusal.notice).toMatch(/Someone else changed this user/)
  })

  it('LAST_ADMINISTRATOR explains why, and what to do first', () => {
    expect(userRefusal(refused(409, 'LAST_ADMINISTRATOR')).notice).toMatch(/no active user who can manage users/)
  })

  it('field errors the server names land on their fields; every scope path on the scope', () => {
    const refusal = userRefusal(refused(400, 'VALIDATION_FAILED', 'Check the fields.', { displayName: 'Too long.', 'scope.codes': 'Unknown code.' }))
    expect(refusal.fields).toEqual({ displayName: 'Too long.', scope: 'Unknown code.' })
    expect(refusal.notice).toBe('Check the fields.')
    // Bean Validation names a list's element by its index.
    expect(userRefusal(refused(400, 'VALIDATION_FAILED', 'Check.', { 'scope.codes[1]': 'A code is at most 8 characters.' })).fields)
      .toEqual({ scope: 'A code is at most 8 characters.' })
  })

  it('a refusal the screen does not branch on is said in the server\'s words (ROLE_ABOVE_YOUR_OWN, signin-server-notes §3)', () => {
    expect(userRefusal(refused(403, 'ROLE_ABOVE_YOUR_OWN', 'That role holds a permission you do not hold.')).notice)
      .toBe('That role holds a permission you do not hold.')
  })

  it('a position or role save that may have landed says "could not be confirmed", never "not saved"', () => {
    const timedOut = Object.assign(new Error('The request took too long and was stopped.'), { kind: 'timeout', status: 0 })
    expect(editorFailure(timedOut, 'The position Organiser', 'the positions').notice).toMatch(/could not be confirmed\. Reload the positions/)
    expect(editorFailure(refused(409, 'POSITION_TAKEN', 'That code is taken.'), 'The position', 'the positions').notice).toBe('That code is taken.')
  })
})

describe('positions and roles', () => {
  it('a new code is capitals, digits and underscores; a changed one is never sent anew', () => {
    const form = { code: 'organiser', name: 'Organiser', defaultRoleCode: 'ORGANISER', ordinal: '', active: true }
    expect(positionProblems(form, { creating: true }).code).toMatch(/capital letters/)
    expect(positionProblems({ ...form, code: 'ORGANISER' }, { creating: true })).toEqual({})
    expect(positionProblems(form, { creating: false })).toEqual({})
    expect(positionProblems({ ...form, ordinal: 'x' }, { creating: false }).ordinal).toBeDefined()
    expect(positionBody({ ...form, code: ' ORGANISER ', ordinal: ' 6 ' })).toEqual({ code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: true, ordinal: 6 })
    // A position read from the server carries its version, and a change sends it back (signin-server-notes §1.5).
    expect(positionBody(positionForm({ code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: true, ordinal: 6, version: 3 })))
      .toMatchObject({ version: 3 })
    expect(positionProblems({ ...form, code: 'O' }, { creating: true }).code).toBeDefined()
    expect(roleProblems({ code: '', name: '', permissions: [] }, { creating: true })).toEqual({ name: expect.any(String), code: expect.any(String) })
  })

  it('positions stand in SASBO\'s order, then by name', () => {
    expect(byOrdinal([{ name: 'B', ordinal: 2 }, { name: 'C', ordinal: 1 }, { name: 'A', ordinal: 2 }]).map((p) => p.name)).toEqual(['C', 'A', 'B'])
  })
})

describe('what the tables say', () => {
  it('a scope in words', () => {
    expect(scopeLabel({ type: 'ALL', codes: [] })).toBe('All of SASBO')
    expect(scopeLabel({ type: 'REGION', codes: ['NORTH'] })).toBe('Region: NORTH')
    expect(scopeLabel({ type: 'AREA', codes: ['A', 'B'] })).toBe('Areas: A, B')
    expect(scopeLabel(undefined)).toBe('All of SASBO')
  })

  it('an instant in Johannesburg, and the absence mark for none', () => {
    expect(whenLabel('2026-10-05T07:30:00Z')).toBe('05 Oct 2026, 09:30')
    expect(whenLabel(null)).toBe('—')
    expect(whenLabel('not a date')).toBe('—')
  })

  it('an action and a person as the record has them', () => {
    expect(actionLabel('SIGN_IN_FAILED')).toBe('Sign in failed')
    expect(actionLabel('')).toBe('—')
    expect(whoLabel({ displayName: 'Mo Synthetic', username: 'mo.synthetic' })).toBe('Mo Synthetic (mo.synthetic)')
    expect(whoLabel({ username: 'mo.synthetic' })).toBe('mo.synthetic')
    expect(whoLabel({})).toBe('System')
  })

  it('where a user stands', () => {
    expect(standingLabel({ active: false })).toBe('Closed')
    expect(standingLabel({ active: true, locked: true, lockedUntil: '2026-10-05T07:30:00Z' })).toBe('Locked until 05 Oct 2026, 09:30')
    expect(standingLabel({ active: true, mustChangePassword: true })).toBe('Temporary password')
    expect(standingLabel({ active: true })).toBe('Active')
  })
})
