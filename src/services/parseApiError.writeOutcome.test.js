import { describe, expect, it } from 'vitest'

import { ERROR_KINDS, writeFailure, writeMayHaveLanded } from './parseApiError'

/**
 * R3-F2: only a server that answered and REFUSED supports "nothing was written". Both halves, because a helper that
 * always said "may have landed" would pass the first alone and turn every refusal into a vague "check the register".
 */
describe('writeMayHaveLanded', () => {
  it.each([
    ['a timeout', { kind: ERROR_KINDS.TIMEOUT, status: 0 }],
    ['a lost connection', { kind: ERROR_KINDS.NETWORK, status: 0 }],
    ['a server error', { kind: ERROR_KINDS.HTTP, status: 500 }],
    ['a success whose body was not JSON', { kind: ERROR_KINDS.NON_JSON, status: 200 }],
    ['something that is not an API error at all', new Error('boom')],
    ['nothing', undefined],
  ])('%s may have landed', (_, error) => {
    expect(writeMayHaveLanded(error)).toBe(true)
  })

  it.each([
    ['a bad request', 400],
    ['a conflict', 409],
    ['a validation refusal', 422],
  ])('the must-pass: %s did not', (_, status) => {
    expect(writeMayHaveLanded({ kind: ERROR_KINDS.HTTP, status })).toBe(false)
  })
})

/**
 * The sentence the rule chooses, one home since RF (Recruiters and Recruitment Channel each wrote it). Both halves,
 * and the place named: a builder that ignored `whereToCheck` would pass the default and send a clerk to the wrong screen.
 */
describe('writeFailure', () => {
  it('a timeout says it could not be confirmed, and where to look', () => {
    expect(writeFailure({ kind: ERROR_KINDS.TIMEOUT, status: 0, message: 'The request took too long.' }, 'That recruiter', 'the recruiter'))
      .toBe('That recruiter could not be confirmed. Reload the recruiter and check before trying again.')
  })

  it('the place defaults to the list', () => {
    expect(writeFailure({ kind: ERROR_KINDS.NETWORK, status: 0 }, 'That recruitment channel'))
      .toBe('That recruitment channel could not be confirmed. Reload the list and check before trying again.')
  })

  it("the must-pass: a refusal says it was not saved, in the server's words", () => {
    expect(writeFailure({ kind: ERROR_KINDS.HTTP, status: 409, message: 'That name is taken.' }, 'The new name for Walk-in'))
      .toBe('The new name for Walk-in was not saved. That name is taken.')
  })
})
