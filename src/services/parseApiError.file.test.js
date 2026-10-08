import { afterEach, describe, expect, it, vi } from 'vitest'

import { ERROR_KINDS, filenameOf, requestJson } from './parseApiError'

/**
 * A DOWNLOAD THROUGH THE ONE ERROR PATH: `requestJson(send, { file: true })` resolves `{ blob, filename }`.
 *
 * A report that needs the signed-in user's token is fetched, not linked, so its failures must be read exactly as every
 * other request's are -- the server's sentence on a refusal, a lost connection as a network failure, the deadline over
 * the body as well as the headers -- and the one new rule is the file's own: an HTML page answered with a 200 is a
 * proxy's page, never the report. Every row is a real `Response`, as the body-shapes table's are.
 */
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

const reply = (body, { status = 200, headers = {} } = {}) => () => Promise.resolve(new Response(body, { status, headers }))

const outcome = (promise) => promise.then((value) => ({ value }), (error) => ({ error }))

afterEach(() => {
  vi.useRealTimers()
})

describe('a download -- requestJson(send, { file: true })', () => {
  it('resolves the bytes and the name the server gave them', async () => {
    const { value, error } = await outcome(requestJson(reply('synthetic workbook bytes', {
      headers: { 'Content-Type': XLSX, 'Content-Disposition': 'attachment; filename="Payroll discrepancies - Invented - September 2026.xlsx"' },
    }), { file: true }))

    expect(error).toBeUndefined()
    expect(value.filename).toBe('Payroll discrepancies - Invented - September 2026.xlsx')
    expect(value.blob).toBeInstanceOf(Blob)
    expect(await value.blob.text()).toBe('synthetic workbook bytes')
  })

  it('a file sent with no name resolves a null name, for the caller to name it', async () => {
    const { value } = await outcome(requestJson(reply('%PDF-1.7 synthetic', { headers: { 'Content-Type': 'application/pdf' } }), { file: true }))
    expect(value.filename).toBeNull()
    expect(await value.blob.text()).toBe('%PDF-1.7 synthetic')
  })

  it('🔴 an HTML page answered with a 200 is not the file: it rejects, and is never handed over to be saved', async () => {
    const { value, error } = await outcome(requestJson(reply('<!doctype html><title>gateway</title>', {
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    }), { file: true }))

    expect(value).toBeUndefined()
    expect(error).toMatchObject({ kind: ERROR_KINDS.NON_JSON, status: 200, retryable: true })
    expect(error.message).toMatch(/web page instead of the file/)
  })

  it('the must-pass beside it: any other type is the file -- JSON included, which the service error contract sends', async () => {
    const { value, error } = await outcome(requestJson(reply('{"id":1}', { headers: { 'Content-Type': 'application/json' } }), { file: true }))
    expect(error).toBeUndefined()
    expect(await value.blob.text()).toBe('{"id":1}')
  })

  it('a refusal still rejects with the server\'s own sentence and code', async () => {
    const { error } = await outcome(requestJson(reply(JSON.stringify({ code: 'REPORT_FORMAT_UNKNOWN', message: 'That format is not offered.' }), {
      status: 400, headers: { 'Content-Type': 'application/json' },
    }), { file: true }))

    expect(error).toMatchObject({ kind: ERROR_KINDS.HTTP, status: 400, message: 'That format is not offered.' })
    expect(error.body.code).toBe('REPORT_FORMAT_UNKNOWN')
  })

  it('a request that never reached a server rejects as a network failure', async () => {
    const { error } = await outcome(requestJson(() => Promise.reject(new TypeError('Failed to fetch')), { file: true }))
    expect(error).toMatchObject({ kind: ERROR_KINDS.NETWORK, isNetworkError: true })
  })

  it('the deadline covers the body: a report whose bytes stop arriving is stopped, as a timeout (R2-F4)', async () => {
    vi.useFakeTimers()
    // A body that stalls after its headers, and errors when the request's signal fires -- as a fetched body does.
    const stalled = (signal) => new ReadableStream({
      start(controller) {
        signal.addEventListener('abort', () => controller.error(new DOMException('The operation was aborted.', 'AbortError')))
      },
    })
    const settled = outcome(requestJson(
      (signal) => Promise.resolve(new Response(stalled(signal), { headers: { 'Content-Type': XLSX } })),
      { file: true, timeoutMs: 50 },
    ))

    await vi.advanceTimersByTimeAsync(60)
    const { value, error } = await settled

    expect(value).toBeUndefined()
    expect(error).toMatchObject({ kind: ERROR_KINDS.TIMEOUT, retryable: true })
  })
})

describe('filenameOf -- the name in a Content-Disposition header', () => {
  it.each([
    ['attachment; filename="report.xlsx"', 'report.xlsx'],
    ['attachment; filename=report.pdf', 'report.pdf'],
    ['ATTACHMENT; FILENAME="upper.csv"', 'upper.csv'],
    ["attachment; filename*=UTF-8''Payroll%20queries%20-%20Invented.xlsx", 'Payroll queries - Invented.xlsx'],
    ["attachment; filename*=utf-8''caf%C3%A9.pdf", 'café.pdf'],
    ["attachment; filename=\"plain.xlsx\"; filename*=UTF-8''preferred.xlsx", 'preferred.xlsx'],
  ])('%s → %s', (header, name) => {
    expect(filenameOf(header)).toBe(name)
  })

  it('🔒 keeps only the last part of a path, so a name can never point the save outside the downloads folder', () => {
    expect(filenameOf('attachment; filename="../../etc/passwd"')).toBe('passwd')
    expect(filenameOf('attachment; filename="C:\\Users\\someone\\report.xlsx"')).toBe('report.xlsx')
    expect(filenameOf('attachment; filename=".."')).toBeNull()
  })

  it('a malformed encoded name falls back to the plain one', () => {
    expect(filenameOf("attachment; filename=\"plain.xlsx\"; filename*=UTF-8''broken%E0%A4%A.xlsx")).toBe('plain.xlsx')
  })

  it.each([[null], [''], ['attachment'], ['attachment; filename=""']])('no name it can read (%j) is null', (header) => {
    expect(filenameOf(header)).toBeNull()
  })
})
