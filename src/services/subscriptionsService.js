import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * 🔴 A24's QUIETER HALF, third of three: this file rejected with the raw response TEXT, so a
 * caller's `catch (e) { e.message }` was `undefined`. Every method is on the one error path now
 * (conventions §16) and rejects with an `Error` carrying `kind`, `status`, `validationErrors` and
 * `retryable`, under A14's request ceiling.
 *
 * 🔴 A23, FIXED (the October build): `create` POSTed `{ name }` to `/api/recruitment-channels`, so
 * creating a subscription silently created a RECRUITMENT CHANNEL and answered 2xx. It addresses its
 * own resource now, with the row the server's `SubscriptionsDto` reads.
 */
export const subscriptionsService = {
    getAll: () =>
        requestJson((signal) => fetch(apiUrl('/api/subscriptions'), {
            credentials: 'include',
            signal,
        })),

    /** @param {{ bankName: string, acbDebitDay: number, subscriptionAmount: number, isCoreBanking?: boolean, status?: string }} subscription */
    create: (subscription) =>
        requestJsonWrite((signal) => fetch(apiUrl('/api/subscriptions'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(subscription),
            signal,
        })),

    update: (id, data) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/subscriptions/${id}`), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(data),
            signal,
        })),

    // `parseBody: false`: a DELETE answers 204 with no body, and rejecting because that body was
    // not JSON would be the defect rather than the fix.
    remove: (id) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/subscriptions/${id}`), {
            method: 'DELETE',
            credentials: 'include',
            signal,
        }), { parseBody: false }),
};
