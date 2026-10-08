import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { memberService } from './memberService'
import { server } from '../test/msw/server'

/**
 * A50, AT THE SERVICE: WHAT `POST /api/members/{id}/send-welcome` ANSWERS, AND WHAT EACH ANSWER MEANS.
 *
 * The server answers a sent letter with an EMPTY 200 (`MemberController`: `ResponseEntity.ok().build()`). Read as JSON,
 * that rejected -- a letter that went out reported as failed, and a second press emailed the member twice. Read as
 * "any body is fine", an HTML page with a 200 -- a proxy's error page -- would resolve as sent (the error contract's
 * case 4). Both are wrong, so both are pinned here: nothing means sent; a page that is not JSON does not.
 */
const answer = (response) => server.use(http.post('*/api/members/:id/send-welcome', () => response))

describe('memberService.sendWelcomeLetter', () => {
  it('an empty 200 is a sent letter', async () => {
    answer(new HttpResponse(null, { status: 200 }))
    await expect(memberService.sendWelcomeLetter(9001)).resolves.toBeUndefined()
  })

  it('must-pass: a 200 carrying an HTML page is NOT a sent letter', async () => {
    answer(new HttpResponse('<html><body>Bad gateway</body></html>', { status: 200, headers: { 'content-type': 'text/html' } }))
    await expect(memberService.sendWelcomeLetter(9001)).rejects.toThrow()
  })

  it('must-pass: a server failure rejects with its status', async () => {
    answer(new HttpResponse(null, { status: 503 }))
    await expect(memberService.sendWelcomeLetter(9001)).rejects.toMatchObject({ status: 503 })
  })
})

describe('memberService.getWelcomeLetterMetadata (A64)', () => {
  it('reads whether this server emails the letter, from its own route', async () => {
    server.use(http.get('*/api/members/metadata/welcome-letter', () => HttpResponse.json({ emailEnabled: false })))
    await expect(memberService.getWelcomeLetterMetadata()).resolves.toEqual({ emailEnabled: false })
  })

  it('must-pass: a failure rejects, so the dialog can say it does not know', async () => {
    server.use(http.get('*/api/members/metadata/welcome-letter', () => new HttpResponse(null, { status: 500 })))
    await expect(memberService.getWelcomeLetterMetadata()).rejects.toMatchObject({ status: 500 })
  })
})
