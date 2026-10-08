import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * ⚠️ THIS SERVICE CALLS AN ENDPOINT THE SERVER DOES NOT HAVE, and that is a PINNED defect (A2,
 * `apiContract.test.js`). It is left addressing `/api/debounce` deliberately: repairing it inside
 * an error-handling change would flip a pinned defect silently, which contract ④ forbids. What
 * changes here is only how the failure is REPORTED.
 *
 * 🔑 AND THAT MATTERS MORE HERE THAN ALMOST ANYWHERE, because this endpoint always fails. It used
 * to fail as `HTTP 404: ` — `statusText` is empty over HTTP/2 — logged once by this service and
 * once again by its caller. On the one error path it now rejects with a sentence fit to show a
 * person, carrying `kind: 'http'`, `status: 404` and `retryable: false`, so a consumer can tell a
 * missing endpoint from a server that is down and stop offering a retry that cannot work.
 */
export const debounceService = {
  useDebounce: (fn, delay) =>
    requestJson((signal) => fetch(apiUrl(`/api/debounce?fn=${fn}&delay=${delay}`), {
      credentials: 'include',
      signal,
    })),
};
