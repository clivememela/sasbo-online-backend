import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { memberStatusService } from './memberStatusService'
import { server } from '../test/msw/server'
import { monthEndAnswer, monthEndExamples, monthEndRefusal } from '../test/msw/monthEndExamples'

/**
 * `POST /api/members/{id}/status` at the service (month end 03.1), answered as the SERVER answered it -- its golden
 * answers (MD48) -- so what this asserts is what the server says, not what somebody typed here.
 */
const ROUTE = 'POST /api/members/{id}/status'
const example = monthEndExamples.answers[ROUTE]

describe('memberStatusService.changeStatus', () => {
  it('sends the change as the server reads it, and answers the member\'s status and the change written', async () => {
    let sent
    server.use(http.post('*/api/members/:id/status', async ({ request, params }) => {
      sent = { id: params.id, body: await request.json() }
      return HttpResponse.json(example.body)
    }))

    await expect(memberStatusService.changeStatus(1, example.requestBody)).resolves.toEqual(example.body)
    expect(sent).toEqual({ id: '1', body: example.requestBody })
  })

  it('the golden answer\'s own handler matches the route, its {id} and all', async () => {
    server.use(monthEndAnswer(ROUTE))

    await expect(memberStatusService.changeStatus(7, example.requestBody)).resolves
      .toMatchObject({ status: 'Resigned', change: { reason: 'DELETION_CODE', deletionCode: 2 } })
  })

  it('STATUS_CHANGED_SINCE: a change on a stale view rejects 409, with the server\'s code and its sentence', async () => {
    const refusal = monthEndExamples.refusals.STATUS_CHANGED_SINCE
    server.use(monthEndRefusal('STATUS_CHANGED_SINCE', ROUTE))

    await expect(memberStatusService.changeStatus(1, example.requestBody)).rejects.toMatchObject({
      status: 409,
      message: refusal.body.message,
      body: { code: 'STATUS_CHANGED_SINCE' },
    })
  })
})
