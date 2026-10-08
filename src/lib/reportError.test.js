import { describe, expect, it } from 'vitest'

import { redact } from './reportError'
import { apiErrorFrom } from '../services/parseApiError'

/**
 * THE SINK REDACTS. EH-D10.
 *
 * 🔑 **IT ASSERTS ON `redact`, NOT ON A `console.error` SPY, AND THAT IS THE POINT.** A test
 * that spies the console proves the sink CALLED it and says nothing about what it said. The
 * question here is what leaves the process, so the thing under test is the value.
 *
 * 🔴 **THE ERROR IS BUILT BY `parseApiError`, NEVER BY HAND.** A hand-written
 * `{ message: '...', body: {...} }` is this test's author's idea of what a rejection looks
 * like, and it goes on being that after the real shape changes. `useAsyncOptions` is this
 * repository's own worked example: its abort test constructed a `name: 'AbortError'`
 * rejection, `06.1a` moved every service to a `kind`-carrying `Error`, and the test went on
 * passing over a branch that had become dead code. So these feed the sink what the helper
 * actually produces, from a `Response` the helper actually parses.
 */

/** A member envelope in the shape `GlobalExceptionHandler` really sends. */
const MEMBER_ENVELOPE = {
  message: 'Member 8801015009087 already exists',
  validationErrors: {
    identityNumber: 'must be 13 digits',
    bankAccountNumber: 'is not a valid account number',
  },
  // The echo a 400 handler returns, and the reason this test exists.
  member: {
    firstNames: 'Thandiwe',
    surname: 'Mokoena',
    identityNumber: '8801015009087',
    bankAccountNumber: '1234567890',
    personalEmail: 'thandiwe@example.test',
  },
}

const rejectionFor = (status, body) =>
  apiErrorFrom(
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )

describe('reportError redacts', () => {
  it('emits the diagnosis and NONE of the member', async () => {
    const error = await rejectionFor(400, MEMBER_ENVELOPE)

    const line = redact('Members.save', error)

    // What a developer needs, and it must genuinely be there -- a sink that redacted
    // everything would pass a test that only looked for absences.
    expect(line).toEqual({
      context: 'Members.save',
      kind: 'http',
      status: 400,
      retryable: false,
      refusedFields: ['identityNumber', 'bankAccountNumber'],
    })

    // And the whole of it, serialised, against every identifying value in the envelope.
    // Asserting field by field would pass a sink that copied `body` wholesale under a key
    // this test did not think to check.
    const serialised = JSON.stringify(line)
    for (const secret of [
      'Thandiwe',
      'Mokoena',
      '8801015009087',
      '1234567890',
      'thandiwe@example.test',
      // The server's own sentence carries the identity number INSIDE it, which is why the
      // message is not on the allowlist even though it is the most useful line to have.
      'Member 8801015009087 already exists',
    ]) {
      expect(serialised, `the sink leaked ${secret}`).not.toContain(secret)
    }
  })

  it('keeps the FIELD NAMES that were refused, and none of their values', async () => {
    const error = await rejectionFor(400, MEMBER_ENVELOPE)

    const line = redact('CaptureMember.save', error)

    // Knowing WHICH field was refused is most of the diagnosis; the value it was refused
    // for is the member's identity number.
    expect(line.refusedFields).toContain('identityNumber')
    expect(JSON.stringify(line)).not.toContain('must be 13 digits')
  })

  it('says `unhandled` for a rejection that did not come from requestJson', () => {
    // A programming error -- a TypeError from a bad destructure, say -- carries no `kind`.
    // Reporting it as an ordinary request failure would send someone to look at the network
    // tab for a bug that is in the component, so the sink distinguishes them in one word.
    const line = redact('Recruiters.render', new TypeError('x is not a function'))

    expect(line).toEqual({ context: 'Recruiters.render', kind: 'unhandled' })
    // ⚠️ NOT the message, even here. A TypeError's message is written by the engine and
    // interpolates the offending VALUE -- which on this screen is a member.
    expect(JSON.stringify(line)).not.toContain('is not a function')
  })

  it('survives being handed nothing at all', () => {
    // `catch (e)` can receive `undefined` from a `Promise.reject()` with no argument, and a
    // sink that throws inside an error handler replaces a handled failure with an unhandled
    // one -- at exactly the moment the application is already in trouble.
    expect(redact('somewhere', undefined)).toEqual({ context: 'somewhere', kind: 'unhandled' })
    expect(redact('somewhere', null)).toEqual({ context: 'somewhere', kind: 'unhandled' })
  })

  it('reports a network failure as retryable, with no status to mislead anyone', async () => {
    const { networkError } = await import('../services/parseApiError')

    const line = redact('Members.load', networkError(new TypeError('Failed to fetch')))

    expect(line.kind).toBe('network')
    expect(line.retryable).toBe(true)
    // `status: 0` is what a network failure carries. It is omitted rather than emitted as a
    // zero, because "HTTP 0" in a log is a number somebody will try to look up.
    expect(line).not.toHaveProperty('status')
  })
})
