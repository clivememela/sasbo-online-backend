import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import {
  VISIT_PAGE_SIZE, createDraft, discardDraft, getDraft, getMyDrafts, getVisits, recordVisit, submitDraft, updateDraft,
  workplaceVisitService,
} from './workplaceVisitService'
import { server } from '../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../test/msw/monthEndExamples'

/**
 * A workplace's visits at the service (month end A.1, MO22), answered as the SERVER answered them -- its golden
 * answers (MD48) -- so what this asserts is what the server says, and where the service asks it.
 */
const PAGE = 'GET /api/workplaces/{id}/visits'
const RECORD = 'POST /api/workplaces/{id}/visits'

describe('workplaceVisitService', () => {
  it('a page of visits, as the server answers it', async () => {
    server.use(monthEndAnswer(PAGE))
    await expect(getVisits(1)).resolves.toEqual(monthEndBody(PAGE))
  })

  it('asks for the page and its size by name -- the server\'s first page of twenty unless told -- the workplace in the path', async () => {
    const asked = []
    server.use(http.get('*/api/workplaces/:id/visits', ({ request }) => {
      const url = new URL(request.url)
      asked.push(`${url.pathname}${url.search}`)
      return HttpResponse.json(monthEndBody(PAGE))
    }))
    await getVisits(1)
    await getVisits('1', 2, 5)
    await getVisits('1/../../summary')
    expect(VISIT_PAGE_SIZE).toBe(monthEndBody(PAGE).size)
    expect(asked).toEqual([
      '/api/workplaces/1/visits?page=0&size=20',
      '/api/workplaces/1/visits?page=2&size=5',
      // A workplace's id travels as one path segment, whatever it holds.
      '/api/workplaces/1%2F..%2F..%2Fsummary/visits?page=0&size=20',
    ])
  })

  it('records a visit: the body POSTed as given, as JSON, and the visit answered back', async () => {
    let sent
    server.use(http.post('*/api/workplaces/:id/visits', async ({ request, params }) => {
      sent = { id: params.id, type: request.headers.get('content-type'), body: await request.json() }
      return HttpResponse.json(monthEndBody(RECORD), { status: 201 })
    }))
    const visit = monthEndExamples.answers[RECORD].requestBody

    await expect(recordVisit(1, visit)).resolves.toEqual(monthEndBody(RECORD))
    expect(sent).toEqual({ id: '1', type: 'application/json', body: visit })
  })

  it('VISIT_INVALID and WORKPLACE_UNKNOWN reject with the server\'s code, its sentence, the field named -- and no retry', async () => {
    server.use(monthEndRefusal('VISIT_INVALID', RECORD))
    await expect(recordVisit(1, { complementFound: 40 })).rejects.toMatchObject({
      status: 400,
      retryable: false,
      message: monthEndExamples.refusals.VISIT_INVALID.body.message,
      body: { code: 'VISIT_INVALID', details: { field: 'visitedOn' } },
    })
    server.use(monthEndRefusal('WORKPLACE_UNKNOWN', PAGE))
    await expect(getVisits(999999)).rejects.toMatchObject({ status: 400, retryable: false, body: { code: 'WORKPLACE_UNKNOWN' } })
  })

  // October build: the visit drafts' six joined the two -- each the very function, never a copy.
  it('is the eight methods its service gathers, for everything that iterates the service layer', () => {
    expect(workplaceVisitService).toEqual({
      getVisits, recordVisit, getMyDrafts, getDraft, createDraft, updateDraft, submitDraft, discardDraft,
    })
  })
})
