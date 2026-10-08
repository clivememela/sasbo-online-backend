import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import {
  STATISTIC_READS, askPack, getMonths, getPack, getMembership, getNewAndDeleted, monthEndService, packWorkbookUrl,
} from './monthEndService'
import { server } from '../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../test/msw/monthEndExamples'

/**
 * Month end's reads and its pack at the service (04.1-04.10), answered as the SERVER answered them -- its golden
 * answers (MD48) -- so what this asserts is what the server says, and the address each read asks at.
 */
describe('monthEndService', () => {
  it('the months, as the server answers them', async () => {
    server.use(monthEndAnswer('GET /api/month-end/months'))
    await expect(getMonths()).resolves.toEqual(monthEndBody('GET /api/month-end/months'))
  })

  it('each statistic is read at its own route, for the month, with an area or a region and never both', async () => {
    const asked = []
    server.use(http.get('*/api/month-end/:month/:statistic', ({ request, params }) => {
      const url = new URL(request.url)
      asked.push(`${params.statistic} ${params.month} ${url.search}`)
      return HttpResponse.json(monthEndBody(`GET /api/month-end/{month}/${params.statistic}`))
    }))

    for (const [statistic, read] of Object.entries(STATISTIC_READS)) {
      await expect(read('2026-08', {})).resolves.toMatchObject({ statistic })
    }
    await getNewAndDeleted('2026-08', { area: 'A' })
    await getMembership('2026-09', { region: 'NORTH' })
    await getMembership('2026-09', { area: 'A', region: 'NORTH' })

    // As many as there are statistics -- seven since 04.9 -- never a count typed here.
    const reads = Object.keys(STATISTIC_READS).length
    expect(asked.slice(0, reads)).toEqual(Object.keys(STATISTIC_READS).map((statistic) => `${statistic} 2026-08 `))
    expect(asked.slice(reads)).toEqual(['new-and-deleted 2026-08 ?area=A', 'membership 2026-09 ?region=NORTH', 'membership 2026-09 ?area=A'])
  })

  it('the golden answers\' own handlers match the routes, {month} and all', async () => {
    server.use(monthEndAnswer('GET /api/month-end/{month}/leavers'))
    await expect(STATISTIC_READS.leavers('2026-08')).resolves
      .toEqual(monthEndBody('GET /api/month-end/{month}/leavers'))
  })

  it('a month from the address travels as one path segment, whatever was typed', async () => {
    let path
    server.use(http.get('*/api/month-end/:month/membership', ({ request }) => {
      path = new URL(request.url).pathname
      return HttpResponse.json(monthEndBody('GET /api/month-end/{month}/membership'))
    }))
    await getMembership('2026-08/../../members', {})
    expect(path).toBe('/api/month-end/2026-08%2F..%2F..%2Fmembers/membership')
  })

  it('MONTH_NOT_TAKEN: a past month never taken rejects 404, with the server\'s code and its sentence', async () => {
    server.use(monthEndRefusal('MONTH_NOT_TAKEN', 'GET /api/month-end/{month}/membership'))
    await expect(getMembership('2026-07', {})).rejects.toMatchObject({
      status: 404,
      message: monthEndExamples.refusals.MONTH_NOT_TAKEN.body.message,
      retryable: false,
      body: { code: 'MONTH_NOT_TAKEN' },
    })
  })

  it('the pack: asked for by a POST with no body, and read at its own address', async () => {
    let posted
    server.use(
      http.post('*/api/month-end/:month/pack', async ({ request, params }) => {
        posted = { month: params.month, body: await request.text() }
        const example = monthEndExamples.answers['POST /api/month-end/{month}/pack']
        return HttpResponse.json(example.body, { status: example.status })
      }),
      monthEndAnswer('GET /api/month-end/packs/{id}'),
    )
    await expect(askPack('2026-08')).resolves.toEqual(monthEndBody('POST /api/month-end/{month}/pack'))
    expect(posted).toEqual({ month: '2026-08', body: '' })
    await expect(getPack(1)).resolves.toEqual(monthEndBody('GET /api/month-end/packs/{id}'))
  })

  it('PACK_BUSY and PACK_NOT_FOUND reject with the server\'s code, its sentence and its details', async () => {
    server.use(
      monthEndRefusal('PACK_BUSY', 'POST /api/month-end/{month}/pack'),
      monthEndRefusal('PACK_NOT_FOUND', 'GET /api/month-end/packs/{id}'),
    )
    await expect(askPack('2026-08')).rejects.toMatchObject({
      status: 409, message: monthEndExamples.refusals.PACK_BUSY.body.message, body: { code: 'PACK_BUSY', details: { inHand: '2026-07' } },
    })
    await expect(getPack(999999)).rejects.toMatchObject({ status: 404, body: { code: 'PACK_NOT_FOUND' } })
  })

  it('a workbook is saved from an address, not fetched -- and the address is the server\'s route for it', () => {
    expect(packWorkbookUrl(1, 'statistics')).toBe('http://api.test.invalid/api/month-end/packs/1/statistics')
    expect(packWorkbookUrl(1, 'a/b')).toBe('http://api.test.invalid/api/month-end/packs/1/a%2Fb')
    expect(Object.values(monthEndService)).not.toContain(packWorkbookUrl)
  })
})
