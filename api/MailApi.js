import { ApiClient } from './ApiClient.js'
import { MAIL_URL } from '../support/env.js'

/**
 * The mail catcher's REST surface -- Mailpit's, not the application's.
 *
 * 🔴 WHY THIS SUITE READS MAIL AT ALL. Since client 8c6b675 and server 99936a5 a
 * member is captured by SENDING their welcome letter: one request saves the member
 * and emails them (register row A64). The catcher in docker-compose.yml is what
 * lets that request succeed in the stack, and this client is what turns "the letter
 * was sent" from a toast into something a scenario can check -- who it went to,
 * and what was attached.
 *
 * WHAT IT READS, from Mailpit's documented v1 API:
 *
 *   GET /api/v1/search?query=to:"<address>"   the messages addressed to one recipient
 *   GET /api/v1/message/{ID}                   one message, its attachments listed
 *   GET /api/v1/message/{ID}/part/{PartID}     one attachment's bytes
 *
 * ⚠️ NOTHING IS DELETED, and nothing needs to be. The catcher lives and dies with
 * the stack (`compose down -v`), and a scenario that reads mail addresses a MINTED
 * recipient, so two scenarios running at once never read each other's letters.
 */
export class MailApi extends ApiClient {
  constructor(request) {
    super(request)
    this.baseURL = MAIL_URL
  }

  /** Every caught message addressed to `address` -- summaries, as Mailpit lists them. */
  async messagesTo(address) {
    const query = encodeURIComponent(`to:"${address}"`)
    const found = await this.json(await this.get(`/api/v1/search?query=${query}`))
    return found.messages ?? []
  }

  /** One message in full, with `Attachments` as `{ PartID, FileName, ContentType, Size }`. */
  async message(id) {
    return this.json(await this.get(`/api/v1/message/${encodeURIComponent(id)}`))
  }

  /** The bytes of one attachment, as a Buffer. */
  async attachment(id, partId) {
    const response = await this.get(`/api/v1/message/${encodeURIComponent(id)}/part/${encodeURIComponent(partId)}`)
    return response.body()
  }
}
