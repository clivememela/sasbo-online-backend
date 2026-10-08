import { apiUrl } from './api';
import { requestJsonWrite } from './parseApiError';

/**
 * A member's status changed, with SASBO's code for it -- month end 03.1, `POST /api/members/{id}/status`.
 *
 * `change` is `{ toStatus, expectedStatus, reason, deletionCode?, reasonUnderCode?, suspensionCode?, note? }`.
 * `expectedStatus` is the status the screen showed, so a change made on a stale view is refused `STATUS_CHANGED_SINCE`
 * (409) instead of being written over somebody else's. A change into a leaving status carries SASBO's deletion code --
 * its NUMBER, never the row's id (RC7) -- and, where one was chosen, the reason SASBO keeps under it, by its name
 * (`reasonUnderCode`, 04.5); a change into Suspended its suspension code.
 *
 * The answer is `{ memberId, membershipNumber, status, change }`. The same change asked twice writes once (ME-3):
 * where the member already stood there, `change` is `null`. A refusal rejects with the server's code and its sentence
 * (ME-13), and a write whose answer never arrived is re-read before anything is said of it.
 */
export const memberStatusService = {
  changeStatus: (memberId, change) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/members/${memberId}/status`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(change),
      signal,
    })),
};
