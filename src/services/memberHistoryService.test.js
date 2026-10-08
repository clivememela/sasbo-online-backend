import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { HISTORY_PAGE_SIZE, getMemberHistory, memberHistoryService } from './memberHistoryService'
import { server } from '../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../test/msw/monthEndExamples'

/**
 * A member's history at the service (month end 03.6), answered as the SERVER answered it -- its golden answers (MD48) --
 * so what this asserts is what the server says, and the address the read asks at.
 */
describe('memberHistoryService', () => {
  it('the golden page, as the server answers it', async () => {
    server.use(monthEndAnswer('GET /api/members/{id}/history'))
    await expect(getMemberHistory(7)).resolves.toEqual(monthEndBody('GET /api/members/{id}/history'))
  })

  it('asks for the page and its size by name -- the first page of fifty unless told -- the member in the path', async () => {
    const asked = []
    server.use(http.get('*/api/members/:id/history', ({ request }) => {
      const url = new URL(request.url)
      asked.push(`${url.pathname}${url.search}`)
      return HttpResponse.json(monthEndBody('GET /api/members/{id}/history'))
    }))
    await getMemberHistory(7)
    await getMemberHistory('7', 2, 1)
    await getMemberHistory('7/../../summary', 0, 50)
    expect(HISTORY_PAGE_SIZE).toBe(50)
    expect(asked).toEqual([
      '/api/members/7/history?page=0&size=50',
      '/api/members/7/history?page=2&size=1',
      // A member's id travels as one path segment, whatever it holds.
      '/api/members/7%2F..%2F..%2Fsummary/history?page=0&size=50',
    ])
  })

  it('HISTORY_PAGE_INVALID and MEMBER_NOT_FOUND reject with the server\'s code, its sentence, and no retry', async () => {
    server.use(monthEndRefusal('HISTORY_PAGE_INVALID', 'GET /api/members/{id}/history'))
    await expect(getMemberHistory(7, 99)).rejects.toMatchObject({
      status: 400,
      retryable: false,
      message: monthEndExamples.refusals.HISTORY_PAGE_INVALID.body.message,
      body: { code: 'HISTORY_PAGE_INVALID' },
    })
    server.use(monthEndRefusal('MEMBER_NOT_FOUND', 'GET /api/members/{id}/history'))
    await expect(getMemberHistory(999999)).rejects.toMatchObject({
      status: 404, retryable: false, body: { code: 'MEMBER_NOT_FOUND' },
    })
  })

  it('is the one method its service gathers, for everything that iterates the service layer', () => {
    expect(memberHistoryService).toEqual({ getMemberHistory })
  })
})
