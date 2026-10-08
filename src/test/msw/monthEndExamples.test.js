import { describe, expect, it } from 'vitest'

import { committedSurface, committedVocabulary } from '../apiContractScan'
import { monthEndExamples } from './monthEndExamples'

/**
 * The golden answers copied from the server (MD48) cover the arc: an answer for every month-end route the server
 * routes, and an envelope for every refusal code it publishes. A route or a code added on the server without its
 * example reddens here, before a screen is written against a guess.
 */
const MONTH_END_ROUTE = /^(GET \/api\/reference\/.+|GET \/api\/workplaces|(GET|POST) \/api\/workplaces\/\{id\}\/visits|GET \/api\/members\/summary|POST \/api\/members\/\{id\}\/status)$/

describe('month end\'s golden answers', () => {
  it('answer every month-end route in the committed API surface', () => {
    // committedSurface() is the surface's lines, already trimmed and without the blank ones.
    const routes = committedSurface().filter((line) => MONTH_END_ROUTE.test(line))
    expect(routes.length).toBeGreaterThan(5)
    // A.1's two (MO22) are among them, so the screens' visits stand on the server's own answers.
    expect(routes).toEqual(expect.arrayContaining(['GET /api/workplaces/{id}/visits', 'POST /api/workplaces/{id}/visits']))
    expect(Object.keys(monthEndExamples.answers).sort()).toEqual(expect.arrayContaining(routes))
  })

  it('hold an envelope for every refusal code the server publishes, each with its code and status', () => {
    const published = committedVocabulary().monthEnd.refusalCodes
    expect(Object.keys(monthEndExamples.refusals).sort()).toEqual(published.map((c) => c.code).sort())
    for (const { code, status } of published) {
      expect(monthEndExamples.refusals[code].status).toBe(status)
      expect(monthEndExamples.refusals[code].body.code).toBe(code)
    }
  })

  it('say they hold nothing of SASBO\'s (MD57)', () => {
    expect(monthEndExamples._holds).toMatch(/invented/)
  })
})
