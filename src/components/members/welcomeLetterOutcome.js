/**
 * 🔴 A64: WHAT HAPPENED TO THE WELCOME LETTER, ONCE THE MEMBER IS SAVED.
 *
 * A capture that asks for the letter (`POST /api/members?sendWelcome=true`) no longer fails because of it. The server
 * saves the member first and answers with `welcomeLetter` beside the saved record: one of the keys below. Each says
 * what the footer announces and what the dialog explains.
 *
 * 🔑 THE KEYS ARE THE SERVER'S, NOT A COPY. `WelcomeLetterOutcome` is published in `api-vocabulary.json` under
 * `welcomeLetter`, and `welcomeLetterOutcome.test.js` holds this table to that list -- so an outcome the server adds
 * cannot reach the dialog with nothing to say.
 *
 * 🔒 Nothing here names an address when a letter fails (register B8): the sentence is fixed, and the server's log has
 * the reason.
 */
export const LETTER_OUTCOMES = {
  SENT: { status: 'Welcome Letter Sent', tone: 'quiet', says: null, retry: false },
  EMAIL_OFF: {
    status: 'Member Saved',
    tone: 'pending',
    says: 'The member is saved. Emailing is not switched on yet, so the letter was not sent.',
    retry: false,
  },
  NO_ADDRESS: {
    status: 'Member Saved',
    tone: 'pending',
    says: 'The member is saved. They have no email address, so no letter was sent.',
    retry: false,
  },
  FAILED: {
    status: 'Member Saved',
    tone: 'warning',
    says: 'The member is saved, but the letter could not be emailed. Try sending it again.',
    retry: true,
  },
}

/**
 * An outcome this dialog was never told about -- only possible if the server moved ahead of this build. The member is
 * saved (the capture answered 200), so that much is said, and nothing is guessed about the letter.
 */
export const UNKNOWN_OUTCOME = {
  status: 'Member Saved',
  tone: 'pending',
  says: 'The member is saved.',
  retry: false,
}

/**
 * The outcome a capture's answer carries. A server from before A64's fix answers without `welcomeLetter`, and for it a
 * 200 to `?sendWelcome=true` meant the letter WAS sent -- so absence reads as `SENT`, never as unknown.
 */
export function outcomeOf(saved) {
  const outcome = saved?.welcomeLetter
  if (outcome === undefined || outcome === null) return 'SENT'
  return outcome
}

/**
 * Whether this server emails the letter at all, from `GET /api/members/metadata/welcome-letter`: `'on'`, `'off'`, or
 * `'unknown'`. Only a plain boolean is an answer. A proxy's page, a server from before the route existed and the test
 * suite's catch-all `[]` are all `'unknown'` -- and the dialog never claims a letter will go, or will not, on that.
 */
export function emailingFrom(metadata) {
  if (metadata?.emailEnabled === true) return 'on'
  if (metadata?.emailEnabled === false) return 'off'
  return 'unknown'
}
