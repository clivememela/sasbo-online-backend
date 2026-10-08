import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * The A.C.B. debit-order run — `/api/acb/runs`.
 *
 * Every list is a server-side page, `{ content, page, size, totalElements, totalPages }`, with a
 * 0-based `page`; the server caps `size`. Preparing answers 202 with the run `PREPARING`: the
 * preparation is a job, and the screen polls `get` until the run settles. Approving is not here —
 * the server refuses it to every caller until the API knows who is calling (register B1).
 */
const pageQuery = (page, size) => new URLSearchParams({ page: String(page), size: String(size) });

export const acbRunService = {
    list: (page = 0, size = 12) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    /** `actionMonth` is `YYYY-MM`. Answers the run as created -- `PREPARING`. */
    prepare: (actionMonth) =>
        requestJsonWrite((signal) => fetch(apiUrl('/api/acb/runs'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ actionMonth }),
            signal,
        })),

    get: (id) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}`), {
            credentials: 'include',
            signal,
        })),

    lines: (id, page = 0, size = 50) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}/lines?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    exclusions: (id, page = 0, size = 50) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}/exclusions?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    /**
     * The holiday calendar -- its name, the date it is known until, and whether that date is near --
     * and one year of it: this year's unless `year` is given (D30). Public, and read-only here: the
     * server refuses its writes to every caller until the API knows who is calling (B1).
     */
    holidayCalendar: (year) => {
        // The query is built BEFORE `apiUrl`: the contract scan reads the literal it is given, and a nested
        // template with quotes inside it hid this endpoint from the scan (the runtime witness, gate 1).
        const query = year == null ? '' : `?${new URLSearchParams({ year: String(year) })}`;
        return requestJson((signal) => fetch(apiUrl(`/api/acb/holidays${query}`), {
            credentials: 'include',
            signal,
        }));
    },

    /** Every dated flat rate, oldest first, and -- from December -- the January notice (Q43). Read-only here. */
    rates: () =>
        requestJson((signal) => fetch(apiUrl('/api/acb/rates'), {
            credentials: 'include',
            signal,
        })),

    /**
     * A run's unpaids, a page at a time -- every one, or one reason's: `INVALID_ACCOUNT_NUMBER` is the
     * legacy *Invalid Accounts* list (Q11). Each says its code, its reason, where its letter is and its
     * decision -- or, undecided, the decision its code suggests (Q40, Q44).
     */
    unpaids: (id, reason, page = 0, size = 50) => {
        const query = pageQuery(page, size);
        if (reason) query.set('reason', reason);
        return requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}/unpaids?${query}`), {
            credentials: 'include',
            signal,
        }));
    },

    /** Records the decision on one unpaid -- `RE_PRESENT` or `SUSPEND`. Once recorded it stands. */
    decide: (id, lineId, decision) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/acb/runs/${id}/lines/${lineId}/decision`), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ decision }),
            signal,
        })),

    /** The re-debits run `id` holds over for want of the member's permission (D32), a page at a time. */
    /**
     * The termination report (03.5): the members at two unpaid presentations or more in the last thirteen months,
     * a page at a time, those the rule would terminate first -- with the month counting began and what the report
     * cannot see of the old system. Read-only: terminating waits for sign-in (B1).
     */
    terminationReport: (page = 0, size = 50) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/termination-report?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    heldOver: (id, page = 0, size = 50) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}/held-over?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    /**
     * The member's permission for a further re-debit in one run (D32): who agreed, when and how. `id`
     * and `lineId` are the UNPAID's -- the earlier run's line -- as the held-over list names them.
     */
    permit: (id, lineId, note) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/acb/runs/${id}/lines/${lineId}/permission`), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ note }),
            signal,
        })),

    /** The run's letters that could not go -- held for want of an address, or a send that failed (Q46, EH-E). */
    heldLetters: (id, page = 0, size = 50) =>
        requestJson((signal) => fetch(apiUrl(`/api/acb/runs/${id}/letters/held?${pageQuery(page, size)}`), {
            credentials: 'include',
            signal,
        })),

    /** A held letter dealt with by hand, and how (Q46). It leaves the worklist and is never sent. */
    markHandled: (id, letterId, note) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/acb/runs/${id}/letters/${letterId}/handled`), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ note }),
            signal,
        })),

    /**
     * Sends a held letter once the member has an address (Q46). The server refuses every caller until
     * administrators sign in to this system (B1); its refusal says so, and nothing is sent.
     */
    sendLetter: (id, letterId) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/acb/runs/${id}/letters/${letterId}/send`), {
            method: 'POST',
            credentials: 'include',
            signal,
        })),

    /**
     * The Debits screen's member search, paged on the server (R-SEC-B, R-PERF-D). A POST, so an identity
     * number never travels in a URL. Answers each member's account as its last four digits, and no
     * identity number; the full record is one member's, loaded when chosen.
     */
    searchMembers: ({ surname = '', membershipNumber = '', identityNumber = '' } = {}, page = 0, size = 25) =>
        requestJson((signal) => fetch(apiUrl('/api/acb/members/search'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ surname, membershipNumber, identityNumber, page, size }),
            signal,
        })),

    /** Withdraws a prepared or approved run. `version` is the one the screen was showing. */
    cancel: (id, version, reason) =>
        requestJsonWrite((signal) => fetch(apiUrl(`/api/acb/runs/${id}/cancel`), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ version, reason }),
            signal,
        })),
};

/**
 * The server's refusal, read off a rejection: its stable `code` and the facts it carries (a run's
 * `status` and `version`, a month). `null` when the error is not an A.C.B. refusal -- a timeout,
 * a lost connection, any other server error.
 */
export const acbRefusalOf = (error) => {
    const code = error?.body?.code;
    return typeof code === 'string' ? { code, details: error.body.details ?? {} } : null;
};
