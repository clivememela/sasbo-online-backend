import { describe, expect, it } from 'vitest'
import { requestJson, requestOptionalJson } from './parseApiError'

/**
 * The three body shapes, pinned as a table (RF). `requestOptionalJson` was a second copy of `requestJson`'s ceiling and
 * became a mode of it; this is what says the three answer exactly as they did when each had its own parse path.
 *
 * Every row is a real `Response`, so `json()` and `text()` behave as the browser's do -- a hand-rolled double that only
 * implemented one of them would pass a helper that called the other by accident.
 */
const reply = (body, status = 200) => () => Promise.resolve(new Response(body, { status, headers: { 'content-type': 'application/json' } }))

const outcome = (promise) => promise.then((value) => ({ value }), (error) => ({ kind: error?.kind, message: error?.message }))

const MODES = {
  'requestJson (a body is expected)': (send) => requestJson(send),
  'requestJson { allowEmpty }': (send) => requestJson(send, { allowEmpty: true }),
  requestOptionalJson: (send) => requestOptionalJson(send),
}

// mode → body → what it must settle to. `NON_JSON` is a rejection of that kind.
const EXPECTED = {
  'requestJson (a body is expected)': { empty: 'nonJson', json: { id: 7 }, html: 'nonJson' },
  'requestJson { allowEmpty }': { empty: undefined, json: { id: 7 }, html: 'nonJson' },
  requestOptionalJson: { empty: undefined, json: { id: 7 }, html: undefined },
}

const BODIES = { empty: '', json: '{"id":7}', html: '<!doctype html><title>proxy</title>' }

describe('the three body shapes answer as they always did (RF: one parse path)', () => {
  for (const [mode, call] of Object.entries(MODES)) {
    for (const [name, body] of Object.entries(BODIES)) {
      const want = EXPECTED[mode][name]
      it(`${mode}, a 200 with ${name === 'empty' ? 'no body' : `a ${name} body`} → ${want === 'nonJson' ? 'rejects as nonJson' : JSON.stringify(want) ?? 'undefined'}`, async () => {
        const got = await outcome(call(reply(body)))
        if (want === 'nonJson') expect(got.kind).toBe('nonJson')
        else expect(got).toEqual({ value: want })
      })
    }

    // The must-pass beside the table: a FAILED response rejects with the server's own words in every mode -- a helper
    // that swallowed everything into `undefined` would satisfy the optional rows above.
    it(`${mode}, a 409 still rejects with the server's message`, async () => {
      const got = await outcome(call(reply(JSON.stringify({ message: 'That member already exists.' }), 409)))
      expect(got).toEqual({ kind: 'http', message: 'That member already exists.' })
    })
  }
})
