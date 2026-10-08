import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { summaryService } from './summaryService'
import { server } from '../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../test/msw/monthEndExamples'

/**
 * `GET /api/members/summary` at the service (month end 02.2 / 02.3), answered as the SERVER answered it -- its golden
 * answers (MD48) -- so what this asserts is what the server says.
 */
const ROUTE = 'GET /api/members/summary'

/** The summary answered with its golden body, and the query of every request it was asked. */
const recordAsked = () => {
  const asked = []
  server.use(http.get('*/api/members/summary', ({ request }) => {
    asked.push(new URL(request.url).search)
    return HttpResponse.json(monthEndBody(ROUTE))
  }))
  return asked
}

describe('summaryService.getSummary', () => {
  it('🔴 MD55: asked for nothing, it names no month -- the server answers for its own, and says so', async () => {
    const asked = recordAsked()

    await expect(summaryService.getSummary()).resolves.toMatchObject({ month: '2026-09', monthFromTheServer: true })
    expect(asked).toEqual([''])
  })

  it('names the month, the grouping and the place asked for -- and leaves out a part with no value', async () => {
    const asked = recordAsked()

    await summaryService.getSummary({ month: '2026-08', by: 'employer', area: 'A' })
    await summaryService.getSummary({ month: '', by: 'region', region: 'NORTH', area: undefined })

    expect(asked).toEqual(['?month=2026-08&by=employer&area=A', '?by=region&region=NORTH'])
  })

  it('the golden answer\'s own handler matches the route', async () => {
    server.use(monthEndAnswer(ROUTE))

    await expect(summaryService.getSummary({ by: 'group' })).resolves.toEqual(monthEndBody(ROUTE))
  })

  it.each(['SUMMARY_GROUPING_UNKNOWN', 'SUMMARY_AREA_UNKNOWN', 'SUMMARY_MONTH_INVALID', 'SUMMARY_MONTH_IN_FUTURE'])(
    '%s rejects 400 with the server\'s code and its sentence, and offers no retry that cannot work',
    async (code) => {
      server.use(monthEndRefusal(code, ROUTE))

      await expect(summaryService.getSummary()).rejects.toMatchObject({
        status: 400,
        retryable: false,
        message: monthEndExamples.refusals[code].body.message,
        body: { code },
      })
    },
  )

  it('SUMMARY_UNAVAILABLE -- a figure that outlived the server\'s four seconds -- rejects 503, and may be tried again', async () => {
    server.use(monthEndRefusal('SUMMARY_UNAVAILABLE', ROUTE))

    await expect(summaryService.getSummary()).rejects.toMatchObject({
      status: 503,
      retryable: true,
      message: 'The figures are taking too long to work out just now. Nothing is wrong with the members\' records. '
        + 'Try again in a moment.',
      body: { code: 'SUMMARY_UNAVAILABLE' },
    })
  })
})
